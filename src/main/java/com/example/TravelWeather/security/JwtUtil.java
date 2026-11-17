package com.example.TravelWeather.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    private static final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long EXPIRATION_TIME = 86400000; // 24 horas

    public String generarToken(Integer usuarioId) {
        return Jwts.builder()
                .setSubject(usuarioId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }

    public Integer extraerId(String token) {
        String tokenLimpio = token.replace("Bearer ", "");
        String idStr = Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(tokenLimpio)
                .getBody()
                .getSubject();
        return Integer.parseInt(idStr);
    }
}