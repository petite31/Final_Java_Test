package org.file.transfer.network;

import org.file.transfer.controller.SendFilesController;
import org.file.transfer.service.TransferService;

import java.io.File;
import java.net.SocketException;
import java.util.Random;

public class TransferManager {
    private FileSender fileSender;
    private FileReceiver fileReceiver;
    private String myPasskey;

    public TransferManager() {
        // Generate a random 6-digit passkey
        this.myPasskey = String.format("%06d", new Random().nextInt(1000000));
        try {
            this.fileSender = new FileSender(this);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public int startListening() {
        try {
            // Try port 6969, if busy try 0 (random)
            int port = 6969;
            try {
                this.fileReceiver = new FileReceiver(this, port, myPasskey);
            } catch (SocketException e) {
                this.fileReceiver = new FileReceiver(this, 0, myPasskey);
            }
            return fileReceiver.getPort();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public void authenticateAndSend(File file, String ip, String passkey, SendFilesController callback) {
        new Thread(() -> {
            boolean auth = fileSender.performHandshake(ip, 6969, passkey); // Default port 6969
            if (auth) {
                fileSender.sendFileOrFolder(file, ip, 6969, callback);
            } else {
                // Handle auth failure
                System.out.println("Auth failed");
            }
        }).start();
    }

    public String getMyPasskey() {
        return myPasskey;
    }

    // Helper to get service instance if needed by children
    public TransferService getService() {
        return TransferService.getInstance();
    }

    // Helper for Receiver to notify UI (if we had a ReceiverController callback)
    // For now, Receiver updates via polling or we can add an event bus later.
    // But since we are refactoring, let's keep it simple: Receiver writes file, UI
    // refreshes on user action or polling.
    // The user requirement says "Receiver Files Page... Display all received
    // files".
    // We can just rely on the file system watcher or manual refresh for now to keep
    // it simple as per "clean code".
}