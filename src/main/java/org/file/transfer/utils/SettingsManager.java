package org.file.transfer.utils;

import java.io.File;
import java.util.prefs.Preferences;

public class SettingsManager {
    private static final SettingsManager INSTANCE = new SettingsManager();
    private final Preferences prefs;

    private static final String KEY_DOWNLOAD_DIR = "download_directory";
    private static final String KEY_ALLOW_EXTERNAL = "allow_external";

    private SettingsManager() {
        prefs = Preferences.userNodeForPackage(SettingsManager.class);
    }

    public static SettingsManager getInstance() {
        return INSTANCE;
    }

    public String getDownloadDirectory() {
        String defaultDir = System.getProperty("user.home") + File.separator + "Downloads" + File.separator
                + "FileTransfer";
        String dir = prefs.get(KEY_DOWNLOAD_DIR, defaultDir);

        File folder = new File(dir);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return dir;
    }

    public void setDownloadDirectory(String path) {
        prefs.put(KEY_DOWNLOAD_DIR, path);
    }

    public boolean isAllowExternal() {
        return prefs.getBoolean(KEY_ALLOW_EXTERNAL, true);
    }

    public void setAllowExternal(boolean allow) {
        prefs.putBoolean(KEY_ALLOW_EXTERNAL, allow);
    }
}
