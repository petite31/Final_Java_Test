package org.file.transfer.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PeerInfo {
    private final StringProperty name;
    private final StringProperty ip;
    private final int port;
    private long lastSeen;

    public PeerInfo(String name, String ip, int port) {
        this.name = new SimpleStringProperty(name);
        this.ip = new SimpleStringProperty(ip);
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getIp() {
        return ip.get();
    }

    public StringProperty ipProperty() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public String getStatus() {
        return "Online"; // Simple status for now
    }
}
