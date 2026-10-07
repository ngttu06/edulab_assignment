package vn.edu.khtn8.content.video;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class VideoPolicyTest {
    private final VideoPolicy policy=new VideoPolicy("media.example.com, cdn.example.com","/js/vendor/playerjs.js");
    @Test void acceptsAndNormalizesSupportedYoutubeLinks() {
        for (String url:new String[]{"https://youtu.be/M7lc1UVf-VE","https://www.youtube.com/watch?v=M7lc1UVf-VE&t=5",
                "https://www.youtube.com/embed/M7lc1UVf-VE","https://www.youtube.com/shorts/M7lc1UVf-VE"}) {
            assertEquals("M7lc1UVf-VE",policy.validate("YOUTUBE",url).youtubeId());
            assertEquals("https://www.youtube.com/watch?v=M7lc1UVf-VE",policy.validate("YOUTUBE",url).url());
        }
    }
    @Test void acceptsOnlyAllowlistedHttpsHlsWithoutPlayerSyntax() {
        assertEquals("https://media.example.com/lesson.m3u8?token=test",policy.validate("PLAYERJS","https://media.example.com/lesson.m3u8?token=test").url());
        for (String url:new String[]{"http://media.example.com/x.m3u8","https://evil.example.com/x.m3u8","https://media.example.com.evil.com/x.m3u8",
                "https://media.example.com/x.mp4","https://user@media.example.com/x.m3u8","https://media.example.com:8443/x.m3u8",
                "https://media.example.com/x.m3u8#x","https://media.example.com/x.m3u8?x=[seek:100]","https://127.0.0.1/x.m3u8"})
            assertThrows(ResponseStatusException.class,()->policy.validate("PLAYERJS",url));
    }
    @Test void rejectsInvalidYoutubeAndInvalidDeploymentSettings() {
        for (String url:new String[]{"https://youtu.be/","https://youtu.be","https://www.youtube.com/playlist?list=123",
                "https://youtube.com.evil.com/watch?v=M7lc1UVf-VE","javascript:alert(1)","https://youtu.be/invalid"})
            assertThrows(ResponseStatusException.class,()->policy.validate("YOUTUBE",url));
        for (String hosts:new String[]{"*.example.com","https://example.com","127.0.0.1","video.local","video.example.com:443"})
            assertThrows(IllegalArgumentException.class,()->new VideoPolicy(hosts,"/js/playerjs.js"));
        assertThrows(IllegalArgumentException.class,()->new VideoPolicy("","https://example.com/player.js"));
        assertFalse(new VideoPolicy("","/js/missing-player.js").playerAvailable());
    }
}