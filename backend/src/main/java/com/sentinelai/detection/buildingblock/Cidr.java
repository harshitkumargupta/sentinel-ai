package com.sentinelai.detection.buildingblock;

/** Minimal IPv4 CIDR helpers (non-IPv4 input is never "inside" a range). */
public final class Cidr {

    private Cidr() {
    }

    public static boolean isIpv4(String ip) {
        try {
            toLong(ip);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean contains(String cidr, String ip) {
        try {
            String[] parts = cidr.trim().split("/");
            int bits = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            if (bits < 0 || bits > 32) {
                return false;
            }
            long mask = bits == 0 ? 0 : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
            return (toLong(ip) & mask) == (toLong(parts[0]) & mask);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isValid(String cidr) {
        try {
            String[] parts = cidr.trim().split("/");
            toLong(parts[0]);
            int bits = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            return parts.length <= 2 && bits >= 0 && bits <= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static long toLong(String ip) {
        String[] o = ip.trim().split("\\.");
        if (o.length != 4) {
            throw new IllegalArgumentException("not IPv4: " + ip);
        }
        long v = 0;
        for (String part : o) {
            int b = Integer.parseInt(part);
            if (b < 0 || b > 255) {
                throw new IllegalArgumentException("bad octet");
            }
            v = (v << 8) | b;
        }
        return v;
    }
}
