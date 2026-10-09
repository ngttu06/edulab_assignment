package vn.edu.khtn8.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

/** OpenAI-compatible model discovery. Never returns credentials/upstream bodies or follows redirects. */
@Service
public class AiModelService {
    private final AiSettingsService settings;
    private final AiEndpointPolicy endpoints;
    private final ObjectMapper json;
    private final RestClient client;
    private final Semaphore requests=new Semaphore(2);
    @Autowired
    public AiModelService(AiSettingsService settings,AiEndpointPolicy endpoints,ObjectMapper json,RestClient.Builder builder) {
        this(settings,endpoints,json,client(builder));
    }
    AiModelService(AiSettingsService settings,AiEndpointPolicy endpoints,ObjectMapper json,RestClient client) {
        this.settings=settings;this.endpoints=endpoints;this.json=json;this.client=client;
    }
    private static RestClient client(RestClient.Builder builder) {
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory=new JdkClientHttpRequestFactory(http);factory.setReadTimeout(Duration.ofSeconds(15));
        return builder.clone().requestFactory(factory).build();
    }
    public record Models(List<String> models) {}
    public Models fetch(AuthenticatedUser actor,AiSettingsService.ModelsInput input) {
        vn.edu.khtn8.school.SchoolService.require(actor,"ADMIN");
        var connection=settings.modelConnection(actor,input);
        endpoints.requirePublicAddress(connection.baseUrl);
        if(!requests.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Đang lấy danh sách model. Hãy thử lại sau.");
        try {
            // Base URL already includes the API version, e.g. https://provider/v1.
            return client.get().uri(URI.create(connection.baseUrl+"/models"))
                    .header(HttpHeaders.AUTHORIZATION,"Bearer "+connection.apiKey)
                    .header(HttpHeaders.USER_AGENT,"EduLab/0.1").header(HttpHeaders.ACCEPT,"application/json")
                    .exchange((request,response)-> {
                        if(!response.getStatusCode().is2xxSuccessful()) throw unavailable("Không lấy được model từ provider (HTTP "+response.getStatusCode().value()+"). Kiểm tra Base URL, API key hoặc nhập model thủ công.");
                        byte[] body=response.getBody().readNBytes(1_048_577);
                        if(body.length>1_048_576) throw unavailable("Danh sách model từ provider quá lớn. Hãy nhập model thủ công.");
                        var data=json.readTree(body);
                        if(data==null || !data.path("data").isArray()) throw unavailable("Provider không trả danh sách model theo định dạng OpenAI. Hãy nhập model thủ công.");
                        var models=new TreeSet<String>();
                        for(var item:data.path("data")) {
                            var id=item.path("id");
                            if(id.isTextual() && id.asText().matches("[A-Za-z0-9._:/-]{1,120}")) models.add(id.asText());
                            if(models.size()>2000) throw unavailable("Danh sách model quá lớn. Hãy nhập model thủ công.");
                        }
                        if(models.isEmpty()) throw unavailable("Provider chưa trả model hợp lệ. Hãy nhập model thủ công.");
                        return new Models(List.copyOf(models));
                    });
        } catch(RestClientException ex) {
            throw unavailable("Không kết nối được API model hoặc phản hồi không hợp lệ. Hãy thử lại hoặc nhập model thủ công.");
        } finally { requests.release(); }
    }
    private static ResponseStatusException unavailable(String message) {return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message);}
}