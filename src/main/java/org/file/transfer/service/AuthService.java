package org.file.transfer.service;

import java.io.*;
import java.net.*;

public class AuthService {
    private static final String SERVER_HOST = "192.168.16.74";
    private static final int SERVER_PORT = 8888;
    private static final int BUFFER_SIZE = 4096;
    private static final int TIMEOUT_MS = 3000;
    private static final int MAX_RETRIES = 3;

    private static final byte TYPE_LOGIN = 1;
    private static final byte TYPE_REGISTER = 2;
    private static final byte TYPE_LOGIN_RESPONSE = 3;
    private static final byte TYPE_REGISTER_RESPONSE = 4;
    private static final byte TYPE_ERROR = 0;

    public boolean login(String username, String password, StringBuilder messageBuffer) {
        return sendRequest(TYPE_LOGIN, username, password, null, messageBuffer);
    }

    public boolean register(String username, String password, String role, StringBuilder messageBuffer) {
        return sendRequest(TYPE_REGISTER, username, password, role, messageBuffer);
    }

    private boolean sendRequest(byte type, String username, String password, String role, StringBuilder messageBuffer) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeByte(type);
            dos.writeUTF(username);
            dos.writeUTF(password);
            if (type == TYPE_REGISTER && role != null) {
                dos.writeUTF(role);
            }

            byte[] sendData = baos.toByteArray();

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(TIMEOUT_MS);
                InetAddress serverAddress = InetAddress.getByName(SERVER_HOST);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                byte[] receiveData = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                for (int i = 0; i < MAX_RETRIES; i++) {
                    try {
                        socket.send(sendPacket);
                        socket.receive(receivePacket);

                        try (ByteArrayInputStream bais = new ByteArrayInputStream(receivePacket.getData(),
                                receivePacket.getOffset(), receivePacket.getLength());
                                DataInputStream dis = new DataInputStream(bais)) {

                            byte responseType = dis.readByte();

                            if (responseType == TYPE_LOGIN_RESPONSE || responseType == TYPE_REGISTER_RESPONSE) {
                                boolean success = dis.readBoolean();
                                String message = dis.readUTF();

                                if (messageBuffer != null) {
                                    messageBuffer.append(message);
                                }

                                if (responseType == TYPE_LOGIN_RESPONSE && success && dis.available() > 0) {
                                    String userRole = dis.readUTF();
                                    System.out.println("Logged in as: " + userRole);
                                }

                                return success;
                            } else {
                                String error = dis.readUTF();
                                if (messageBuffer != null)
                                    messageBuffer.append(error);
                                return false;
                            }
                        }

                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout, retrying... (" + (i + 1) + "/" + MAX_RETRIES + ")");
                    }
                }
                if (messageBuffer != null)
                    messageBuffer.append("Server unavailable (Timeout)");
            }
        } catch (IOException e) {
            if (messageBuffer != null)
                messageBuffer.append("Network error: ").append(e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
}
