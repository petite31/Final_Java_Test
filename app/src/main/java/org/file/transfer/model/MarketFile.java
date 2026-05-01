package org.file.transfer.model;

import javafx.beans.property.*;

public class MarketFile {
    private final IntegerProperty id;
    private final StringProperty fileName;
    private final LongProperty fileSize;
    private final StringProperty filePath;
    private final IntegerProperty sellerId;
    private final StringProperty sellerName; // Seller's username for UI display
    private final LongProperty price;
    private final StringProperty uploadDate;
    private final BooleanProperty isBought;

    public MarketFile(int id, String fileName, long fileSize, String filePath, int sellerId, String sellerName, long price, String uploadDate, boolean isBought) {
        this.id = new SimpleIntegerProperty(id);
        this.fileName = new SimpleStringProperty(fileName);
        this.fileSize = new SimpleLongProperty(fileSize);
        this.filePath = new SimpleStringProperty(filePath);
        this.sellerId = new SimpleIntegerProperty(sellerId);
        this.sellerName = new SimpleStringProperty(sellerName);
        this.price = new SimpleLongProperty(price);
        this.uploadDate = new SimpleStringProperty(uploadDate != null ? uploadDate : "");
        this.isBought = new SimpleBooleanProperty(isBought);
    }

    // --- Property Getters (Required for JavaFX TableView bindings) ---
    public IntegerProperty idProperty() { return id; }
    public StringProperty fileNameProperty() { return fileName; }
    public LongProperty fileSizeProperty() { return fileSize; }
    public StringProperty filePathProperty() { return filePath; }
    public IntegerProperty sellerIdProperty() { return sellerId; }
    public StringProperty sellerNameProperty() { return sellerName; }
    public LongProperty priceProperty() { return price; }
    public StringProperty uploadDateProperty() { return uploadDate; }
    public BooleanProperty isBoughtProperty() { return isBought; }

    // --- Standard Getters and Setters ---
    public int getId() { return id.get(); }
    public void setId(int id) { this.id.set(id); }

    public String getFileName() { return fileName.get(); }
    public void setFileName(String fileName) { this.fileName.set(fileName); }

    public long getFileSize() { return fileSize.get(); }
    public void setFileSize(long fileSize) { this.fileSize.set(fileSize); }

    public String getFilePath() { return filePath.get(); }
    public void setFilePath(String filePath) { this.filePath.set(filePath); }

    public int getSellerId() { return sellerId.get(); }
    public void setSellerId(int sellerId) { this.sellerId.set(sellerId); }

    public String getSellerName() { return sellerName.get(); }
    public void setSellerName(String sellerName) { this.sellerName.set(sellerName); }

    public long getPrice() { return price.get(); }
    public void setPrice(long price) { this.price.set(price); }

    public String getUploadDate() { return uploadDate.get(); }
    public void setUploadDate(String uploadDate) { this.uploadDate.set(uploadDate); }

    public boolean isBought() { return isBought.get(); }
    public void setBought(boolean isBought) { this.isBought.set(isBought); }
}