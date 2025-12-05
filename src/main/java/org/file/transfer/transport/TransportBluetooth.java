package org.file.transfer.transport;

import java.io.IOException;

/**
 * Skeleton implementation for Bluetooth RFCOMM transport.
 * Requires BlueCove (Windows/macOS) or TinyB (Linux).
 * 
 * NOTE: JavaFX/Standard JDK does not include Bluetooth support.
 * This class serves as a structure for future native integration.
 */
public class TransportBluetooth implements Transport {

    private boolean connected = false;

    @Override
    public void connect(String address, int port) throws IOException {
        // TODO: Implement Bluetooth connect using BlueCove
        // Example:
        // String url = "btspp://" + address + ":" + port +
        // ";authenticate=false;encrypt=false;master=false";
        // StreamConnection conn = (StreamConnection) Connector.open(url);
        throw new IOException("Bluetooth not supported on this platform without native libraries.");
    }

    @Override
    public void bind(int port) throws IOException {
        // TODO: Implement Bluetooth listen
        // String url = "btspp://localhost:" + uuid + ";name=FileTransfer";
        // StreamConnectionNotifier notifier = (StreamConnectionNotifier)
        // Connector.open(url);
        throw new IOException("Bluetooth not supported on this platform without native libraries.");
    }

    @Override
    public void send(byte[] data) throws IOException {
        throw new IOException("Not connected");
    }

    @Override
    public void sendTo(byte[] data, String address, int port) throws IOException {
        throw new IOException("Bluetooth requires connection first.");
    }

    @Override
    public byte[] receive() throws IOException {
        throw new IOException("Bluetooth not implemented");
    }

    @Override
    public String getLastSenderAddress() {
        return "Unknown";
    }

    @Override
    public int getLastSenderPort() {
        return 0;
    }

    @Override
    public void close() throws IOException {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isBound() {
        return false;
    }

    @Override
    public String getName() {
        return "Bluetooth";
    }
}
