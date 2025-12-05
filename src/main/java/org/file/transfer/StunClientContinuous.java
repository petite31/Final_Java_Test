package org.file.transfer;

import java.net.*;
import java.security.SecureRandom;
import java.util.Arrays;

public class StunClientContinuous {

    private static final int BINDING_REQUEST = 0x0001;
    private static final int MAPPED_ADDRESS = 0x0001;
    private static final int XOR_MAPPED_ADDRESS = 0x0020;
    private static final int MAGIC_COOKIE = 0x2112A442;

    public static void main(String[] args) throws Exception {
        String stunHost = "stun.l.google.com";
        int stunPort = 19302;
        int localPort = 1511;
        int intervalMs = 100; // gửi liên tục mỗi 5 giây

        if (args.length >= 1) stunHost = args[0];
        if (args.length >= 2) stunPort = Integer.parseInt(args[1]);
        if (args.length >= 3) localPort = Integer.parseInt(args[2]);
        if (args.length >= 4) intervalMs = Integer.parseInt(args[3]);

        InetAddress stunAddr = InetAddress.getByName(stunHost);
        DatagramSocket socket = new DatagramSocket(localPort);
        socket.setSoTimeout(3000);

        System.out.println("Local address: " + InetAddress.getLocalHost().getHostAddress() + ":" + localPort);
        System.out.println("Using STUN server: " + stunHost + ":" + stunPort);
        System.out.println("Sending STUN requests every " + intervalMs + "ms");

        while (true) {
            try {
                byte[] txId = new byte[12];
                new SecureRandom().nextBytes(txId);
                byte[] request = buildBindingRequest(txId);
                DatagramPacket p = new DatagramPacket(request, request.length, stunAddr, stunPort);
                socket.send(p);

                byte[] buf = new byte[576];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                socket.receive(resp);

                byte[] respBytes = Arrays.copyOfRange(resp.getData(), 0, resp.getLength());
                StunResult result = parseStunResponse(respBytes, txId);

                if (result != null) {
                    System.out.println("Public IP: " + result.address + " | Public port: " + result.port
                            + " | " + result.family);

                    if (result.port == localPort) {
                        System.out.println("Port preserved by NAT.");
                    } else {
                        System.out.println("Port remapped by NAT.");
                    }
                } else {
                    System.out.println("No mapped address found in STUN response.");
                }

            } catch (SocketTimeoutException e) {
                System.err.println("No response from STUN server (timeout).");
            } catch (Exception e) {
                e.printStackTrace();
            }

            Thread.sleep(intervalMs);
        }
    }

    private static byte[] buildBindingRequest(byte[] txId) {
        byte[] buf = new byte[20];
        buf[0] = (byte) ((BINDING_REQUEST >> 8) & 0xFF);
        buf[1] = (byte) (BINDING_REQUEST & 0xFF);
        buf[2] = 0;
        buf[3] = 0;
        buf[4] = (byte) ((MAGIC_COOKIE >> 24) & 0xFF);
        buf[5] = (byte) ((MAGIC_COOKIE >> 16) & 0xFF);
        buf[6] = (byte) ((MAGIC_COOKIE >> 8) & 0xFF);
        buf[7] = (byte) (MAGIC_COOKIE & 0xFF);
        System.arraycopy(txId, 0, buf, 8, 12);
        return buf;
    }

    private static StunResult parseStunResponse(byte[] data, byte[] txId) {
        if (data.length < 20) return null;
        int msgLen = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int cookie = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        byte[] respTx = Arrays.copyOfRange(data, 8, 20);

        int offset = 20;
        int end = Math.min(20 + msgLen, data.length);
        StunResult result = null;

        while (offset + 4 <= end) {
            int attrType = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            int attrLen = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            int attrValueStart = offset + 4;
            if (attrValueStart + attrLen > data.length) break;

            if (attrType == XOR_MAPPED_ADDRESS) {
                result = parseXorMappedAddress(data, attrValueStart, attrLen, cookie, respTx);
                break;
            } else if (attrType == MAPPED_ADDRESS) {
                result = parseMappedAddress(data, attrValueStart, attrLen);
                break;
            }

            offset = attrValueStart + ((attrLen + 3) & ~3);
        }
        return result;
    }

    private static StunResult parseMappedAddress(byte[] data, int off, int len) {
        if (len < 4) return null;
        int family = data[off + 1] & 0xFF;
        int port = ((data[off + 2] & 0xFF) << 8) | (data[off + 3] & 0xFF);

        try {
            if (family == 0x01 && len >= 8) {
                InetAddress a = InetAddress.getByAddress(Arrays.copyOfRange(data, off + 4, off + 8));
                return new StunResult(a.getHostAddress(), port, "IPv4 (MAPPED-ADDRESS)");
            } else if (family == 0x02 && len >= 20) {
                InetAddress a = InetAddress.getByAddress(Arrays.copyOfRange(data, off + 4, off + 20));
                return new StunResult(a.getHostAddress(), port, "IPv6 (MAPPED-ADDRESS)");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static StunResult parseXorMappedAddress(byte[] data, int off, int len, int cookie, byte[] txId) {
        if (len < 4) return null;
        int family = data[off + 1] & 0xFF;
        int xport = ((data[off + 2] & 0xFF) << 8) | (data[off + 3] & 0xFF);
        int port = xport ^ ((cookie >> 16) & 0xFFFF);

        try {
            if (family == 0x01 && len >= 8) {
                byte[] xaddr = Arrays.copyOfRange(data, off + 4, off + 8);
                byte[] addr = new byte[4];
                addr[0] = (byte) (xaddr[0] ^ ((cookie >> 24) & 0xFF));
                addr[1] = (byte) (xaddr[1] ^ ((cookie >> 16) & 0xFF));
                addr[2] = (byte) (xaddr[2] ^ ((cookie >> 8) & 0xFF));
                addr[3] = (byte) (xaddr[3] ^ (cookie & 0xFF));
                return new StunResult(InetAddress.getByAddress(addr).getHostAddress(), port, "IPv4 (XOR-MAPPED-ADDRESS)");
            } else if (family == 0x02 && len >= 20) {
                byte[] xaddr = Arrays.copyOfRange(data, off + 4, off + 20);
                byte[] addr = new byte[16];
                byte[] key = new byte[16];
                key[0] = (byte) ((cookie >> 24) & 0xFF);
                key[1] = (byte) ((cookie >> 16) & 0xFF);
                key[2] = (byte) ((cookie >> 8) & 0xFF);
                key[3] = (byte) (cookie & 0xFF);
                System.arraycopy(txId, 0, key, 4, 12);
                for (int i = 0; i < 16; i++) {
                    addr[i] = (byte) (xaddr[i] ^ key[i]);
                }
                return new StunResult(InetAddress.getByAddress(addr).getHostAddress(), port, "IPv6 (XOR-MAPPED-ADDRESS)");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static class StunResult {
        String address;
        int port;
        String family;
        StunResult(String a, int p, String fam) { address = a; port = p; family = fam; }
    }
}
