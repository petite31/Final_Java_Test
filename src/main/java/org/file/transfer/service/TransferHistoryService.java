package org.file.transfer.service;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TransferHistoryService {
    private static final TransferHistoryService INSTANCE = new TransferHistoryService();

    private final Map<String, Set<String>> history = Collections.synchronizedMap(new HashMap<>());

    private TransferHistoryService() {
    }

    public static TransferHistoryService getInstance() {
        return INSTANCE;
    }

    public boolean isDuplicate(String ip, File file) {
        if (!history.containsKey(ip)) {
            return false;
        }
        String id = getFileIdentifier(file);
        return history.get(ip).contains(id);
    }

    public void markAsSent(String ip, File file) {
        history.computeIfAbsent(ip, k -> Collections.synchronizedSet(new HashSet<>())).add(getFileIdentifier(file));
    }

    private String getFileIdentifier(File file) {
        return file.getName() + "_" + file.length() + "_" + file.lastModified();
    }

    public void clearHistory() {
        history.clear();
    }
}
