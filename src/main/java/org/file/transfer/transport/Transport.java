package org.file.transfer.transport;

import java.io.IOException;

public interface Transport {
    /**
     * Connect to a specific target (Client mode) or prepare for sending.
     * For UDP, this sets the default destination.
     * For Bluetooth, this opens a connection.
     */
    void connect(String address, int port) throws IOException;

    /**
     * Bind to a specific port/service (Server mode).
     * For UDP, this binds the DatagramSocket.
     * For Bluetooth, this makes the device discoverable/listen.
     */
    void bind(int port) throws IOException;

    /**
     * Send data. Must be connected or have a target set.
     */
    void send(byte[] data) throws IOException;

    /**
     * Send data to a specific address/port (Connectionless style).
     * Supported by UDP. Bluetooth might throw UnsupportedOperationException if
     * connected.
     */
    void sendTo(byte[] data, String address, int port) throws IOException;

    /**
     * receive data. Blocks until data is available.
     * Returns the raw data.
     */
    byte[] receive() throws IOException;

    /**
     * Get the sender info of the last received packet.
     * Useful for UDP to know who sent the data.
     */
    String getLastSenderAddress();

    int getLastSenderPort();

    void close() throws IOException;

    boolean isConnected();

    boolean isBound();

    String getName(); // "UDP", "Bluetooth", etc.
}
