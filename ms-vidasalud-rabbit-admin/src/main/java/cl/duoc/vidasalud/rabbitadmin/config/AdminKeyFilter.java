package cl.duoc.vidasalud.rabbitadmin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminKeyFilter extends OncePerRequestFilter {
    private final byte[] secret;

    public AdminKeyFilter(@Value("${vidasalud.admin.api-key}") String secret) {
        if (secret.length() < 24) {
            throw new IllegalArgumentException("RABBIT_ADMIN_KEY debe tener al menos 24 caracteres");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Si se provee cabecera X-Admin-Key en cualquier endpoint, o si la ruta es /api/rabbit-admin/*
        String supplied = request.getHeader("X-Admin-Key");
        if (path.startsWith("/api/rabbit-admin")) {
            if (supplied == null || !MessageDigest.isEqual(secret, supplied.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-Admin-Key faltante o no válida");
                return;
            }
        } else if (supplied != null) {
            if (!MessageDigest.isEqual(secret, supplied.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-Admin-Key no válida");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
