package com.tanidikvar.api.auth.security;

import com.tanidikvar.api.auth.config.AuthProperties;
import com.tanidikvar.api.auth.exception.AuthRejectedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
    private static final String ISSUER = "tanidikvar-api";
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public JwtTokens(AuthProperties properties, Clock clock) {
        this.clock = clock;
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(properties.secret()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("JWT_SECRET must be base64 encoded"); }
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes");
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        var jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(ISSUER)));
        this.decoder = jwtDecoder;
    }

    public String issue(String kind, UUID userId, UUID familyId, UUID tokenId, Instant expiry) {
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(userId.toString())
                .audience(List.of("tanidikvar-" + kind)).issuedAt(clock.instant()).expiresAt(expiry)
                .id(tokenId.toString()).claim("kind", kind).claim("family", familyId.toString()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public Jwt read(String token, String kind) {
        if (token == null || token.length() > 4096) throw new AuthRejectedException();
        try {
            var jwt = decoder.decode(token);
            if (!kind.equals(jwt.getClaimAsString("kind")) || !jwt.getAudience().equals(List.of("tanidikvar-" + kind))
                    || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant())
                    || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(clock.instant())) throw new AuthRejectedException();
            UUID.fromString(jwt.getSubject()); UUID.fromString(jwt.getId()); UUID.fromString(jwt.getClaimAsString("family"));
            return jwt;
        } catch (JwtException | IllegalArgumentException | NullPointerException e) { throw new AuthRejectedException(); }
    }

    public String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
