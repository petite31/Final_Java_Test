package org.file.transfer.transport;

import java.io.IOException;
import java.net.*;

public class TransportUDP implements Transport {
    private DatagramSocket socket;
    private InetAddress connectedAddress;
    private int connectedPort;
    private boolean isBound = false;
    private boolean isConnected = false;

    // Last received info
    private String lastSenderIp;
    private int lastSenderPort;

    // Buffer for receiving - matches typical UDP max or Jumbo frame
    // We used 65507 previously.
    private static final int BUFFER_SIZE = 65507;

    public TransportUDP() throws SocketException {
        // Default constructor, creates unbound socket (ephemeral port)
        this.socket = new DatagramSocket();
    }

    public TransportUDP(int port) throws SocketException {
        this.socket = new DatagramSocket(port);
        this.isBound = true;
    }

    @Override
    public void connect(String address, int port) throws IOException {
        this.connectedAddress = InetAddress.getByName(address);
        this.connectedPort = port;
        this.isConnected = true;
        // logic: UDP doesn't really connect, but we store target
        // socket.connect() restricts receiving, which might not be what we want if we
        // want to hear from others.
        // So we just store state.
    }

    @Override
    public void bind(int port) throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        this.socket = new DatagramSocket(port);
        this.isBound = true;
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (!isConnected)
            throw new IOException("Not connected. Use sendTo or connect first.");
        sendTo(data, connectedAddress, connectedPort);
    }

    @Override
    public void sendTo(byte[] data, String address, int port) throws IOException {
        sendTo(data, InetAddress.getByName(address), port);
    }

    private void sendTo(byte[] data, InetAddress addr, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        socket.send(packet);
    }

    @Override
    public byte[] receive() throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        this.lastSenderIp = packet.getAddress().getHostAddress();
        this.lastSenderPort = packet.getPort();

        // Return actual data size
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
        return data;
    }

    @Override
    public String getLastSenderAddress() {
        return lastSenderIp;
    }

    @Override
    public int getLastSenderPort() {
        return lastSenderPort;
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        isBound = false;
        isConnected = false;
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public boolean isBound() {
        return isBound;
    }

    @Override
    public String getName() {
        return "UDP";
    }

    // Helper to get raw socket if needed (legacy support)
    public DatagramSocket getSocket() {
        return socket;
    }
}
