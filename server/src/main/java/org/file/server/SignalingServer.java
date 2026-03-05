package org.file.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignalingServer {
    private static final int PORT = 8888;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("TCP Signaling Server started on port " + PORT);

            while (true) {
                Socket client = serverSocket.accept();
                pool.execute(new ClientHandler(client));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream dis;
        private DataOutputStream dos;
        private String username;

        // Protocol types (must match JavaFX client)
        private static final byte TYPE_LOGIN = 1;
        private static final byte TYPE_REGISTER = 2;
        private static final byte TYPE_LOGIN_RESPONSE = 3;
        private static final byte TYPE_REGISTER_RESPONSE = 4;
        private static final byte TYPE_ERROR = 0;

        // Extended Protocol types for P2P connection signaling
        private static final byte TYPE_ONLINE_USERS = 5;
        // 6: REQUEST_CONNECT (Client A wants to transfer to B)
        // 7: CONNECT_INFO (Server sends IP/Port data of A to B and B to A)
        // 8: RELAY_REQUEST (Fallback to Relay)

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                dis = new DataInputStream(socket.getInputStream());
                dos = new DataOutputStream(socket.getOutputStream());

                while (!socket.isClosed()) {
                    byte type = dis.readByte();

                    switch (type) {
                        case TYPE_LOGIN:
                            handleLogin();
                            break;
                        case TYPE_REGISTER:
                            handleRegister();
                            break;
                        case -1: // Disconnect Custom Code
                            disconnect();
                            return;
                        default:
                            System.err.println("Unknown packet type: " + type);
                    }
                }
            } catch (EOFException e) {
                // Disconnected
                disconnect();
            } catch (IOException e) {
                disconnect();
            }
        }

        private void handleLogin() throws IOException {
            String user = dis.readUTF();
            String pass = dis.readUTF();

            boolean isValid = DatabaseManager.validateUser(user, pass);

            dos.writeByte(TYPE_LOGIN_RESPONSE);
            dos.writeBoolean(isValid);

            if (isValid) {
                this.username = user;
                String role = DatabaseManager.getUserRole(user);
                dos.writeUTF("Login successful");
                dos.writeUTF(role);

                SessionManager.addClient(user, socket.getInetAddress().getHostAddress(), socket.getPort());
            } else {
                dos.writeUTF("Invalid username or password");
            }
        }

        private void handleRegister() throws IOException {
            String user = dis.readUTF();
            String pass = dis.readUTF();
            String role = "USER";

            // Depends on client implementation, role might not be sent
            if (dis.available() > 0) {
                try {
                    role = dis.readUTF();
                } catch (EOFException e) {
                }
            }

            boolean success = DatabaseManager.registerUser(user, pass, role);

            dos.writeByte(TYPE_REGISTER_RESPONSE);
            dos.writeBoolean(success);
            dos.writeUTF(success ? "Registration successful" : "Username already exists");
        }

        private void disconnect() {
            if (username != null) {
                SessionManager.removeClient(username);
            }
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        }
    }
}
