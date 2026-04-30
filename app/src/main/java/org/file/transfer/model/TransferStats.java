package org.file.transfer.model;

public class TransferStats {
    private long startTime;
    private long totalBytes;
    private long transferredBytes;

    public TransferStats(long totalBytes) {
        this.totalBytes = totalBytes;
        this.startTime = System.currentTimeMillis();
    }

    public void update(long newTransferredBytes) {
        this.transferredBytes = newTransferredBytes;
    }

    public double getSpeedMBs() {
        long now = System.currentTimeMillis();
        long diff = now - startTime;
        if (diff == 0)
            return 0;
        return (transferredBytes / 1024.0 / 1024.0) / (diff / 1000.0);
    }

    public double getCurrentSpeedMBs() {
        return getSpeedMBs();
    }

    public long getEstimatedTimeRemainingSeconds() {
        double speed = getSpeedMBs();
        if (speed <= 0)
            return 0;
        long remainingBytes = totalBytes - transferredBytes;
        return (long) ((remainingBytes / 1024.0 / 1024.0) / speed);
    }

    public double getProgress() {
        if (totalBytes == 0)
            return 0;
        return (double) transferredBytes / totalBytes;
    }
}
