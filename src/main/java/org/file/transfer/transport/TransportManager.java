package org.file.transfer.transport;

import java.net.SocketException;

public class TransportManager {
    private static TransportManager instance;
    private Transport currentTransport;

    private TransportManager() {
        // Hardcoded to UDP
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
        return currentTransport;
    }

    // Bluetooth/UDP toggles removed. Explicitly UDP.
    
    public Transport createTransport(String type) throws SocketException {
        // Ignore type, return UDP
        return new TransportUDP(); 
    }
}
