package vn.edu.khtn8.content.video;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

/** Vendor bytes must remain identical to the official, versioned distribution. */
class PlayerJsDistributionTest {
    @Test void officialPlayerIsPackagedUnchangedAndDetectedByVideoPolicy() throws Exception {
        var resource=new ClassPathResource("static/js/vendor/playerjs.js");
        assertTrue(resource.exists());
        byte[] bytes;
        try (var stream=resource.getInputStream()) { bytes=stream.readAllBytes(); }
        assertTrue(new String(bytes,0,100,StandardCharsets.UTF_8).contains("Playerjs.com 22.0.7"));
        assertEquals("44c7d16598f92f5e6b81960ebcd81111c7f9d581a209d1b06577d7a6aa85ef4f",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertTrue(new VideoPolicy("", "/js/vendor/playerjs.js").playerAvailable());
        assertFalse(new VideoPolicy("", "/js/vendor/not-installed.js").playerAvailable());
    }
}