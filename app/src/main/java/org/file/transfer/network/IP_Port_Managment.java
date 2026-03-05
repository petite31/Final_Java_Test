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

                if (nif.isLoopback() || !nif.isUp()) {
                    continue;
                }

                String displayName = nif.getDisplayName().toLowerCase();
                String name = nif.getName().toLowerCase();

                if (displayName.contains("virtual") || displayName.contains("vmware") ||
                        displayName.contains("box") || displayName.contains("docker") ||
                        displayName.contains("wsl") ||
                        name.contains("veth") || name.contains("docker") || name.contains("br-")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Unknown IP";
        }
    }
}
