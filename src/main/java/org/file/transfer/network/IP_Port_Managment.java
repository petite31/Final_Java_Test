package org.file.transfer.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;

public class IP_Port_Managment {
    public static String getBestLocalIp() {
        try {
            var en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                var nif = en.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                var addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("127.0.0.1")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Không xác định";
        }
    }
}
