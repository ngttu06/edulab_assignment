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
    @Test void acceptsAllowlistedHttpsHlsAndMp4WithoutPlayerSyntax() {
        assertEquals("https://media.example.com/lesson.m3u8?token=test",policy.validate("PLAYERJS","https://media.example.com/lesson.m3u8?token=test").url());
        assertEquals("https://cdn.example.com/lesson.MP4?token=test",policy.validate("PLAYERJS","https://cdn.example.com/lesson.MP4?token=test").url());
        for (String url:new String[]{"http://media.example.com/x.m3u8","https://evil.example.com/x.m3u8","https://media.example.com.evil.com/x.m3u8",
                "https://media.example.com/x.html","https://user@media.example.com/x.m3u8","https://media.example.com:8443/x.m3u8",
                "https://media.example.com/x.m3u8#x","https://media.example.com/x.m3u8?x=[seek:100]","https://127.0.0.1/x.m3u8"})
            assertThrows(ResponseStatusException.class,()->policy.validate("PLAYERJS",url));
    }
    @Test void emptyOrWildcardConfigurationAllowsAllDomainsForBothFormats() {
        for (String hosts:new String[]{"", "  ", "*", " * "}) {
            var openPolicy=new VideoPolicy(hosts,"/js/vendor/playerjs.js");
            assertTrue(openPolicy.allowsAllHosts());assertEquals("https:",openPolicy.cspSources());
            for (String url:new String[]{"https://any.example.org/lesson.m3u8?token=abc%2B123",
                    "https://another.example.net:443/lesson.mp4", "https://new.cdn.example.com/lesson.M3U8"}) {
                assertEquals(url,openPolicy.validate("PLAYERJS",url).url());
                assertNull(openPolicy.validate("PLAYERJS",url).youtubeId());
            }
        }
        assertFalse(policy.allowsAllHosts());
        assertEquals("https://media.example.com https://cdn.example.com",policy.cspSources());
    }
    @Test void allDomainsStillRequireSafeHttpsUrlsAndSupportedFiles() {
        var openPolicy=new VideoPolicy("*","/js/vendor/playerjs.js");
        for (String url:new String[]{"http://video.example.com/x.mp4", "javascript:alert(1)", "file:///x.mp4",
                "https://127.0.0.1/x.mp4", "https://[::1]/x.m3u8", "https://192.168.1.1/x.mp4",
                "https://localhost/x.mp4", "https://video.local/x.mp4", "https://video.localhost/x.m3u8",
                "https://user:pass@video.example.com/x.mp4", "https://video.example.com:8443/x.mp4",
                "https://video.example.com/x.mp4#test", "https://video.example.com/x.html",
                "https://video.example.com/x.mp4?source={other}", "https://video.example.com/x.m3u8?x=[seek:100]",
                "https://video.example.com/x.mp4?x=a b", "https://video.example.com/x.mp4?x="+"a".repeat(2048)})
            assertThrows(ResponseStatusException.class,()->openPolicy.validate("PLAYERJS",url),url);
        assertThrows(ResponseStatusException.class,()->openPolicy.validate("OTHER","https://video.example.com/x.mp4"));
    }
    @Test void rejectsInvalidYoutubeAndInvalidDeploymentSettings() {
        for (String url:new String[]{"https://youtu.be/","https://youtu.be","https://www.youtube.com/playlist?list=123",
                "https://youtube.com.evil.com/watch?v=M7lc1UVf-VE","javascript:alert(1)","https://youtu.be/invalid"})
            assertThrows(ResponseStatusException.class,()->policy.validate("YOUTUBE",url));
        for (String hosts:new String[]{"*.example.com","*,example.com","https://example.com","127.0.0.1","video.local","video.example.com:443"})
            assertThrows(IllegalArgumentException.class,()->new VideoPolicy(hosts,"/js/playerjs.js"));
        assertThrows(IllegalArgumentException.class,()->new VideoPolicy("","https://example.com/player.js"));
        assertFalse(new VideoPolicy("","/js/missing-player.js").playerAvailable());
    }
}