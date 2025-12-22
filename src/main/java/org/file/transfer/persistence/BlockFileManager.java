package org.file.transfer.persistence;

import java.io.*;
import java.util.BitSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class BlockFileManager {
    private static BlockFileManager instance;

    public static class TransferState implements Serializable {
        public BitSet receivedBlocks;
        public int totalBlocks;
        public long lastModified;

        public TransferState(int total) {
            this.receivedBlocks = new BitSet(total);
            this.totalBlocks = total;
            this.lastModified = System.currentTimeMillis();
        }
    }

    private final Map<String, TransferState> activeTransfers = new ConcurrentHashMap<>();
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();
    private final Set<String> finishedFiles = ConcurrentHashMap.newKeySet();

    private BlockFileManager() {
    }

    public static synchronized BlockFileManager getInstance() {
        if (instance == null)
            instance = new BlockFileManager();
        return instance;
    }

    public Set<String> getActiveFiles() {
        return activeTransfers.keySet();
    }

    public TransferState getState(String fileName) {
        return activeTransfers.computeIfAbsent(fileName, this::loadFromDisk);
    }

    public BitSet getReceivedBlocks(String fileName) {
        return getState(fileName).receivedBlocks;
    }

    public void markBlockReceived(String fileName, int packetId, int totalPackets) {
        TransferState state = getState(fileName);
        if (state.totalBlocks == 0)
            state.totalBlocks = totalPackets;

        synchronized (state) {
            state.receivedBlocks.set(packetId);
            state.lastModified = System.currentTimeMillis();
        }
        saveToDisk(fileName, state);
    }

    public boolean isComplete(String fileName, int totalPackets) {
        TransferState state = getState(fileName);
        synchronized (state) {
            return state.receivedBlocks.cardinality() == totalPackets;
        }
    }

    public void cleanup(String fileName) {
        activeTransfers.remove(fileName);
        finishedFiles.add(fileName);

        // Retry deletion logic for Windows file locking issues
        File f = getTransferFile(fileName);
        int retries = 0;
        while (f.exists() && retries < 20) { // Try for ~2 seconds
            if (f.delete()) {
                System.out.println("[BlockFileManager] Deleted transfer file: " + f.getAbsolutePath());
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            retries++;
            System.gc(); // Hint to GC to release any lingering file handles
        }

        if (f.exists()) {
            System.err.println("[BlockFileManager] FAILED to delete transfer file: " + f.getAbsolutePath());
            // Last resort: delete on exit
            f.deleteOnExit();
        }
    }

    public boolean isFinished(String fileName) {
        return finishedFiles.contains(fileName);
    }

    public void reset(String fileName) {
        finishedFiles.remove(fileName);
        activeTransfers.remove(fileName);
    }

    private File getTransferFile(String fileName) {
        String dir = org.file.transfer.utils.SettingsManager.getInstance().getDownloadDirectory();
        return new File(dir, fileName + ".transfer");
    }

    private TransferState loadFromDisk(String fileName) {
        File meta = getTransferFile(fileName);
        if (!meta.exists())
            return new TransferState(0);

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(meta))) {
            Object obj = ois.readObject();
            if (obj instanceof BitSet) {
                TransferState s = new TransferState(0);
                s.receivedBlocks = (BitSet) obj;
                return s;
            } else if (obj instanceof TransferState) {
                return (TransferState) obj;
            }
        } catch (Exception e) {
            return new TransferState(0);
        }
        return new TransferState(0);
    }

    private void saveToDisk(String fileName, TransferState state) {
        Object lock = fileLocks.computeIfAbsent(fileName, k -> new Object());
        synchronized (lock) {
            // Fix Race Condition: Check if file is finished/cleaned up BEFORE writing
            if (finishedFiles.contains(fileName)) {
                return;
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getTransferFile(fileName)))) {
                synchronized (state) {
                    oos.writeObject(state);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
