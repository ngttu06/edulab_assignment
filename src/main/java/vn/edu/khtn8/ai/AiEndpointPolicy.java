package vn.edu.khtn8.ai;

import java.net.InetAddress;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Exact host allowlist controlled by the operator, not by the admin request. No redirects. */
@Component
public class AiEndpointPolicy {
    private final java.util.Set<String> hosts;
    public AiEndpointPolicy(@Value("${EDULAB_AI_ALLOWED_HOSTS:gatecheap.vn}") String allowed) {
        hosts = java.util.Arrays.stream(allowed.split(",")).map(String::trim)
                .map(s -> s.toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }
    public String validate(String input) {
        try {
            URI uri = URI.create(input.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !hosts.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))
                    || uri.getPath().contains("..") || uri.getPath().contains("%")) throw bad();
            return uri.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException ex) { throw bad(); }
    }
    public void requirePublicAddress(String baseUrl) {
        String validated = validate(baseUrl);
        try {
            for (InetAddress address : InetAddress.getAllByName(URI.create(validated).getHost())) {
                byte[] b = address.getAddress();
                int first = Byte.toUnsignedInt(b[0]), second = Byte.toUnsignedInt(b[1]);
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || (b.length == 16 && (first & 0xfe) == 0xfc)
                        || (b.length == 4 && (first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127)
                        || (first == 169 && second == 254) || (first == 198 && (second == 18 || second == 19))))) throw bad();
            }
        } catch (java.net.UnknownHostException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không phân giải được host AI.");
        }
    }
    private static ResponseStatusException bad() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Host AI phải là HTTPS thuộc EDULAB_AI_ALLOWED_HOSTS, không có thông tin đăng nhập, query hoặc địa chỉ nội bộ.");
    }
}