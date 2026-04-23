package org.file.transfer.service;

import org.file.transfer.model.TransferRecord;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class HistoryService {
    private static final String SERVER_HOST = "192.168.1.4";
    private static final int SERVER_PORT = 8888;
    private static final int BUFFER_SIZE = 8192;
    private static final int TIMEOUT_MS = 5000;

    private static final byte TYPE_LOG_TRANSFER = 10;
    private static final byte TYPE_GET_HISTORY = 11;
    private static final byte TYPE_DELETE_HISTORY = 12;
    private static final byte TYPE_DELETE_ALL_HISTORY = 13;

    private static final byte TYPE_SUCCESS = 100;
    private static final byte TYPE_HISTORY_DATA = 101;

    private static HistoryService instance;

    private HistoryService() {
    }

    public static synchronized HistoryService getInstance() {
        if (instance == null) {
            instance = new HistoryService();
        }
        return instance;
    }

    public void logTransfer(String sender, String receiver, String fileName, long size, String status,
            String filePath) {
        new Thread(() -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos)) {

                dos.writeByte(TYPE_LOG_TRANSFER);
                dos.writeUTF(sender);
                dos.writeUTF(receiver);
                dos.writeUTF(fileName);
                dos.writeLong(size);
                dos.writeUTF(status);
                dos.writeUTF(filePath != null ? filePath : "");

                sendRequest(baos.toByteArray());
                System.out.println("[HistoryService] Logged transfer: " + fileName);

            } catch (IOException e) {
                System.err.println("[HistoryService] Failed to log transfer: " + e.getMessage());
            }
        }).start();
    }

    public List<TransferRecord> getHistory(String username) {
        List<TransferRecord> history = new ArrayList<>();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeByte(TYPE_GET_HISTORY);
            dos.writeUTF(username);

            byte[] response = sendRequest(baos.toByteArray());

            if (response != null) {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(response);
                        DataInputStream dis = new DataInputStream(bais)) {

                    byte type = dis.readByte();
                    if (type == TYPE_HISTORY_DATA) {
                        int count = dis.readInt();
                        for (int i = 0; i < count; i++) {
                            int id = dis.readInt();
                            String sender = dis.readUTF();
                            String receiver = dis.readUTF();
                            String fileName = dis.readUTF();
                            long size = dis.readLong();
                            String timestamp = dis.readUTF();
                            String status = dis.readUTF();
                            String filePath = "";
                            try {
                                filePath = dis.readUTF();
                            } catch (EOFException e) {
                            }

                            TransferRecord rec = new TransferRecord(id, sender, receiver, fileName, size, timestamp,
                                    status);
                            rec.setFilePath(filePath);
                            history.add(rec);
                        }
                    }
                }
                return history;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean deleteHistory(int id, String username) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeByte(TYPE_DELETE_HISTORY);
            dos.writeInt(id);
            dos.writeUTF(username);

            byte[] response = sendRequest(baos.toByteArray());
            return response != null && response[0] == TYPE_SUCCESS;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteAllHistory(String username) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeByte(TYPE_DELETE_ALL_HISTORY);
            dos.writeUTF(username);

            byte[] response = sendRequest(baos.toByteArray());
            return response != null && response[0] == TYPE_SUCCESS;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private byte[] sendRequest(byte[] data) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress serverAddress = InetAddress.getByName(SERVER_HOST);
            DatagramPacket sendPacket = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
            socket.send(sendPacket);

            byte[] receiveBuffer = new byte[BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            byte[] responseData = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), responseData, 0,
                    receivePacket.getLength());
            return responseData;

        } catch (IOException e) {
            return null;
        }
    }
}
