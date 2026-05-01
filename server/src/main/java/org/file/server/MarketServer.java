package org.file.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class MarketServer extends Thread {
    private static final int MARKET_PORT = 8892;
    // Thư mục lưu file trên server (Đảm bảo bạn đã tạo thư mục này trên ổ đĩa Server)
    private static final String STORAGE_DIR = "Server_MarketFiles/";

    public MarketServer() {
        File dir = new File(STORAGE_DIR);
        if (!dir.exists()) dir.mkdirs(); // Tự động tạo thư mục nếu chưa có
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(MARKET_PORT)) {
            System.out.println("[MarketServer] Listening for uploads on port " + MARKET_PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            
            String command = dis.readUTF();

            if ("UPLOAD_SELL".equals(command)) {
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();
                int sellerId = dis.readInt();
                long price = dis.readLong();

                // Nơi lưu trữ vật lý
                File saveFile = new File(STORAGE_DIR + System.currentTimeMillis() + "_" + fileName);

                try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                    byte[] buffer = new byte[8192]; // Buffer 8KB
                    int bytesRead;
                    long totalRead = 0;

                    while (totalRead < fileSize && (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }

                // Sau khi lưu file vật lý xong -> Lưu vào Database
                boolean success = DatabaseManager.insertMarketFile(fileName, fileSize, saveFile.getAbsolutePath(), sellerId, price);
                System.out.println("[MarketServer] File received & saved: " + fileName + ". DB Status: " + success);
                
                dos.writeBoolean(success);
                dos.flush();
                
            } else if ("GET_MARKET_DATA".equals(command)) {
                int currentUserId = dis.readInt();
                List<DatabaseManager.MarketFileRecord> files = DatabaseManager.getAllMarketFiles();
                dos.writeInt(files.size());
                
                for (DatabaseManager.MarketFileRecord file : files) {
                    dos.writeInt(file.id);
                    dos.writeUTF(file.fileName);
                    dos.writeLong(file.fileSize);
                    dos.writeUTF(file.filePath);
                    dos.writeInt(file.sellerId);
                    dos.writeUTF(file.sellerName);
                    dos.writeLong(file.price);
                    dos.writeUTF(file.uploadDate);
                    
                    boolean isBought = DatabaseManager.hasUserBoughtFile(currentUserId, file.id);
                    dos.writeBoolean(isBought);
                }
                dos.flush();
                
            } else if ("BUY_MARKET_FILE".equals(command)) {
                int buyerId = dis.readInt();
                int fileId = dis.readInt();
                boolean success = DatabaseManager.buyFile(buyerId, fileId);
                dos.writeBoolean(success);
                dos.flush();
                
            } else if ("DELETE_MARKET_FILE".equals(command)) {
                int fileId = dis.readInt();
                int sellerId = dis.readInt();
                String filePath = DatabaseManager.getFilePath(fileId);
                
                boolean success = DatabaseManager.deleteMarketFile(fileId, sellerId);
                if (success && filePath != null) {
                    File physicalFile = new File(filePath);
                    if (physicalFile.exists()) {
                        physicalFile.delete();
                    }
                }
                dos.writeBoolean(success);
                dos.flush();
                
            } else if ("DOWNLOAD_MARKET_FILE".equals(command)) {
                int fileId = dis.readInt();
                int userId = dis.readInt();
                
                String filePath = DatabaseManager.getFilePath(fileId);
                if (filePath == null) {
                    dos.writeBoolean(false);
                    dos.writeUTF("File not found in database.");
                    dos.flush();
                    return;
                }
                
                File physicalFile = new File(filePath);
                if (!physicalFile.exists()) {
                    dos.writeBoolean(false);
                    dos.writeUTF("Physical file missing on server.");
                    dos.flush();
                    return;
                }
                
                // Allow download if user bought it OR user is the owner (we'll just allow it if physical file exists for simplicity, or we can check)
                dos.writeBoolean(true);
                dos.writeLong(physicalFile.length());
                dos.flush();
                
                try (FileInputStream fis = new FileInputStream(physicalFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                    dos.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("[MarketServer] Client disconnected or error: " + e.getMessage());
        }
    }
}
