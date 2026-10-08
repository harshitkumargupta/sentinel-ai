package com.sentinelai.playbook;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.event.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Safety policy for action targets: which IPs/users are protected (never auto-blocked), and the
 * blast radius of acting on a target. Protected = an admin user, a loopback/internal-network IP, or
 * an allow-listed IP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetPolicy {

    public record Blast(int users, int admins) {
    }

    private final PlaybookProperties props;
    private final UserRepository userRepository;
    private final SecurityEventRepository eventRepository;

    public boolean isProtectedIp(String ip) {
        if (ip == null) {
            return false;
        }
        if (props.getProtectedIps().contains(ip)) {
            return true;
        }
        for (String cidr : props.getProtectedCidrs()) {
            if (ipInCidr(ip, cidr)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdminUser(String username) {
        return username != null && userRepository.findByUsername(username)
                .map(u -> u.getRole() == Role.ADMIN).orElse(false);
    }

    /** Blast radius for blocking an IP: distinct users seen from it, and how many are admins. */
    public Blast blastForIp(Long orgId, String ip) {
        List<String> users = eventRepository.distinctUsernamesByOrgAndIp(orgId, ip);
        int admins = (int) users.stream().filter(this::isAdminUser).count();
        return new Blast(users.size(), admins);
    }

    public boolean isProtectedHost(String host) {
        return host != null && props.getProtectedHosts().stream().anyMatch(h -> h.equalsIgnoreCase(host));
    }

    /** Blast radius for isolating a host: distinct users seen on it, and how many are admins. */
    public Blast blastForHost(Long orgId, String host) {
        List<String> users = eventRepository.distinctUsernamesByOrgAndEntityKey(orgId, "host:" + host);
        int admins = (int) users.stream().filter(this::isAdminUser).count();
        return new Blast(users.size(), admins);
    }

    /** Blast radius for a user action: one user, possibly an admin. */
    public Blast blastForUser(String username) {
        return new Blast(1, isAdminUser(username) ? 1 : 0);
    }

    /** Minimal IPv4 CIDR containment check. Non-IPv4 inputs return false. */
    static boolean ipInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            long net = toLong(parts[0]);
            int bits = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            long mask = bits == 0 ? 0 : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
            return (toLong(ip) & mask) == (net & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private static long toLong(String ip) {
        String[] o = ip.split("\\.");
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
