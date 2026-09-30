package vn.edu.khtn8.identity.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import org.springframework.http.ResponseCookie;
import vn.edu.khtn8.common.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.edu.khtn8.identity.dto.LoginRequest;
import vn.edu.khtn8.identity.dto.LoginResponse;
import vn.edu.khtn8.identity.entity.User;
import vn.edu.khtn8.identity.repository.UserRepository;
import vn.edu.khtn8.identity.security.JwtAuthenticationFilter;
import vn.edu.khtn8.identity.security.JwtService;

@Service
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final vn.edu.khtn8.identity.config.JwtProperties jwtProperties;
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    public AuthenticationService(AuthenticationManager authenticationManager, UserRepository users, JwtService jwtService, AppProperties properties,
            org.springframework.jdbc.core.JdbcTemplate jdbc, vn.edu.khtn8.identity.config.JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.properties = properties;
        this.jdbc = jdbc;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = users.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found"));
        String token = jwtService.createAccessToken(user);
        response.addHeader("Set-Cookie", accessCookie(token, (int) jwtService.accessTokenSeconds(), servletRequest));
        createRefresh(user.getId(), java.util.UUID.randomUUID().toString(), servletRequest, response);
        return new LoginResponse(jwtService.accessTokenSeconds(), user.getUsername(), user.getFullName(), user.getRole().name());
    }

    @Transactional
    public void logout(AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {
        users.revokeAccessTokens(user.id());
        jdbc.update("UPDATE refresh_sessions SET revoked_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND revoked_at IS NULL", user.id());
        response.addHeader("Set-Cookie", accessCookie("", 0, request));
        response.addHeader("Set-Cookie", refreshCookie("", 0, request));
    }

    @Transactional(noRollbackFor = org.springframework.security.authentication.BadCredentialsException.class)
    public LoginResponse refresh(String token, HttpServletRequest request, HttpServletResponse response) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw invalidRefresh();
        var rows = jdbc.queryForList("SELECT id,user_id,family_id,revoked_at,expires_at FROM refresh_sessions WHERE token_hash=? FOR UPDATE", hash(token));
        if (rows.isEmpty()) throw invalidRefresh();
        var row = rows.getFirst();
        if (row.get("revoked_at") != null) {
            jdbc.update("UPDATE refresh_sessions SET revoked_at=CURRENT_TIMESTAMP(6) WHERE family_id=? AND revoked_at IS NULL", row.get("family_id"));
            users.revokeAccessTokens(((Number)row.get("user_id")).longValue());
            throw invalidRefresh();
        }
        if (!((java.sql.Timestamp)row.get("expires_at")).toInstant().isAfter(java.time.Instant.now())) throw invalidRefresh();
        User user = users.findById(((Number)row.get("user_id")).longValue()).filter(User::isEnabled).orElseThrow(AuthenticationService::invalidRefresh);
        jdbc.update("UPDATE refresh_sessions SET revoked_at=CURRENT_TIMESTAMP(6) WHERE id=?", row.get("id"));
        createRefresh(user.getId(), row.get("family_id") == null ? java.util.UUID.randomUUID().toString() : row.get("family_id").toString(), request, response);
        response.addHeader("Set-Cookie", accessCookie(jwtService.createAccessToken(user), (int)jwtService.accessTokenSeconds(), request));
        return new LoginResponse(jwtService.accessTokenSeconds(), user.getUsername(), user.getFullName(), user.getRole().name());
    }

    private void createRefresh(long userId, String familyId, HttpServletRequest request, HttpServletResponse response) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = java.util.HexFormat.of().formatHex(bytes);
        long seconds = java.time.Duration.ofDays(jwtProperties.getRefreshTokenDays()).toSeconds();
        jdbc.update("INSERT INTO refresh_sessions(user_id,token_hash,expires_at,family_id) VALUES(?,?,?,?)", userId, hash(token), java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(seconds)), familyId);
        response.addHeader("Set-Cookie", refreshCookie(token, seconds, request));
    }

    private String refreshCookie(String value, long age, HttpServletRequest request) {
        return ResponseCookie.from("EDULAB_REFRESH",value).httpOnly(true).secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite()).path(request.getContextPath()+"/api/v1/auth").maxAge(age).build().toString();
    }

    private static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static org.springframework.security.authentication.BadCredentialsException invalidRefresh() {
        return new org.springframework.security.authentication.BadCredentialsException("Invalid refresh session");
    }

    private String accessCookie(String value, int maxAge, HttpServletRequest request) {
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        return ResponseCookie.from(JwtAuthenticationFilter.ACCESS_COOKIE, value)
                .httpOnly(true).secure(properties.getCookie().isSecure()).path(path)
                .sameSite(properties.getCookie().getSameSite()).maxAge(maxAge).build().toString();
    }
}
