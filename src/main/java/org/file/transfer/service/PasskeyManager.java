package org.file.transfer.service;

import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PasskeyManager {
    private static PasskeyManager instance;
    private final StringProperty currentPasskey = new SimpleStringProperty("");
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private long passkeyExpiryTime = 0;

    // Configurable timeout
    public static final long PASSKEY_TIMEOUT_SECONDS = 45;

    private PasskeyManager() {
    }

    public static synchronized PasskeyManager getInstance() {
        if (instance == null) {
            instance = new PasskeyManager();
        }
        return instance;
    }

    public void generateNewPasskey() {
        // SecureRandom for 6 digit alphanumeric
        SecureRandom random = new SecureRandom();
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        String key = sb.toString();

        System.out.println("[Security] Generated Passkey: " + key);

        javafx.application.Platform.runLater(() -> currentPasskey.set(key));
        passkeyExpiryTime = System.currentTimeMillis() + (PASSKEY_TIMEOUT_SECONDS * 1000);

        // Schedule auto-expire
        scheduler.schedule(() -> {
            if (System.currentTimeMillis() >= passkeyExpiryTime) {
                ExpirePasskey();
            }
        }, PASSKEY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void ExpirePasskey() {
        System.out.println("[Security] Passkey expired");
        javafx.application.Platform.runLater(() -> currentPasskey.set("EXPIRED"));
    }

    public StringProperty currentPasskeyProperty() {
        return currentPasskey;
    }

    public String getCurrentPasskey() {
        return currentPasskey.get();
    }

    public boolean isValid() {
        return !currentPasskey.get().isEmpty() && !"EXPIRED".equals(currentPasskey.get())
                && System.currentTimeMillis() < passkeyExpiryTime;
    }
}
