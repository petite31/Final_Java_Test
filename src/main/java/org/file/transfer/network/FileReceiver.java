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

    private final Map<String, RandomAccessFile> openFileHandles = new ConcurrentHashMap<>();
    private final Map<String, String> senderNames = new ConcurrentHashMap<>();

    public FileReceiver(TransferManager manager, int requestedPort, String passkey) throws SocketException {
        this.myPasskey = passkey;
        this.transport = TransportManager.getInstance().createTransport("UDP");
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
                    // [1. NHẬN DỮ LIỆU] Lắng nghe liên tục từ cổng mạng
                    byte[] data = transport.receive();
                    String senderIp = transport.getLastSenderAddress();
                    int senderPort = transport.getLastSenderPort();

                    boolean handled = false;

                    // [2. KIỂM TRA LỆNH] Nếu gói tin nhỏ (< 1KB), có thể là lệnh (Command)
                    if (data.length < 1024) {
                        try {
                            String msg = new String(data).trim();
                            if (handleCommand(msg, senderIp, senderPort)) {
                                handled = true;
                            }
                        } catch (Exception e) {
                        }
                    }

                    // [3. XỬ LÝ DỮ LIỆU] Nếu không phải lệnh -> Gói tin chứa nội dung file
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

        // [LỆNH AUTH] Xác thực mật khẩu (Passkey)
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

        // [LỆNH HANDSHAKE] Yêu cầu gửi file từ Sender
        if (msg.startsWith("FILE_REQ:")) {
            // Kiểm tra quyền nhận file (đã xác thực hoặc cho phép người lạ)
            if (!authenticatedIps.contains(senderIp) && !SettingsManager.getInstance().isAllowExternal()) {
                return true;
            }
            String[] parts = msg.split(":", 6);
            if (parts.length >= 4) {
                String fileName = parts[3];
                int totalPackets = Integer.parseInt(parts[2]);

                if (parts.length >= 5) {
                    String senderName = parts[4];
                    senderNames.put(senderIp, senderName);
                }

                // [LOGIC RESUME] Kiểm tra file này đã nhận được phần nào chưa?
                BitSet received = blockManager.getReceivedBlocks(fileName);

                if (received.isEmpty()) {
                    // Chưa có gì -> Gửi ACK đồng ý nhận mới từ đầu
                    transport.sendTo("FILE_ACK:START".getBytes(), senderIp, senderPort);
                } else {
                    // Đã có một phần -> Gửi Bitmap báo cho Sender biết để gửi tiếp (Resume)
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
                // [RELIABLE UDP] Gửi xác nhận (ACK) ngay khi nhận được gói tin
                String ack = "ACK_" + fp.packetId();
                transport.sendTo(ack.getBytes(), senderIp, senderPort);
                processFilePacket(fp, senderIp);
            }

        } catch (Exception e) {
        }
    }

    private void processFilePacket(FilePacket fp, String senderIp) {
        try {
            // Kiểm tra trùng lặp: Gói này đã ghi rồi thì thôi
            BitSet received = blockManager.getReceivedBlocks(fp.fileName());
            if (received.get(fp.packetId()))
                return;

            // [GHI DỮ LIỆU] Mở file ở chế độ Random Access (Đọc/Ghi ngẫu nhiên)
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
                // Tính vị trí cần ghi: Offset = ID gói * Kích thước gói (60KB)
                long offset = (long) fp.packetId() * 60000;
                synchronized (raf) {
                    raf.seek(offset); // Nhảy đến đúng vị trí
                    raf.write(fp.data()); // Ghi dữ liệu xuống đĩa
                }
            }

            // Đánh dấu gói tin đã hoàn thành
            blockManager.markBlockReceived(fp.fileName(), fp.packetId(), fp.totalPackets());

            // [KIỂM TRA HOÀN THÀNH] Nếu đã nhận đủ 100% gói tin
            if (blockManager.isComplete(fp.fileName(), fp.totalPackets())) {
                System.out.println("[Receiver] File complete: " + fp.fileName());

                // Đóng file lại để hệ điều hành lưu hẳn xuống đĩa
                RandomAccessFile openRaf = openFileHandles.remove(fp.fileName());
                if (openRaf != null) {
                    try {
                        openRaf.close();
                    } catch (IOException ignored) {
                    }
                }

                // Xóa thông tin tạm trong bộ nhớ
                blockManager.cleanup(fp.fileName());

                File outputFile = new File(SettingsManager.getInstance().getDownloadDirectory(), fp.fileName());

                // Xử lý đặc biệt nếu là thư mục (Manifest) hoặc file thường
                if (fp.fileName().endsWith(".manifest")) {
                    processManifest(outputFile);
                } else {
                    // Ghi lại lịch sử nhận file
                    String senderName = senderNames.getOrDefault(senderIp, "Unknown Peer");
                    String myName = org.file.transfer.service.UserSession.getInstance().getUsername();
                    if (myName == null)
                        myName = "Unknown";
                    org.file.transfer.service.HistoryService.getInstance().logTransfer(senderName, myName,
                            fp.fileName(),
                            outputFile.length(), "Received", outputFile.getAbsolutePath());
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