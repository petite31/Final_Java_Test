package org.file.transfer.network;

import org.file.transfer.controller.SendFilesController;
import org.file.transfer.service.TransferService;

import java.io.File;
import java.net.SocketException;
import java.util.List;
import java.util.Random;

public class TransferManager {
    private FileSender fileSender;
    private FileReceiver fileReceiver;
    private String myPasskey;

    public TransferManager() {
        this.myPasskey = String.format("%06d", new Random().nextInt(1000000));
        try {
            this.fileSender = new FileSender(this);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public int startListening() {
        try {
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
            boolean auth = fileSender.performHandshake(ip, 6969, passkey);
            if (auth) {
                fileSender.sendFileOrFolder(file, ip, 6969, callback);
                if (callback != null)
                    callback.onTransferComplete();
            } else {
                System.out.println("Auth failed");
            }
        }).start();
    }

    public void authenticateAndSendFiles(List<File> files, String ip, String passkey, SendFilesController callback) {
        new Thread(() -> {
            boolean auth = fileSender.performHandshake(ip, 6969, passkey);
            if (auth) {
                fileSender.sendFiles(files, ip, 6969, callback);
            } else {
                System.out.println("Auth failed");
            }
        }).start();
    }

    public String getMyPasskey() {
        return myPasskey;
    }

    public String regeneratePasskey() {
        this.myPasskey = String.format("%06d", new Random().nextInt(1000000));
        if (this.fileReceiver != null) {
            this.fileReceiver.setPasskey(this.myPasskey);
        }
        System.out.println("[TransferManager] Regenerated passkey: " + this.myPasskey);
        return this.myPasskey;
    }

    public TransferService getService() {
        return TransferService.getInstance();
    }
}