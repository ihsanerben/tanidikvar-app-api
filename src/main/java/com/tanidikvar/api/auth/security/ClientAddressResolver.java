package com.tanidikvar.api.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves a client address without trusting forwarding headers from arbitrary callers. */
@Component
public class ClientAddressResolver {
    private final List<Network> trustedProxies;

    public ClientAddressResolver(@Value("${app.rate-limit.trusted-proxies:}") String configuredNetworks) {
        trustedProxies = Arrays.stream(configuredNetworks.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Network::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        InetAddress peer = address(request.getRemoteAddr());
        if (peer == null || !trusted(peer)) return request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return peer.getHostAddress();

        List<InetAddress> chain = new ArrayList<>();
        for (String value : forwarded.split(",")) {
            InetAddress candidate = address(value.trim());
            if (candidate == null) return peer.getHostAddress();
            chain.add(candidate);
        }
        chain.add(peer);
        for (int index = chain.size() - 1; index >= 0; index--) {
            InetAddress candidate = chain.get(index);
            if (!trusted(candidate)) return candidate.getHostAddress();
        }
        return chain.getFirst().getHostAddress();
    }

    private boolean trusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(network -> network.contains(address));
    }

    private static InetAddress address(String value) {
        try { return InetAddress.getByName(value); }
        catch (UnknownHostException exception) { return null; }
    }

    private record Network(byte[] address, int prefixLength) {
        static Network parse(String value) {
            String[] parts = value.split("/", 2);
            InetAddress parsed;
            try { parsed = InetAddress.getByName(parts[0]); }
            catch (UnknownHostException exception) { throw new IllegalArgumentException("Invalid trusted proxy: " + value, exception); }
            int bits = parsed.getAddress().length * 8;
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bits;
            if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("Invalid trusted proxy prefix: " + value);
            return new Network(parsed.getAddress(), prefix);
        }

        boolean contains(InetAddress candidate) {
            byte[] other = candidate.getAddress();
            if (other.length != address.length) return false;
            int wholeBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < wholeBytes; index++) if (address[index] != other[index]) return false;
            if (remainingBits == 0) return true;
            int mask = 0xff << (8 - remainingBits);
            return (address[wholeBytes] & mask) == (other[wholeBytes] & mask);
        }
    }
}
