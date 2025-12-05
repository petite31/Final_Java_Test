package org.file.transfer.model;

public class TransferStats {
    private long startTime;
    private long lastUpdate;
    private long totalBytes;
    private long transferredBytes;

    public TransferStats(long totalBytes) {
        this.totalBytes = totalBytes;
        this.startTime = System.currentTimeMillis();
        this.lastUpdate = startTime;
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
        // This calculates average speed. For current speed we need sliding window.
        // But simple average is often stable enough for simple apps.
        // Let's implement a simple weighted average if needed, or just return average.
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
