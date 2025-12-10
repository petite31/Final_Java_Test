package org.file.transfer.service;

import org.file.transfer.network.FileReceiver;
import org.file.transfer.network.FileSender;
import org.file.transfer.network.TransferManager;

public class TransferService {
    private static final TransferService INSTANCE = new TransferService();

    private TransferManager transferManager;
    private String myPasskey;
    private int listeningPort;

    private TransferService() {
        // Initialize with a dummy controller or refactor TransferManager to not need
        // one immediately
        // For now, we'll keep TransferManager but decouple it slightly or wrap it.
        // Ideally, TransferManager should be the Service itself, but we'll wrap it for
        // the new architecture.
    }

    public static TransferService getInstance() {
        return INSTANCE;
    }

    public void initialize(TransferManager manager) {
        this.transferManager = manager;
        this.myPasskey = manager.getMyPasskey();
        try {
            // We might need to re-initialize receiver if port changes, but for now assume
            // static start
            this.listeningPort = manager.startListening();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TransferManager getTransferManager() {
        return transferManager;
    }

    public String getMyPasskey() {
        return myPasskey;
    }

    public void regeneratePasskey() {
        if (transferManager != null) {
            this.myPasskey = transferManager.regeneratePasskey();
        }
    }

    public int getListeningPort() {
        return listeningPort;
    }
}
