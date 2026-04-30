package org.file.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

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
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
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
                DatabaseManager db = new DatabaseManager(); // Hoặc dùng instance có sẵn của bạn
                boolean success = db.insertMarketFile(fileName, fileSize, saveFile.getAbsolutePath(), sellerId, price);

                System.out.println("[MarketServer] File received & saved: " + fileName + ". DB Status: " + success);
            }
        } catch (IOException e) {
            System.err.println("[MarketServer] Client disconnected or error.");
        }
    }
}
