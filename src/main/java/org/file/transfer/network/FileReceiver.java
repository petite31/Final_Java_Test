package org.file.transfer.network;

import org.file.transfer.model.FilePacket;
import org.file.transfer.model.FolderManifest;
import org.file.transfer.persistence.BlockFileManager;
import org.file.transfer.transport.Transport;
import org.file.transfer.transport.TransportManager;
import org.file.transfer.utils.SettingsManager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileReceiver {
    private final Transport transport;
    private final FileSender fileSender;
    private final String myPasskey;
    private final Set<String> authenticatedIps = Collections.synchronizedSet(new HashSet<>());
    private final BlockFileManager blockManager = BlockFileManager.getInstance();

    public FileReceiver(TransferManager manager, int requestedPort, String passkey) throws SocketException {
        this.myPasskey = passkey;
        // Use TransportManager to create transport. For now fixed UDP for receiver
        // listening.
        // Bluetooth listener would need separate thread/transport.
        this.transport = TransportManager.getInstance().createTransport("UDP"); // Default to UDP
        try {
            this.transport.bind(requestedPort);
        } catch (IOException e) {
            throw new SocketException(e.getMessage());
        }

        System.out.println("[DEBUG] FileReceiver listening on " + requestedPort);

        this.fileSender = new FileSender(manager);
        startReceivingLoop();
    }

    public int getPort() {
        // Transport abstraction doesn't easily expose local port if bind() logic
        // varies.
        // But for UDP we might need it. Transport interface needs getLocalPort() if
        // generic.
        // Or we cast.
        if (transport instanceof org.file.transfer.transport.TransportUDP udp) {
            return udp.getSocket().getLocalPort();
        }
        return 0;
    }

    public void close() {
        try {
            transport.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startReceivingLoop() {
        new Thread(() -> {
            while (transport.isBound()) {
                try {
                    byte[] data = transport.receive();
                    String senderIp = transport.getLastSenderAddress();
                    int senderPort = transport.getLastSenderPort();

                    // Heuristic: Check if Command (String) or Data (Object)
                    // Packets < 1024 bytes and starting with known prefixes are commands
                    boolean handled = false;
                    if (data.length < 1024) {
                        try {
                            String msg = new String(data).trim(); // trim to remove nulls if any
                            if (handleCommand(msg, senderIp, senderPort)) {
                                handled = true;
                            }
                        } catch (Exception e) {
                            // Not a string or command
                        }
                    }

                    if (!handled) {
                        handleDataPacket(data, senderIp, senderPort);
                    }

                } catch (Exception e) {
                    if (!transport.isBound())
                        break;
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean handleCommand(String msg, String senderIp, int senderPort) throws IOException {
        if (msg.startsWith("PING_FROM_P2P_APP")) {
            transport.sendTo("PONG_FROM_P2P_APP_OK".getBytes(), senderIp, senderPort);
            return true;
        }

        if (msg.startsWith("AUTH_REQUEST:")) {
            String receivedPasskey = msg.split(":")[1];
            if (this.myPasskey.equals(receivedPasskey)) {
                authenticatedIps.add(senderIp);
                transport.sendTo("AUTH_RESPONSE:OK".getBytes(), senderIp, senderPort);
                System.out.println("[Receiver] Auth SUCCESS for " + senderIp);
            } else {
                transport.sendTo("AUTH_RESPONSE:FAIL".getBytes(), senderIp, senderPort);
                System.out.println("[Receiver] Auth FAILED for " + senderIp);
            }
            return true;
        }

        // RESUME REQUEST V2
        // FORMAT: FILE_REQ:<chksum>:<totalPackets>:<fileName>
        if (msg.startsWith("FILE_REQ:")) {
            if (!authenticatedIps.contains(senderIp) && !SettingsManager.getInstance().isAllowExternal()) {
                // Ignore
                return true;
            }
            String[] parts = msg.split(":", 4);
            if (parts.length >= 3) {
                String fileName = parts[3];
                int totalPackets = Integer.parseInt(parts[2]);

                BitSet received = blockManager.getReceivedBlocks(fileName);

                if (received.isEmpty()) {
                    transport.sendTo("FILE_ACK:START".getBytes(), senderIp, senderPort);
                } else {
                    // Send MISSING_BLOCKS (Compressed? Or just the BitSet object)
                    // Implementation: Send BitSet object.
                    sendBitmap(received, senderIp, senderPort);
                }
            }
            return true;
        }

        return false;
    }

    private void handleDataPacket(byte[] data, String senderIp, int senderPort) {
        if (!authenticatedIps.contains(senderIp))
            return;

        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            Object obj = ois.readObject();

            if (obj instanceof FilePacket fp) {
                // Send Ack
                fileSender.sendAck(fp.packetId(), senderIp, senderPort);
                processFilePacket(fp);
            }

        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    private void processFilePacket(FilePacket fp) {
        try {
            String downloadDir = SettingsManager.getInstance().getDownloadDirectory();
            File dir = new File(downloadDir);
            if (!dir.exists())
                dir.mkdirs();

            File outputFile = new File(dir, fp.fileName());

            // Check block manager first
            BitSet received = blockManager.getReceivedBlocks(fp.fileName());
            if (received.get(fp.packetId()))
                return; // Duplicate

            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                long offset = (long) fp.packetId() * 60000;
                raf.seek(offset);
                raf.write(fp.data());
            }

            blockManager.markBlockReceived(fp.fileName(), fp.packetId(), fp.totalPackets());

            if (blockManager.isComplete(fp.fileName(), fp.totalPackets())) {
                System.out.println("[Receiver] File complete: " + fp.fileName());
                blockManager.cleanup(fp.fileName());

                if (fp.fileName().endsWith(".manifest")) {
                    processManifest(outputFile);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processManifest(File manifestFile) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(manifestFile))) {
            FolderManifest manifest = (FolderManifest) ois.readObject();
            String baseDir = SettingsManager.getInstance().getDownloadDirectory();
            File root = new File(baseDir, manifest.getRootFolderName());
            root.mkdirs();

            for (FolderManifest.ManifestItem item : manifest.getItems()) {
                File f = new File(root, item.relativePath());
                f.getParentFile().mkdirs();
            }
            System.out.println("[Receiver] Directory structure created for " + manifest.getRootFolderName());
            // Note: Files inside come as separate packets
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendBitmap(BitSet bs, String ip, int port) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(bs);
            oos.close();
            byte[] raw = baos.toByteArray();
            transport.sendTo(raw, ip, port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}