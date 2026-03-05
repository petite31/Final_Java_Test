package org.file.transfer.model;

import javafx.beans.property.*;

public class PeerInfo {
    private final StringProperty name;
    private final StringProperty ip;
    private final IntegerProperty port;
    private final StringProperty mechanism;
    private final StringProperty passkey;
    private long lastSeen;

    public PeerInfo(String name, String ip, int port) {
        this.name = new SimpleStringProperty(name);
        this.ip = new SimpleStringProperty(ip);
        this.port = new SimpleIntegerProperty(port);
        this.mechanism = new SimpleStringProperty("UDP");
        this.passkey = new SimpleStringProperty("");
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
        return port.get();
    }

    public IntegerProperty portProperty() {
        return port;
    }

    public String getMechanism() {
        return mechanism.get();
    }

    public StringProperty mechanismProperty() {
        return mechanism;
    }

    public void setMechanism(String mech) {
        this.mechanism.set(mech);
    }

    public String getPasskey() {
        return passkey.get();
    }

    public void setPasskey(String key) {
        this.passkey.set(key);
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public String getStatus() {
        return (System.currentTimeMillis() - lastSeen < 5000) ? "Online" : "Away";
    }
}
