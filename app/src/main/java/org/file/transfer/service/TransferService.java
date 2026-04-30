package org.file.transfer.service;

import org.file.transfer.network.TransferManager;

public class TransferService {
    private static final TransferService INSTANCE = new TransferService();

    private TransferManager transferManager;
    private String myPasskey;
    private int listeningPort;

    private TransferService() {
    }

    public static TransferService getInstance() {
        return INSTANCE;
    }

    public void initialize(TransferManager manager) {
        this.transferManager = manager;
        this.myPasskey = manager.getMyPasskey();
        try {
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
