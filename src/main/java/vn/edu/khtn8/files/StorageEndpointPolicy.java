package vn.edu.khtn8.files;

import java.net.URI;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.khtn8.ai.AiEndpointPolicy;

@Component
public class StorageEndpointPolicy {
    public static final String DEFAULT_ENDPOINT="https://c9da795e23235545fa54e8c7ee61d3cf.r2.cloudflarestorage.com";
    private final AiEndpointPolicy policy;
    private final Set<String> hosts;
    public StorageEndpointPolicy(@Value("${EDULAB_STORAGE_ALLOWED_HOSTS:c9da795e23235545fa54e8c7ee61d3cf.r2.cloudflarestorage.com}") String allowed) {
        policy=new AiEndpointPolicy(allowed);
        hosts=Arrays.stream(allowed.split(",")).map(String::trim).filter(s->s.matches("[A-Za-z0-9.-]+")).collect(Collectors.toSet());
    }
    public String validate(String endpoint) {
        String value=policy.validate(endpoint);
        if(!URI.create(value).getPath().isEmpty()) throw StorageSettingsService.bad("Endpoint storage không được có đường dẫn.");
        return value;
    }
    public void requirePublicAddress(String endpoint) { policy.requirePublicAddress(validate(endpoint)); }
    public String cspSources() { return hosts.stream().sorted().map(h->"https://"+h).collect(Collectors.joining(" ")); }
}