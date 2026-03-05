package org.file.transfer.model;

import java.io.Serializable;

public record FilePacket(
                int packetId,
                byte[] data,
                String fileName,
                long fileSize,
                int totalPackets,
                boolean isLast) implements Serializable {
}