package org.file.transfer.service;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PasskeyManager {
    private static PasskeyManager instance;
    private final StringProperty currentPasskey = new SimpleStringProperty("DEFAULT");

    private PasskeyManager() {
    }

    public static synchronized PasskeyManager getInstance() {
        if (instance == null) {
            instance = new PasskeyManager();
        }
        return instance;
    }

    public void generateNewPasskey() {
        System.out.println("[Security] Passkey generation disabled.");
        javafx.application.Platform.runLater(() -> currentPasskey.set("DEFAULT"));
    }

    public StringProperty currentPasskeyProperty() {
        return currentPasskey;
    }

    public String getCurrentPasskey() {
        return currentPasskey.get();
    }

    public boolean isValid() {
        return true;
    }
}
