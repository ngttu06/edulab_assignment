package vn.edu.khtn8.identity.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {
    @Test void tenRequestsPerAddressThenRejects() {
        var limiter=new LoginRateLimiter(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        for(int i=0;i<10;i++)limiter.check("127.0.0.1");
        assertEquals(429,assertThrows(ResponseStatusException.class,()->limiter.check("127.0.0.1")).getStatusCode().value());
        assertDoesNotThrow(()->limiter.check("127.0.0.2"));
    }
    @Test void passwordLimitsArePerAccountAndSeparateFromLoginAddresses() {
        var limiter = new LoginRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 10; i++) limiter.check("password:7", "Quá nhiều lần đổi mật khẩu.");
        var failure = assertThrows(ResponseStatusException.class,
                () -> limiter.check("password:7", "Quá nhiều lần đổi mật khẩu."));
        assertEquals(429, failure.getStatusCode().value());
        assertEquals("Quá nhiều lần đổi mật khẩu.", failure.getReason());
        assertDoesNotThrow(() -> limiter.check("password:8", "Quá nhiều lần đổi mật khẩu."));
        assertDoesNotThrow(() -> limiter.check("127.0.0.1"));
    }
}