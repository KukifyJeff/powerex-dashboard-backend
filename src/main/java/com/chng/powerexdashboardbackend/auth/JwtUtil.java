package com.chng.powerexdashboardbackend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final Key key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.security.jwt.secret}") String secret,
                   @Value("${app.security.jwt.expiration-ms:3600000}") long expirationMs) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
            if (keyBytes.length < 32) {
                // decoded but too short -> derive 32-byte key from original secret
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                keyBytes = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                try {
                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                    keyBytes = sha.digest(keyBytes);
                } catch (NoSuchAlgorithmException e) {
                    // fallback: pad/truncate to 32 bytes
                    byte[] padded = new byte[32];
                    System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
                    keyBytes = padded;
                }
            }
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        return claims.getSubject();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
