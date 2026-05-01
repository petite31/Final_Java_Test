package org.file.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignalingServer {
    private static final int PORT = 8888;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // Protocol types (must match JavaFX client)
    private static final byte TYPE_LOGIN = 1;
    private static final byte TYPE_REGISTER = 2;
    private static final byte TYPE_LOGIN_RESPONSE = 3;
    private static final byte TYPE_REGISTER_RESPONSE = 4;
    private static final byte TYPE_ERROR = 0;

    // Extended Protocol types for P2P connection signaling

    // History Protocol types
    private static final byte TYPE_LOG_TRANSFER = 10;
    private static final byte TYPE_GET_HISTORY = 11;
    private static final byte TYPE_DELETE_HISTORY = 12;
    private static final byte TYPE_DELETE_ALL_HISTORY = 13;
    private static final byte TYPE_SUCCESS = 100;
    private static final byte TYPE_HISTORY_DATA = 101;

    public void start() {
        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            System.out.println("UDP Signaling Server started on port " + PORT);

            byte[] receiveData = new byte[8192];

            while (!serverSocket.isClosed()) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                // Copy data to hand off to thread
                byte[] data = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0,
                        receivePacket.getLength());
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                pool.execute(() -> handlePacket(serverSocket, data, clientAddress, clientPort));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handlePacket(DatagramSocket socket, byte[] data, InetAddress address, int port) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream dis = new DataInputStream(bais)) {

            if (dis.available() == 0)
                return;

            byte type = dis.readByte();
            switch (type) {
                case TYPE_LOGIN:
                    handleLogin(socket, dis, address, port);
                    break;
                case TYPE_REGISTER:
                    handleRegister(socket, dis, address, port);
                    break;
                case TYPE_LOG_TRANSFER:
                    handleLogTransfer(socket, dis, address, port);
                    break;
                case TYPE_GET_HISTORY:
                    handleGetHistory(socket, dis, address, port);
                    break;
                case TYPE_DELETE_HISTORY:
                    handleDeleteHistory(socket, dis, address, port);
                    break;
                case TYPE_DELETE_ALL_HISTORY:
                    handleDeleteAllHistory(socket, dis, address, port);
                    break;
                default:
                    System.err.println("Unknown packet type: " + type);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleLogin(DatagramSocket socket, DataInputStream dis, InetAddress address, int port)
            throws IOException {
        String user = dis.readUTF();
        String pass = dis.readUTF();

        boolean isValid = DatabaseManager.validateUser(user, pass);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeByte(TYPE_LOGIN_RESPONSE);
            dos.writeBoolean(isValid);

            if (isValid) {
                String role = DatabaseManager.getUserRole(user);
                dos.writeUTF("Login successful");
                dos.writeUTF(role);
                dos.writeInt(DatabaseManager.getUserIdByUsername(user));

                // Treat signaling over UDP for tracking
                SessionManager.addClient(user, address.getHostAddress(), port);
            } else {
                dos.writeUTF("Invalid username or password");
            }
            dos.flush();
            byte[] responseData = baos.toByteArray();
            socket.send(new DatagramPacket(responseData, responseData.length, address, port));
        }
    }

    private void handleRegister(DatagramSocket socket, DataInputStream dis, InetAddress address, int port)
            throws IOException {
        String user = dis.readUTF();
        String pass = dis.readUTF();
        String role = "USER";

        if (dis.available() > 0) {
            try {
                role = dis.readUTF();
            } catch (EOFException e) {
            }
        }

        boolean success = DatabaseManager.registerUser(user, pass, role);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeByte(TYPE_REGISTER_RESPONSE);
            dos.writeBoolean(success);
            dos.writeUTF(success ? "Registration successful" : "Username already exists");
            dos.flush();

            byte[] responseData = baos.toByteArray();
            socket.send(new DatagramPacket(responseData, responseData.length, address, port));
        }
    }

    private void handleLogTransfer(DatagramSocket socket, DataInputStream dis, InetAddress address, int port)
            throws IOException {
        String sender = dis.readUTF();
        String receiver = dis.readUTF();
        String fileName = dis.readUTF();
        long size = dis.readLong();
        String status = dis.readUTF();
        try {
            dis.readUTF(); // filePath might be missing
        } catch (EOFException e) {
            // filePath might be missing
        }

        DatabaseManager.logTransferStatus(sender, receiver, fileName, size, status);
        // Fire-and-forget: HistoryService on client doesn't wait for a response for
        // logTransfer
    }

    private void handleGetHistory(DatagramSocket socket, DataInputStream dis, InetAddress address, int port)
            throws IOException {
        String username = dis.readUTF();
        java.util.List<DatabaseManager.TransferRecord> history = DatabaseManager.getTransferHistory(username);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeByte(TYPE_HISTORY_DATA);
            dos.writeInt(history.size());
            for (DatabaseManager.TransferRecord rec : history) {
                dos.writeInt(rec.id);
                dos.writeUTF(rec.sender);
                dos.writeUTF(rec.receiver);
                dos.writeUTF(rec.fileName);
                dos.writeLong(rec.size);
                dos.writeUTF(rec.timestamp);
                dos.writeUTF(rec.status);
                dos.writeUTF(""); // empty filepath
            }
            dos.flush();
            byte[] responseData = baos.toByteArray();
            socket.send(new DatagramPacket(responseData, responseData.length, address, port));
        }
    }

    private void handleDeleteHistory(DatagramSocket socket, DataInputStream dis, InetAddress address, int port)
            throws IOException {
        int id = dis.readInt();
        String username = dis.readUTF();

        boolean success = DatabaseManager.deleteHistory(id, username);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeByte(success ? TYPE_SUCCESS : TYPE_ERROR);
            dos.flush();
            byte[] responseData = baos.toByteArray();
            socket.send(new DatagramPacket(responseData, responseData.length, address, port));
        }
    }

    private void handleDeleteAllHistory(DatagramSocket socket, DataInputStream dis, InetAddress address, int port)
            throws IOException {
        String username = dis.readUTF();

        boolean success = DatabaseManager.deleteAllHistory(username);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeByte(success ? TYPE_SUCCESS : TYPE_ERROR);
            dos.flush();
            byte[] responseData = baos.toByteArray();
            socket.send(new DatagramPacket(responseData, responseData.length, address, port));
        }
    }
}
