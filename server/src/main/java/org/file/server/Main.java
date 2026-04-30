package org.file.server;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting File Transfer Hybrid P2P Server...");

        // Ensure Database connection is valid
        try {
            DatabaseManager.getConnection().close();
            System.out.println("Database connection OK.");
        } catch (Exception e) {
            System.err.println("Could not connect to Database. Please check your config in DatabaseManager.java");
            e.printStackTrace();
            System.exit(1);
        }

        // Start Signaling Server (TCP)
        Thread signalingThread = new Thread(() -> {
            new SignalingServer().start();
        });
        signalingThread.setName("SignalingServer-Thread");
        signalingThread.start();

        // Start UDP Tracker Server
        Thread trackerThread = new Thread(() -> {
            new UdpTrackerServer().start();
        });
        trackerThread.setName("UdpTracker-Thread");
        trackerThread.start();

        // Start Relay Fallback Server
        Thread relayThread = new Thread(() -> {
            new RelayServer().start();
        });
        relayThread.setName("RelayServer-Thread");
        relayThread.start();

        Thread marketServer = new Thread(() -> {
            new MarketServer().start();
        });

        System.out.println("All services started successfully.");
    }
}
