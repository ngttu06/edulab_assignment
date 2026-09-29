package vn.edu.khtn8.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String ACCESS_COOKIE = "EDULAB_ACCESS";
    private final JwtService jwtService;
    private final DatabaseUserDetailsService users;

    public JwtAuthenticationFilter(JwtService jwtService, DatabaseUserDetailsService users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        extractToken(request).ifPresent(token -> authenticate(token));
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        try {
            Claims claims = jwtService.parse(token);
            Long userId = claims.get("uid", Long.class);
            Integer authVersion = claims.get("av", Integer.class);
            if (userId == null || userId <= 0 || authVersion == null || claims.getExpiration() == null) {
                return;
            }
            AuthenticatedUser user = users.loadById(userId);
            if (user.isEnabled() && user.authVersion() == authVersion) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException | org.springframework.security.core.userdetails.UsernameNotFoundException ignored) {
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> ACCESS_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
