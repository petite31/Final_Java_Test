package org.file.transfer.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class IP_Port_Managment {
    public static String getBestLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();

                // Skip loopback and down interfaces
                if (nif.isLoopback() || !nif.isUp()) {
                    continue;
                }

                String displayName = nif.getDisplayName().toLowerCase();
                String name = nif.getName().toLowerCase();

                // Advanced filtering for Virtual Adapters
                if (displayName.contains("virtual") || displayName.contains("vmware") ||
                        displayName.contains("box") || displayName.contains("docker") ||
                        displayName.contains("wsl") || // WSL
                        name.contains("veth") || name.contains("docker") || name.contains("br-")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Return the first valid IPv4 that is not loopback
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // Additional check to prefer standard private networks
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback
        }

        // Ultimate fallback
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Unknown IP";
        }
    }
}
