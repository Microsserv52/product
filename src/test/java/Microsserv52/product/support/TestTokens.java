package Microsserv52.product.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public final class TestTokens {
    public static final String SECRET = "test-secret-with-at-least-32-characters-long";

    private TestTokens() {
    }

    public static String userToken(UUID accountId) {
        return token(accountId, List.of("ROLE_USER"));
    }

    public static String adminToken(UUID accountId) {
        return token(accountId, List.of("ROLE_USER", "ROLE_ADMIN"));
    }

    private static String token(UUID accountId, List<String> roles) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(accountId.toString())
                .claim("roles", roles)
                .issueTime(java.util.Date.from(Instant.now()))
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(300)))
                .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(SECRET));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create test token", exception);
        }
    }
}
