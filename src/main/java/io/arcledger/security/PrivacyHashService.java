package io.arcledger.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class PrivacyHashService {
    private final byte[] key;

    public PrivacyHashService(@Value("${arcledger.security.audit-hash-key:development-only-change-me}") String key) {
        if (key.length() < 24) throw new IllegalArgumentException("Audit hash key must contain at least 24 characters.");
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((value == null ? "unknown" : value)
                .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }
}
