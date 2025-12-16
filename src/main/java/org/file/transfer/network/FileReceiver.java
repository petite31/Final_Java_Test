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
    private String myPasskey;
    private final Set<String> authenticatedIps = Collections.synchronizedSet(new HashSet<>());
    private final BlockFileManager blockManager = BlockFileManager.getInstance();

    // Performance: Cache open file handles
    private final Map<String, RandomAccessFile> openFileHandles = new ConcurrentHashMap<>();
    // Metadata: Store sender names by IP
    private final Map<String, String> senderNames = new ConcurrentHashMap<>();

    public FileReceiver(TransferManager manager, int requestedPort, String passkey) throws SocketException {
        this.myPasskey = passkey;
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
        if (transport instanceof org.file.transfer.transport.TransportUDP udp) {
            return udp.getSocket().getLocalPort();
        }
        return 0;
    }

    public void setPasskey(String passkey) {
        this.myPasskey = passkey;
        System.out.println("[Receiver] Passkey updated to: " + passkey);
    }

    public void close() {
        try {
            // Close all open handles
            for (RandomAccessFile raf : openFileHandles.values()) {
                try {
                    raf.close();
                } catch (Exception ignored) {
                }
            }
            openFileHandles.clear();
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

                    boolean handled = false;
                    if (data.length < 1024) {
                        try {
                            String msg = new String(data).trim();
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

        // RESUME REQUEST V3
        // FORMAT: FILE_REQ:<timestamp>:<totalPackets>:<fileName>:<senderName>
        if (msg.startsWith("FILE_REQ:")) {
            if (!authenticatedIps.contains(senderIp) && !SettingsManager.getInstance().isAllowExternal()) {
                // Ignore
                return true;
            }
            String[] parts = msg.split(":", 6); // Allow for extra parts if needed
            if (parts.length >= 4) {
                String fileName = parts[3];
                int totalPackets = Integer.parseInt(parts[2]);

                // V3: Extract sender name
                if (parts.length >= 5) {
                    String senderName = parts[4];
                    senderNames.put(senderIp, senderName);
                }

                BitSet received = blockManager.getReceivedBlocks(fileName);

                if (received.isEmpty()) {
                    transport.sendTo("FILE_ACK:START".getBytes(), senderIp, senderPort);
                } else {
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
                processFilePacket(fp, senderIp);
            }

        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    private void processFilePacket(FilePacket fp, String senderIp) {
        try {
            BitSet received = blockManager.getReceivedBlocks(fp.fileName());
            if (received.get(fp.packetId()))
                return; // Duplicate

            // Performance: Use cached handle
            RandomAccessFile raf = openFileHandles.computeIfAbsent(fp.fileName(), k -> {
                try {
                    String downloadDir = SettingsManager.getInstance().getDownloadDirectory();
                    File dir = new File(downloadDir);
                    if (!dir.exists())
                        dir.mkdirs();
                    File outputFile = new File(dir, fp.fileName());
                    return new RandomAccessFile(outputFile, "rw");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return null;
                }
            });

            if (raf != null) {
                long offset = (long) fp.packetId() * 60000;
                synchronized (raf) {
                    raf.seek(offset);
                    raf.write(fp.data());
                }
            }

            blockManager.markBlockReceived(fp.fileName(), fp.packetId(), fp.totalPackets());

            if (blockManager.isComplete(fp.fileName(), fp.totalPackets())) {
                System.out.println("[Receiver] File complete: " + fp.fileName());

                // Cleanup handle
                RandomAccessFile openRaf = openFileHandles.remove(fp.fileName());
                if (openRaf != null) {
                    try {
                        openRaf.close();
                    } catch (IOException ignored) {
                    }
                }

                blockManager.cleanup(fp.fileName());

                File outputFile = new File(SettingsManager.getInstance().getDownloadDirectory(), fp.fileName());

                if (fp.fileName().endsWith(".manifest")) {
                    processManifest(outputFile);
                } else {
                    // Log History
                    String senderName = senderNames.getOrDefault(senderIp, "Unknown Peer");
                    String myName = org.file.transfer.service.UserSession.getInstance().getUsername();
                    if (myName == null)
                        myName = "Unknown";
                    org.file.transfer.service.HistoryService.getInstance().logTransfer(senderName, myName,
                            fp.fileName(),
                            outputFile.length(), "Received");
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