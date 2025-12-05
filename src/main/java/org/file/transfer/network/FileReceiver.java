package org.file.transfer.network;

import org.file.transfer.model.FilePacket;
import org.file.transfer.model.FolderManifest;
import org.file.transfer.utils.SettingsManager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileReceiver {
    private final DatagramSocket socket;
    private final FileSender fileSender;
    private final String myPasskey;
    private final Set<String> authenticatedIps = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, BitSet> transferProgress = new ConcurrentHashMap<>(); // Key: fileName

    // Default buffer size
    private static final int BUFFER_SIZE = 65507; // Max UDP payload

    public FileReceiver(TransferManager manager, int requestedPort, String passkey) throws SocketException {
        this.myPasskey = passkey;
        this.socket = new DatagramSocket(requestedPort);

        // Print actual port
        System.out.println("[DEBUG] FileReceiver listening on " + socket.getLocalPort() + " | Passkey: " + myPasskey);

        this.fileSender = new FileSender(manager);
        startReceivingLoop();
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    private void startReceivingLoop() {
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String senderIp = packet.getAddress().getHostAddress();

                    // 1. Check Payload Type
                    // We assume Object stream for FilePackets, but String for commands?
                    // Old protocol used mixed. Let's try to peek or just handle exceptions.
                    // Or we can assume everything is object? No, Handshake was string.
                    // Simple heuristic: If it starts with text "PING" or "AUTH", it is string.
                    // But `FilePacket` is binary.

                    // Let's try to read as string first if short
                    boolean handled = false;
                    if (packet.getLength() < 1024) { // Commands are short
                        String msg = new String(packet.getData(), 0, packet.getLength());
                        if (handleCommand(msg, packet)) {
                            handled = true;
                        }
                    }

                    if (!handled) {
                        handleDataPacket(packet, senderIp);
                    }

                } catch (SocketException e) {
                    if (socket.isClosed())
                        break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean handleCommand(String msg, DatagramPacket packet) throws IOException {
        String senderIp = packet.getAddress().getHostAddress();

        // PING
        if (msg.startsWith("PING_FROM_P2P_APP")) {
            sendString("PONG_FROM_P2P_APP_OK", packet.getAddress(), packet.getPort());
            return true;
        }

        // AUTH
        if (msg.startsWith("AUTH_REQUEST:")) {
            String receivedPasskey = msg.split(":")[1];
            if (this.myPasskey.equals(receivedPasskey)) {
                authenticatedIps.add(senderIp);
                sendString("AUTH_RESPONSE:OK", packet.getAddress(), packet.getPort());
                System.out.println("[Receiver] Auth SUCCESS for " + senderIp);
            } else {
                sendString("AUTH_RESPONSE:FAIL", packet.getAddress(), packet.getPort());
                System.out.println("[Receiver] Auth FAILED for " + senderIp);
            }
            return true;
        }

        // FILE INFO REQUEST (For Resume)
        // FORMAT: FILE_REQ:<chksum>:<totalPackets>:<fileName>
        if (msg.startsWith("FILE_REQ:")) {
            if (!authenticatedIps.contains(senderIp) && !SettingsManager.getInstance().isAllowExternal()) {
                // Ignore if strict
            }
            // Parse
            String[] parts = msg.split(":", 4); // limit 4 to keep filename safe
            if (parts.length >= 3) {
                String fileName = parts[3];
                // Check if we have progress
                BitSet progress = loadProgress(fileName);
                if (progress == null) {
                    sendString("FILE_ACK:START", packet.getAddress(), packet.getPort());
                    // Init new progress
                    transferProgress.put(fileName, new BitSet());
                } else {
                    // Send existing bitmap
                    // Compressed format? For now just say "RESUME" and let sender query?
                    // Or send "FILE_ACK:RESUME:<base64_bitmap>"?
                    // BitSet Serializable.
                    sendBitmap(progress, packet.getAddress(), packet.getPort());
                }
            }
            return true;
        }

        // FOLDER MANIFEST START
        // Just treated as a file with special name usually?
        // Or we handle ".manifest" extension in handleDataPacket.

        return false;
    }

    private void handleDataPacket(DatagramPacket packet, String senderIp) {
        if (!authenticatedIps.contains(senderIp))
            return; // Security

        try {
            ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(packet.getData(), 0, packet.getLength()));
            Object obj = ois.readObject();

            if (obj instanceof FilePacket fp) {
                // Send MSG ACK (UDP reliability)
                // We Ack every packet? Or window?
                // Old code acked every packet.
                fileSender.sendAck(fp.packetId(), packet.getAddress(), packet.getPort());

                processFilePacket(fp);
            }

        } catch (Exception e) {
            // Not an object packet, maybe ignored
        }
    }

    private void processFilePacket(FilePacket fp) {
        try {
            String downloadDir = SettingsManager.getInstance().getDownloadDirectory();
            File dir = new File(downloadDir);
            if (!dir.exists())
                dir.mkdirs();

            File outputFile = new File(dir, fp.fileName());

            // If manifest file
            if (fp.fileName().endsWith(".manifest")) {
                // Write manifest memory/disk
                // Actually we treat it as normal file, then parse it when done?
                // Let's just write it.
            }

            if (fp.packetId() == 0 && !outputFile.exists()) {
                outputFile.createNewFile();
            }

            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                long offset = (long) fp.packetId() * 60000; // 60KB payload assumption (needs sync with Sender)
                raf.seek(offset);
                raf.write(fp.data());
            }

            // Update Progress
            BitSet bs = transferProgress.computeIfAbsent(fp.fileName(), k -> new BitSet());
            bs.set(fp.packetId());
            saveProgress(fp.fileName(), bs);

            if (fp.isLast()) {
                System.out.println("[Receiver] File complete: " + fp.fileName());
                transferProgress.remove(fp.fileName());
                new File(fp.fileName() + ".meta").delete();

                // If it was a manifest, process it
                if (fp.fileName().endsWith(".manifest")) {
                    processManifest(outputFile);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processManifest(File manifestFile) {
        // Read manifest, create folders
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(manifestFile))) {
            FolderManifest manifest = (FolderManifest) ois.readObject();
            String baseDir = SettingsManager.getInstance().getDownloadDirectory();
            File root = new File(baseDir, manifest.getRootFolderName());
            root.mkdirs();

            for (FolderManifest.ManifestItem item : manifest.getItems()) {
                File f = new File(root, item.relativePath());
                if (item.size() == 0) { // logic for empty folder?
                    // ManifestItem only has file.
                    // But we should Create parent dirs
                    f.getParentFile().mkdirs();
                } else {
                    f.getParentFile().mkdirs();
                }
            }
            // We don't need to create empty files, sender will send them.
            // Just structure.
            System.out.println("[Receiver] Directory structure created for " + manifest.getRootFolderName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendString(String msg, InetAddress addr, int port) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendBitmap(BitSet bs, InetAddress addr, int port) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(bs);
            oos.close();
            byte[] raw = baos.toByteArray();

            // We need to wrap it so sender knows it's a bitmap?
            // Or Sender expects it after FILE_REQ.
            // Let's wrap in a specific object or just send raw if sender waits for object.
            // Sender waits for "ACK string" usually.
            // Let's change protocol: Receiver sends "FILE_ACK:RESUME" string, Sender asks
            // for Bitmap?
            // BETTER: Embed in object.
            // Let's just use Java Serialization for the BitSet and let Sender readObject().

            DatagramPacket packet = new DatagramPacket(raw, raw.length, addr, port);
            socket.send(packet);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Persistence logic
    private void saveProgress(String fileName, BitSet bs) {
        // Save to .meta file occasionally?
        // Doing it every packet is slow.
        // Maybe every 100 packets?
        // For simplicity, we skip full persistence on every packet for now, rely on
        // memory.
        // Real persistence:
        /*
         * try (ObjectOutputStream oos = new ObjectOutputStream(new
         * FileOutputStream(fileName + ".meta"))) {
         * oos.writeObject(bs);
         * } catch (Exception e) {}
         */
    }

    private BitSet loadProgress(String fileName) {
        // Check memory
        if (transferProgress.containsKey(fileName))
            return transferProgress.get(fileName);

        // Check disk
        File meta = new File(fileName + ".meta");
        if (meta.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(meta))) {
                return (BitSet) ois.readObject();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}