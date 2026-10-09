package vn.edu.khtn8.content.video;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class VideoPolicy {
    private static final Pattern HOST = Pattern.compile("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}");
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private final List<String> hosts;
    private final boolean allHosts;
    private final String playerPath;
    public VideoPolicy(@Value("${EDULAB_VIDEO_ALLOWED_HOSTS:}") String hosts,
                       @Value("${EDULAB_PLAYERJS_PATH:/js/vendor/playerjs.js}") String playerPath) {
        this.hosts = Arrays.stream(hosts.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).distinct().toList();
        this.allHosts = this.hosts.isEmpty() || this.hosts.equals(List.of("*"));
        if (!allHosts && this.hosts.stream().anyMatch(h -> !publicHost(h)))
            throw new IllegalArgumentException("EDULAB_VIDEO_ALLOWED_HOSTS nhận domain công khai hoặc * để cho phép mọi domain HTTPS; không nhận wildcard từng phần, cổng hoặc URL.");
        if (!playerPath.matches("/js/[A-Za-z0-9_/-]+\\.js") || playerPath.contains(".."))
            throw new IllegalArgumentException("EDULAB_PLAYERJS_PATH phải là đường dẫn /js/...js cùng website.");
        this.playerPath = playerPath;
    }
    public List<String> hosts() { return hosts; }
    public boolean allowsAllHosts() { return allHosts; }
    public String cspSources() {
        return allHosts ? "https:" : hosts.stream().map(h -> "https://" + h).collect(java.util.stream.Collectors.joining(" "));
    }
    public String playerPath() { return playerPath; }
    public boolean playerAvailable() { return new ClassPathResource("static" + playerPath).exists(); }
    public record Source(String url, String youtubeId) { }
    public Source validate(String provider, String value) {
        URI uri;
        try { uri = URI.create(value.trim()); } catch (RuntimeException e) { throw bad("Link video không hợp lệ."); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null)
            throw bad("Video cần link HTTPS, không chứa tài khoản, fragment hoặc cổng tùy ý.");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("YOUTUBE".equals(provider)) {
            String id = null;
            if (host.equals("youtu.be") && uri.getPath().length()>1) id = uri.getPath().substring(1);
            else if (List.of("youtube.com", "www.youtube.com", "m.youtube.com", "www.youtube-nocookie.com").contains(host)) {
                String path = uri.getPath();
                if (path.equals("/watch") && uri.getRawQuery() != null) {
                    for (String part : uri.getRawQuery().split("&")) if (part.startsWith("v=")) id = part.substring(2);
                } else if (path.startsWith("/embed/") || path.startsWith("/shorts/")) id = path.substring(path.lastIndexOf('/') + 1);
            }
            if (id == null || !YOUTUBE_ID.matcher(id).matches()) throw bad("Hãy nhập link của một video YouTube, không phải playlist hoặc kênh.");
            return new Source("https://www.youtube.com/watch?v=" + id, id);
        }
        if (!"PLAYERJS".equals(provider)) throw bad("Nguồn video không hợp lệ.");
        if (!publicHost(host)) throw bad("Video cần domain HTTPS hợp lệ, không dùng IP, localhost hoặc domain nội bộ .local/.localhost.");
        if (!allHosts && !hosts.contains(host)) throw bad("Domain video chưa được admin cho phép trong EDULAB_VIDEO_ALLOWED_HOSTS.");
        String path = uri.getPath().toLowerCase(Locale.ROOT);
        if (!path.endsWith(".m3u8") && !path.endsWith(".mp4")) throw bad("Link video phải trỏ tới tệp .m3u8 hoặc .mp4.");
        // PlayerJS source syntax uses brackets and separators; never interpret them from an input URL.
        if (value.matches("(?s).*[\\[\\]{}\\s\\\\].*")) throw bad("Link video chứa ký tự điều khiển nguồn không hợp lệ; hãy dùng URL đã encode.");
        String normalized=uri.toASCIIString();
        if (normalized.length()>2048) throw bad("Link video sau khi encode vượt 2048 ký tự.");
        return new Source(normalized, null);
    }
    private static boolean publicHost(String host) {
        return HOST.matcher(host).matches() && !host.endsWith(".local") && !host.endsWith(".localhost");
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}