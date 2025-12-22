package org.file.transfer.transport;

import java.net.SocketException;

public class TransportManager {
    private static TransportManager instance;
    private Transport currentTransport;

    private TransportManager() {
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

    public Transport createTransport(String type) throws SocketException {
        return new TransportUDP();
    }
}
