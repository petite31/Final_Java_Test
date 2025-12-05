package org.file.transfer.network;

import org.file.transfer.controller.SendFilesController;
import org.file.transfer.model.FilePacket;
import org.file.transfer.model.FolderManifest;
import org.file.transfer.model.TransferStats;
import org.file.transfer.transport.Transport;
import org.file.transfer.transport.TransportManager;

import java.io.*;
import java.net.*;
import java.util.BitSet;
import java.util.Stack;

public class FileSender {
    private final TransferManager manager;
    private Transport transport;

    private static final int PACKET_SIZE = 60000;

    public FileSender(TransferManager manager) throws SocketException {
        this.manager = manager;
        // Ideally sender uses the same transport manager.
        // If we want to connect to a specific transport mechanism, we should ask
        // TransportManager.
        // For UDP default:
        this.transport = TransportManager.getInstance().createTransport("UDP");
    }

    public boolean performHandshake(String ip, int port, String passkey) {
        try {
            // Using Transport for handshake
            // Auth is simple string exchange
            String authMsg = "AUTH_REQUEST:" + passkey;
            transport.sendTo(authMsg.getBytes(), ip, port);

            // Wait for response?
            // Transport.receive() is blocking.
            // We need a timeout logic. Transport interface doesn't strictly imply timeouts
            // on receive().
            // But UDP transport implementation uses DatagramSocket which can have timeouts.
            // If we cast to UDP, we can set it.
            // Better to wrap in thread or Future?
            // For now, let's assume Transport operations are blocking and we rely on socket
            // timeout if underlying supports it.
            // Or we check `receive()` loop in separate thread?
            // Handshake is synchronous here.

            // NOTE: TransportUDP as implemented in step 148 receives indiscriminately.
            // If we use the SAME transport instance for shared listening, we might steal
            // packets?
            // FileSender usually creates its own socket (ephemeral).
            // FileReceiver has its own bound socket.
            // So it's fine.

            if (transport instanceof org.file.transfer.transport.TransportUDP udp) {
                udp.getSocket().setSoTimeout(3000);
            }

            byte[] response = transport.receive(); // Blocks
            String reply = new String(response).trim();
            // Check sender ip? transport.getLastSenderAddress()

            return reply.equals("AUTH_RESPONSE:OK");

        } catch (Exception e) {
            System.out.println("[Sender] Handshake failed: " + e.getMessage());
            return false;
        }
    }

    public void sendFileOrFolder(File file, String targetIp, int targetPort, SendFilesController callback) {
        if (file.isDirectory()) {
            sendFolder(file, targetIp, targetPort, callback);
        } else {
            sendFile(file, targetIp, targetPort, callback);
        }
    }

    private void sendFolder(File folder, String targetIp, int targetPort, SendFilesController callback) {
        try {
            FolderManifest manifest = new FolderManifest(folder.getName());
            Stack<File> stack = new Stack<>();
            stack.push(folder);
            int rootPathLen = folder.getParentFile().getAbsolutePath().length();

            while (!stack.isEmpty()) {
                File current = stack.pop();
                File[] files = current.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String relPath = f.getAbsolutePath().substring(rootPathLen + 1);
                        if (f.isDirectory()) {
                            manifest.addItem(relPath, 0, 0);
                            stack.push(f);
                        } else {
                            manifest.addItem(relPath, f.length(), f.lastModified());
                        }
                    }
                }
            }

