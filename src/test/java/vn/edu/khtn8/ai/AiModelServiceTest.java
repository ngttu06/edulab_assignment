package vn.edu.khtn8.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiModelServiceTest {
    final AiSettingsService settings=mock(AiSettingsService.class);
    final AiEndpointPolicy endpoints=mock(AiEndpointPolicy.class);
    final AiSettingsService.ModelsInput input=new AiSettingsService.ModelsInput("https://provider.test/v1","");
    MockRestServiceServer server;
    AiModelService service;
    AuthenticatedUser actor(String role) {return new AuthenticatedUser(1L,"admin","Admin","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    @BeforeEach void setup() {
        var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();
        service=new AiModelService(settings,endpoints,new ObjectMapper(),builder.build());
        when(settings.modelConnection(any(),any())).thenReturn(new AiSettingsService.Connection("https://provider.test/v1","private-secret"));
    }
    @Test void fetchUsesServerBearerAndVersionedUrlAndReturnsOnlySortedValidUniqueIds() {
        server.expect(requestTo("https://provider.test/v1/models")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization","Bearer private-secret"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"z-model\",\"owner\":\"secret\"},{\"id\":\"a/model:1\"},{\"id\":\"z-model\"},{\"id\":\"<script>\"},{\"id\":42}]}",MediaType.APPLICATION_JSON));
        assertEquals(List.of("a/model:1","z-model"),service.fetch(actor("ADMIN"),input).models());
        verify(endpoints).requirePublicAddress("https://provider.test/v1");server.verify();
        verify(settings,never()).save(any(),any());
    }
    @Test void nonAdminNeverTouchesSettingsOrNetwork() {
        assertThrows(AccessDeniedException.class,()->service.fetch(actor("STUDENT"),input));
        verify(settings,never()).modelConnection(any(),any());verifyNoInteractions(endpoints);
    }
    @Test void privateEndpointRejectedBeforeNetwork() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(endpoints).requirePublicAddress(anyString());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.fetch(actor("ADMIN"),input)).getStatusCode().value());server.verify();
    }
    @Test void errorsAndRedirectsDoNotLeakUpstreamBodyOrKeys() {
        for(HttpStatus status:List.of(HttpStatus.UNAUTHORIZED,HttpStatus.TOO_MANY_REQUESTS,HttpStatus.FOUND))
            server.expect(anything()).andRespond(withStatus(status).header("Location","https://evil.test").body("private-secret-upstream"));
        for(int i=0;i<3;i++) {
            var ex=assertThrows(ResponseStatusException.class,()->service.fetch(actor("ADMIN"),input));
            assertEquals(503,ex.getStatusCode().value());assertFalse(ex.getReason().contains("private-secret"));
        }
        server.verify();
    }
    @Test void invalidEmptyOrOversizedResponsesAreSanitized() {
        for(String body:List.of("private-secret-invalid-json","{}","{\"data\":[]}","{\"data\":[{\"id\":\"bad id\"}]}"," ".repeat(1_048_577)))
            server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        for(int i=0;i<5;i++) {
            var ex=assertThrows(ResponseStatusException.class,()->service.fetch(actor("ADMIN"),input));
            assertEquals(503,ex.getStatusCode().value());assertFalse(ex.getReason().contains("private-secret"));
        }
        server.verify();
    }
}