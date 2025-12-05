package org.file.transfer.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FolderManifest implements Serializable {
    private String rootFolderName;
    private List<ManifestItem> items = new ArrayList<>();
    private long totalSize;

    public FolderManifest(String rootFolderName) {
        this.rootFolderName = rootFolderName;
    }

    public void addItem(String relativePath, long size, long lastModified) {
        items.add(new ManifestItem(relativePath, size, lastModified));
        totalSize += size;
    }

    public List<ManifestItem> getItems() {
        return items;
    }

    public String getRootFolderName() {
        return rootFolderName;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public record ManifestItem(String relativePath, long size, long lastModified) implements Serializable {
    }
}