            File tempManifest = File.createTempFile("manifest", ".manifest");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tempManifest))) {
                oos.writeObject(manifest);
            }

            sendFileInternal(tempManifest, targetIp, targetPort, callback, folder.getName() + ".manifest");
            tempManifest.delete();

            stack.push(folder);
            while (!stack.isEmpty()) {
                File current = stack.pop();
                File[] files = current.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isDirectory()) {
                            stack.push(f);
                        } else {
                            String relPath = f.getAbsolutePath().substring(rootPathLen + 1);
                            sendFileInternal(f, targetIp, targetPort, callback, folder.getName() + "/" + relPath);
                        }
                    }
                }
            }

            if (callback != null)
                callback.onTransferComplete();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFile(File file, String targetIp, int targetPort, SendFilesController callback) {
        sendFileInternal(file, targetIp, targetPort, callback, file.getName());
        if (callback != null)
            callback.onTransferComplete();
    }

    private void sendFileInternal(File file, String targetIp, int targetPort, SendFilesController callback,
            String remoteFileName) {
        try {
            long fileSize = file.length();
            int totalPackets = (int) Math.ceil(fileSize / (double) PACKET_SIZE);
            if (totalPackets == 0)
                totalPackets = 1;

            // 1. Send FILE_REQ
            String req = "FILE_REQ:" + file.lastModified() + ":" + totalPackets + ":" + remoteFileName;

            BitSet receivedBlocks = new BitSet(totalPackets);
            boolean handshakeDone = false;

            for (int i = 0; i < 5; i++) {
                transport.sendTo(req.getBytes(), targetIp, targetPort);
                try {
                    // Wait for response
                    if (transport instanceof org.file.transfer.transport.TransportUDP udp) {
                        udp.getSocket().setSoTimeout(2000);
                    }

                    byte[] respData = transport.receive();

                    try {
                        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(respData));
                        Object obj = ois.readObject();
                        if (obj instanceof BitSet) {
                            receivedBlocks = (BitSet) obj;
                            handshakeDone = true;
                            System.out.println(
                                    "[Sender] Resuming logic. Blocks received: " + receivedBlocks.cardinality());
                            break;
                        }
                    } catch (Exception ex) {
                        String s = new String(respData).trim();
                        if (s.startsWith("FILE_ACK:START")) {
                            handshakeDone = true;
                            break;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("[Sender] Timeout waiting for FILE_ACK, retrying...");
                }
            }

            if (!handshakeDone) {
                System.out.println("[Sender] Handshake failed, skipping file " + remoteFileName);
                return;
            }

            TransferStats stats = new TransferStats(fileSize);
            stats.update(receivedBlocks.cardinality() * PACKET_SIZE);

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                for (int packetId = 0; packetId < totalPackets; packetId++) {
                    if (receivedBlocks.get(packetId))
                        continue;

                    raf.seek((long) packetId * PACKET_SIZE);
                    byte[] buffer = new byte[PACKET_SIZE];
                    int bytesRead = raf.read(buffer);
                    if (bytesRead == -1)
                        break;

                    byte[] data = (bytesRead == PACKET_SIZE) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                    FilePacket packet = new FilePacket(
                            packetId,
                            data,
                            remoteFileName,
                            fileSize,
                            totalPackets,
                            packetId == totalPackets - 1);

                    sendPacketReliable(packet, targetIp, targetPort);

                    stats.update((long) packetId * PACKET_SIZE + bytesRead);
                    if (callback != null && (packetId % 5 == 0 || packet.isLast())) {
                        callback.updateProgress(stats.getProgress(), stats.getCurrentSpeedMBs(),
                                stats.getEstimatedTimeRemainingSeconds());
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPacketReliable(FilePacket packetData, String targetIp, int port) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(packetData);
        oos.close();
        byte[] raw = baos.toByteArray();

        boolean ack = false;
        int retry = 0;
        while (!ack && retry < 5) {
            transport.sendTo(raw, targetIp, port);

            if (transport instanceof org.file.transfer.transport.TransportUDP udp) {
                udp.getSocket().setSoTimeout(500);
            }

            try {
                byte[] ackBuf = transport.receive();
                String msg = new String(ackBuf).trim();
                if (msg.startsWith("ACK_" + packetData.packetId())) {
                    ack = true;
                }
            } catch (SocketTimeoutException e) {
                retry++;
            }
        }
    }

    public void sendAck(int packetId, String ip, int port) {
        try {
            String ackMsg = "ACK_" + packetId;
            transport.sendTo(ackMsg.getBytes(), ip, port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (transport != null)
                transport.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}