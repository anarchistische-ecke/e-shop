package com.example.api.payment;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Component
public class YooKassaWebhookVerifier {
    private final String webhookSecret;
    private final List<CidrBlock> allowlist;

    public YooKassaWebhookVerifier(
            @Value("${yookassa.webhook-secret:}") String webhookSecret,
            @Value("${yookassa.webhook-allowed-ips:}") String allowedIps
    ) {
        this.webhookSecret = webhookSecret;
        this.allowlist = parseAllowlist(allowedIps);
    }

    public boolean isValid(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (StringUtils.hasText(webhookSecret)) {
            String header = request.getHeader("X-Webhook-Secret");
            if (!webhookSecret.equals(header)) {
                return false;
            }
        }
        if (!allowlist.isEmpty()) {
            String ip = resolveClientIp(request);
            if (!StringUtils.hasText(ip)) {
                return false;
            }
            if (!isAllowed(ip)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAllowed(String ip) {
        InetAddress address;
        try {
            address = InetAddress.getByName(ip);
        } catch (UnknownHostException ex) {
            return false;
        }
        for (CidrBlock block : allowlist) {
            if (block.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private List<CidrBlock> parseAllowlist(String allowedIps) {
        List<CidrBlock> blocks = new ArrayList<>();
        if (!StringUtils.hasText(allowedIps)) {
            return blocks;
        }
        String[] parts = allowedIps.split(",");
        for (String raw : parts) {
            String entry = raw.trim();
            if (!StringUtils.hasText(entry)) {
                continue;
            }
            CidrBlock block = CidrBlock.parse(entry);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private static final class CidrBlock {
        private final InetAddress networkAddress;
        private final int prefixLength;

        private CidrBlock(InetAddress networkAddress, int prefixLength) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
        }

        static CidrBlock parse(String entry) {
            String value = entry.trim();
            String[] parts = value.split("/", 2);
            try {
                InetAddress address = InetAddress.getByName(parts[0]);
                int prefix;
                if (parts.length == 2) {
                    prefix = Integer.parseInt(parts[1]);
                } else {
                    prefix = address.getAddress().length * 8;
                }
                if (prefix < 0 || prefix > address.getAddress().length * 8) {
                    return null;
                }
                return new CidrBlock(address, prefix);
            } catch (Exception ex) {
                return null;
            }
        }

        boolean matches(InetAddress address) {
            byte[] network = networkAddress.getAddress();
            byte[] target = address.getAddress();
            if (network.length != target.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainder = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (network[i] != target[i]) {
                    return false;
                }
            }
            if (remainder == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainder);
            return (network[fullBytes] & mask) == (target[fullBytes] & mask);
        }
    }
}
