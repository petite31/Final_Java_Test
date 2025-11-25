package org.file.transfer.utils;

public class Logger {
    public static void info(String msg) {
        System.out.println("[INFO] " + msg);
    }

    public static void error(String msg) {
        System.err.println("[ERROR] " + msg);
    }

    public static void debug(String msg) {
        System.out.println("[DEBUG] " + msg);
    }
}
