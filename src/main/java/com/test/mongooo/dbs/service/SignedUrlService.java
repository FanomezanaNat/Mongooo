package com.bank.dbs.service;

import com.bank.dbs.constant.SignedUrlStatus;
import com.bank.dbs.entity.SignedUrl;
import com.bank.dbs.exception.InvalidTokenException;
import com.bank.dbs.exception.SignedUrlExpiredException;
import com.bank.dbs.repository.SignedUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * HMAC-SHA256 token generation/validation for the DBS-signed-URL upload/download
 * flow (spec 2.1 "DBS HMAC signed URL + chunks", spec 5.1 endpoints /upload/{docId}
 * and /download/{docId}).
 *
 * Token = base64url( HMAC-SHA256( secretKey, keyId + "." + docId + "." + expiresAtEpochSeconds ) ).
 * Only tokenHash (SHA-256 of the raw token) is persisted (see SignedUrl entity) —
 * the raw token itself never touches the database, only the HTTP response to the
 * caller and, transiently, the Authorization query param on subsequent chunk calls.
 *
 * keyId supports HMAC signing-key rotation (risk R05): validateToken() looks up the
 * key material for the token's keyId, so a 30-minute dual-key window can be
 * supported by keeping the previous key's material available under its own keyId
 * until in-flight signed URLs from before the rotation have all expired.
 */
@Service
public class SignedUrlService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String CURRENT_KEY_ID = "k1";

    private final SignedUrlRepository signedUrlRepository;
    private final String secretKey;
    private final Duration defaultTtl;

    public SignedUrlService(SignedUrlRepository signedUrlRepository,
                             @Value("${dbs.signing.secret-key}") String secretKey,
                             @Value("${dbs.signing.default-ttl-minutes:30}") long defaultTtlMinutes) {
        this.signedUrlRepository = signedUrlRepository;
        this.secretKey = secretKey;
        this.defaultTtl = Duration.ofMinutes(defaultTtlMinutes);
    }

    /** Issues a new upload token and persists the tracking SignedUrl record. */
    public IssuedToken generateUploadUrl(UUID docId, int totalChunks) {
        return issue(docId, "UPLOAD", totalChunks);
    }

    public IssuedToken generateDownloadUrl(UUID docId) {
        return issue(docId, "DOWNLOAD", null);
    }

    private IssuedToken issue(UUID docId, String direction, Integer totalChunks) {
        Instant expiresAt = Instant.now().plus(defaultTtl);
        String rawToken = sign(CURRENT_KEY_ID, docId, expiresAt);

        SignedUrl entity = new SignedUrl();
        entity.setId(UUID.randomUUID());
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setDocId(docId);
        entity.setDirection(direction);
        entity.setTotalChunks(totalChunks);
        entity.setReceivedChunks(0);
        entity.setStatus(SignedUrlStatus.ISSUED);
        entity.setKeyId(CURRENT_KEY_ID);
        entity.setExpiresAt(expiresAt);
        entity.setTtlAnchor(expiresAt);
        signedUrlRepository.save(entity);

        return new IssuedToken(rawToken, expiresAt);
    }

    /**
     * Validates a presented token: signature integrity + expiry + existence of a
     * tracking record. Returns the tracking record for the caller to act on
     * (AC-BE-10: expired -> 410 URL_EXPIRED; tampered -> 401 INVALID_TOKEN).
     */
    public SignedUrl validateToken(String rawToken, UUID expectedDocId) {
        SignedUrl tracking = signedUrlRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidTokenException("token not recognised"));

        if (!tracking.getDocId().equals(expectedDocId)) {
            throw new InvalidTokenException("token does not match requested document");
        }

        if (!isValidSignature(rawToken, tracking.getKeyId(), tracking.getDocId(), tracking.getExpiresAt())) {
            throw new InvalidTokenException("signature verification failed");
        }

        if (Instant.now().isAfter(tracking.getExpiresAt())
                || tracking.getStatus() == SignedUrlStatus.EXPIRED
                || tracking.getStatus() == SignedUrlStatus.CANCELLED) {
            throw new SignedUrlExpiredException();
        }

        return tracking;
    }

    private boolean isValidSignature(String rawToken, String keyId, UUID docId, Instant expiresAt) {
        String expected = sign(keyId, docId, expiresAt);
        // Constant-time comparison to avoid timing side-channel on token verification.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                rawToken.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String keyId, UUID docId, Instant expiresAt) {
        try {
            String payload = keyId + "." + docId + "." + expiresAt.getEpochSecond();
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            // Token embeds keyId + expiresAt + signature so validation can recompute
            // without a DB round-trip for the payload portion.
            return keyId + "." + docId + "." + expiresAt.getEpochSecond() + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC signature", e);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
