package org.file.transfer.network;

import org.file.transfer.controller.SendFilesController;
import org.file.transfer.model.FilePacket;
import org.file.transfer.model.FolderManifest;
import org.file.transfer.model.TransferStats;

import java.io.*;
import java.net.*;
import java.util.BitSet;
import java.util.Stack;

public class FileSender {
    private final TransferManager manager;
    private final DatagramSocket socket;

    private static final int PACKET_SIZE = 60000; // Payload size

    public FileSender(TransferManager manager) throws SocketException {
        this.manager = manager;
        this.socket = new DatagramSocket();
    }

    public boolean performHandshake(String ip, int port, String passkey) {
        try {
            socket.setSoTimeout(3000);
            String authMsg = "AUTH_REQUEST:" + passkey;
            InetAddress address = InetAddress.getByName(ip);

            DatagramPacket packet = new DatagramPacket(
                    authMsg.getBytes(), authMsg.length(), address, port);
            socket.send(packet);

            byte[] buf = new byte[256];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);

            String reply = new String(resp.getData(), 0, resp.getLength());
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
            // 1. Build Manifest
            FolderManifest manifest = new FolderManifest(folder.getName());
            Stack<File> stack = new Stack<>();
            stack.push(folder);

            // Just for recursive scan
            int rootPathLen = folder.getParentFile().getAbsolutePath().length();

            while (!stack.isEmpty()) {
                File current = stack.pop();
                File[] files = current.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String relPath = f.getAbsolutePath().substring(rootPathLen + 1); // +1 for separator
                        if (f.isDirectory()) {
                            manifest.addItem(relPath, 0, 0); // Directory
                            stack.push(f);
                        } else {
                            manifest.addItem(relPath, f.length(), f.lastModified());
                        }
                    }
                }
            }

            // 2. Serialize Manifest to temp file
            File tempManifest = File.createTempFile("manifest", ".manifest");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tempManifest))) {
                oos.writeObject(manifest);
            }

            // 3. Send Manifest (as special file)
            sendFileInternal(tempManifest, targetIp, targetPort, callback, folder.getName() + ".manifest");
            tempManifest.delete();

            // 4. Send Files
            stack.push(folder); // Reuse stack for traversal

            while (!stack.isEmpty()) {
                File current = stack.pop();
                File[] files = current.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isDirectory()) {
                            stack.push(f);
                        } else {
                            String relPath = f.getAbsolutePath().substring(rootPathLen + 1);
                            // We can send file with name as relPath so receiver knows structure?
                            // Actually, receiver just processes files.
                            // But if we send "sub/foo.txt", standard logic might flatten it if we don't
                            // handle paths?
                            // The receiver logic `new File(dir, fp.fileName())` creates file in root
                            // download dir.
                            // WE NEED TO SEND RELATIVE PATH as filename!
                            // Receiver uses `fp.fileName()` which is `String`.
                            // So we send relative path.
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
            InetAddress address = InetAddress.getByName(targetIp);
            long fileSize = file.length();
            int totalPackets = (int) Math.ceil(fileSize / (double) PACKET_SIZE);
            if (totalPackets == 0)
                totalPackets = 1; // Empty file

            // 1. Send FILE_REQ
            // msg: FILE_REQ:<chksum>:<totalPackets>:<fileName>
            // We use timestamp as checksum for simple logic
            String req = "FILE_REQ:" + file.lastModified() + ":" + totalPackets + ":" + remoteFileName;
            DatagramPacket reqPkt = new DatagramPacket(req.getBytes(), req.length(), address, targetPort);

            BitSet receivedBlocks = new BitSet(totalPackets);
            boolean handshakeDone = false;

            for (int i = 0; i < 5; i++) { // Resize 5 times
                socket.send(reqPkt);
                socket.setSoTimeout(2000);
                try {
                    byte[] buf = new byte[65000]; // Large buffer for bitmap
                    DatagramPacket resp = new DatagramPacket(buf, buf.length);
                    socket.receive(resp);

                    // Possible responses:
                    // String: "FILE_ACK:START"
                    // Object: BitSet

                    try {
                        ObjectInputStream ois = new ObjectInputStream(
                                new ByteArrayInputStream(resp.getData(), 0, resp.getLength()));
                        Object obj = ois.readObject();
                        if (obj instanceof BitSet) {
                            receivedBlocks = (BitSet) obj;
                            handshakeDone = true;
                            System.out.println(
                                    "[Sender] Resuming logic. Blocks received: " + receivedBlocks.cardinality());
                            break;
                        }
                    } catch (Exception ex) {
                        // Not an object, maybe string
                        String s = new String(resp.getData(), 0, resp.getLength());
                        if (s.startsWith("FILE_ACK:START")) {
                            handshakeDone = true; // Nothing received yet
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

            // 2. Send Missing Blocks
            TransferStats stats = new TransferStats(fileSize);
            // Pre-calculate transferred bytes
            stats.update(receivedBlocks.cardinality() * PACKET_SIZE);

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                for (int packetId = 0; packetId < totalPackets; packetId++) {
                    if (receivedBlocks.get(packetId))
                        continue; // Skip if already received

                    raf.seek((long) packetId * PACKET_SIZE);
                    byte[] buffer = new byte[PACKET_SIZE];
                    int bytesRead = raf.read(buffer);
                    if (bytesRead == -1)
                        break; // Should not happen if size is correct

                    // Trim if last packet
                    byte[] data = (bytesRead == PACKET_SIZE) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                    FilePacket packet = new FilePacket(
                            packetId,
                            data,
                            remoteFileName,
                            fileSize,
                            totalPackets,
                            packetId == totalPackets - 1);

                    // Send with ACK (Reliability)
                    sendPacketReliable(packet, address, targetPort);

                    // Update Stats
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

    private void sendPacketReliable(FilePacket packetData, InetAddress address, int port) throws IOException {
        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(packetData);
        oos.close();
        byte[] raw = baos.toByteArray();
        DatagramPacket dp = new DatagramPacket(raw, raw.length, address, port);

        boolean ack = false;
        int retry = 0;
        while (!ack && retry < 5) {
            socket.send(dp);
            socket.setSoTimeout(500); // 500ms timeout for ACK
            try {
                byte[] ackBuf = new byte[64];
                DatagramPacket ackPkt = new DatagramPacket(ackBuf, ackBuf.length);
                socket.receive(ackPkt);
                String msg = new String(ackPkt.getData(), 0, ackPkt.getLength());
                if (msg.startsWith("ACK_" + packetData.packetId())) {
                    ack = true;
                }
                // Ignore other ACKs (old ones)
            } catch (SocketTimeoutException e) {
                retry++;
            }
        }
    }

    public void sendAck(int packetId, InetAddress address, int port) {
        try {
            String ackMsg = "ACK_" + packetId;
            DatagramPacket packet = new DatagramPacket(
                    ackMsg.getBytes(), ackMsg.length(), address, port);
            socket.send(packet);
        } catch (Exception e) {
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}