package org.file.transfer.transport;

import java.net.SocketException;

public class TransportManager {
    private static TransportManager instance;
    private Transport currentTransport;

    // Settings to check which transports are enabled
    private boolean useUdp = true;
    private boolean useBluetooth = false;

    private TransportManager() {
        // Default to UDP
        try {
            this.currentTransport = new TransportUDP();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public static synchronized TransportManager getInstance() {
        if (instance == null) {
            instance = new TransportManager();
        }
        return instance;
    }

    public Transport getTransport() {
        // Return active transport.
        // Ideally we select based on target capability, but for now we follow global
        // setting or default.
        return currentTransport;
    }

    public void setUseBluetooth(boolean enable) {
        this.useBluetooth = enable;
    }

    public boolean isBluetoothEnabled() {
        return useBluetooth;
    }

    public void setUseUdp(boolean enable) {
        this.useUdp = enable;
    }

    public boolean isUdpEnabled() {
        return useUdp;
    }

    public Transport createTransport(String type) throws SocketException {
        if ("Bluetooth".equalsIgnoreCase(type)) {
            return new TransportBluetooth();
        }
        return new TransportUDP(); // Default
    }
}
