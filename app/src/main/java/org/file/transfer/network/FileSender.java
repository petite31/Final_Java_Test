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
import java.util.List;
import java.util.Stack;

public class FileSender {
    private Transport transport;

    private static final int PACKET_SIZE = 60000;

    public FileSender(TransferManager manager) throws SocketException {
        this.transport = TransportManager.getInstance().createTransport("UDP");
    }

    public boolean performHandshake(String ip, int port, String passkey) {
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            try {
                String authMsg = "AUTH_REQUEST:" + passkey;
                transport.sendTo(authMsg.getBytes(), ip, port);

                if (transport instanceof org.file.transfer.transport.TransportUDP udp) {
                    udp.getSocket().setSoTimeout(2000); // 2 seconds timeout per try
                }

                byte[] response = transport.receive();
                String reply = new String(response).trim();

                if (reply.equals("AUTH_RESPONSE:OK")) {
                    return true;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("[Sender] Handshake timeout, retrying... (" + (i + 1) + "/" + maxRetries + ")");
            } catch (Exception e) {
                System.out.println("[Sender] Handshake failed: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public void sendFileOrFolder(File file, String targetIp, int targetPort, SendFilesController callback) {
        if (file.isDirectory()) {
            sendFolder(file, targetIp, targetPort, callback);
        } else {
            sendFile(file, targetIp, targetPort, callback);
        }
    }

    public void sendFiles(List<File> files, String targetIp, int targetPort, SendFilesController callback) {
        for (File file : files) {
            if (file.isDirectory()) {
                sendFolder(file, targetIp, targetPort, callback);
            } else {
                sendFile(file, targetIp, targetPort, callback);
            }
            String receiverName = DiscoveryService.getInstance().getPeerName(targetIp);
            org.file.transfer.service.TransferHistoryService.getInstance().markAsSent(receiverName, file);
        }
        if (callback != null) {
            callback.onTransferComplete();
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFile(File file, String targetIp, int targetPort, SendFilesController callback) {
        sendFileInternal(file, targetIp, targetPort, callback, file.getName());
    }

    private void sendFileInternal(File file, String targetIp, int targetPort, SendFilesController callback,
            String remoteFileName) {
        try {
            long fileSize = file.length();
            int totalPackets = (int) Math.ceil(fileSize / (double) PACKET_SIZE);
            if (totalPackets == 0)
                totalPackets = 1;

            String myName = org.file.transfer.service.UserSession.getInstance().getUsername();
            if (myName == null)
                myName = "Unknown";
            String req = "FILE_REQ:" + file.lastModified() + ":" + totalPackets + ":" + remoteFileName + ":" + myName;

            BitSet receivedBlocks = new BitSet(totalPackets);
            boolean handshakeDone = false;

            for (int i = 0; i < 5; i++) {
                transport.sendTo(req.getBytes(), targetIp, targetPort);
                try {
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

                    // tao doi tuong FilePacket
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

    public void close() {
        try {
            if (transport != null)
                transport.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}