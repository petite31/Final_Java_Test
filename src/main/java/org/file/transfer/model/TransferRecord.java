package org.file.transfer.model;

import javafx.beans.property.*;
import java.sql.Timestamp;

public class TransferRecord {
    private final IntegerProperty id;
    private final StringProperty sender;
    private final StringProperty receiver;
    private final StringProperty fileName;
    private final LongProperty size;
    private final ObjectProperty<Timestamp> timestamp;
    private final StringProperty status;

    public TransferRecord(int id, String sender, String receiver, String fileName, long size, String timestampStr,
            String status) {
        this.id = new SimpleIntegerProperty(id);
        this.sender = new SimpleStringProperty(sender);
        this.receiver = new SimpleStringProperty(receiver);
        this.fileName = new SimpleStringProperty(fileName);
        this.size = new SimpleLongProperty(size);
        this.status = new SimpleStringProperty(status);

        Timestamp ts;
        try {
            ts = Timestamp.valueOf(timestampStr);
        } catch (Exception e) {
            ts = new Timestamp(System.currentTimeMillis());
        }
        this.timestamp = new SimpleObjectProperty<>(ts);
        this.filePath = new SimpleStringProperty("");
    }

    public int getId() {
        return id.get();
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getSender() {
        return sender.get();
    }

    public StringProperty senderProperty() {
        return sender;
    }

    public String getReceiver() {
        return receiver.get();
    }

    public StringProperty receiverProperty() {
        return receiver;
    }

    public String getFileName() {
        return fileName.get();
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public long getSize() {
        return size.get();
    }

    public LongProperty sizeProperty() {
        return size;
    }

    public Timestamp getTimestamp() {
        return timestamp.get();
    }

    public ObjectProperty<Timestamp> timestampProperty() {
        return timestamp;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public StringProperty statusProperty() {
        return status;
    }

    private final StringProperty filePath;

    public String getFilePath() {
        if (filePath == null)
            return "";
        return filePath.get();
    }

    public void setFilePath(String path) {
        if (this.filePath != null)
            this.filePath.set(path);
    }
}
