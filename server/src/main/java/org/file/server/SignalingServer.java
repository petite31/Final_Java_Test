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
    private static final byte TYPE_ONLINE_USERS = 5;

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
}
