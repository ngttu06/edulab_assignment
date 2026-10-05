package vn.edu.khtn8.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.config.AppProperties;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GateCheapAiProviderTest {
    private GateCheapAiProvider provider;
    private MockRestServiceServer server;
    private final AiProvider.AiQuestion question = new AiProvider.AiQuestion("Vì sao?","Kiến thức đã duyệt","HINT");
    @BeforeEach void setup() {
        var settings = new AppProperties(); settings.getAi().setApiKey("test-not-a-real-key");
        var builder = RestClient.builder().baseUrl("https://gatecheap.vn/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GateCheapAiProvider(settings,builder.build());
    }
    @Test void requestUsesConfiguredModelAndServerAuthorization() {
        server.expect(requestTo("https://gatecheap.vn/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer test-not-a-real-key"))
                .andExpect(jsonPath("$.model").value("gpt-6-astra"))
                .andExpect(jsonPath("$.max_tokens").value(1000))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"Giải thích\"}}]}",MediaType.APPLICATION_JSON));
        assertEquals("Giải thích",provider.answer(question).text()); server.verify();
    }
    @Test void upstreamErrorsDoNotLeakResponseOrKey() {
        server.expect(requestTo("https://gatecheap.vn/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-upstream-details"));
        var error = assertThrows(ResponseStatusException.class, () -> provider.answer(question));
        assertEquals(503,error.getStatusCode().value()); assertFalse(error.getReason().contains("secret-upstream")); server.verify();
    }
    @Test void emptyResponseFailsExplicitly() {
        server.expect(anything()).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, () -> provider.answer(question)); server.verify();
    }
    @Test void rejectsInsecureBaseUrl() {
        var settings = new AppProperties(); settings.getAi().setBaseUrl("http://example.com/v1");
        assertThrows(IllegalArgumentException.class, () -> new GateCheapAiProvider(settings,RestClient.builder()));
    }
    @Test void missingKeyNeverSendsRequest() {
        assertEquals(503,assertThrows(ResponseStatusException.class, () -> new GateCheapAiProvider(new AppProperties(),RestClient.builder()).answer(question)).getStatusCode().value());
    }
    @Test void supportUsesDifferentSystemAndIncludesBoundedConversation() {
        server.expect(anything()).andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("hỗ trợ website")))
                .andExpect(jsonPath("$.messages[2].role").value("user"))
                .andExpect(jsonPath("$.messages[3].role").value("assistant"))
                .andExpect(jsonPath("$.messages[4].content").value("Lớp 8"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"Tư vấn\"}}]}",MediaType.APPLICATION_JSON));
        provider.answer(new AiProvider.AiQuestion("Lớp 8","Danh mục công khai","SUPPORT",java.util.List.of(
                new AiProvider.ChatMessage("user","Toán"),new AiProvider.ChatMessage("assistant","Bạn học lớp nào?"))));
        server.verify();
    }
    @Test void redirectsNeverExposeKeyOrUpstreamBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FOUND).header("Location","https://evil.test").body("private-key"));
        var error=assertThrows(ResponseStatusException.class,()->provider.answer(question));
        assertFalse(error.getReason().contains("private-key"));server.verify();
    }
    @Test void dynamicConfigurationChangesNextRequestWithoutRestart() {
        var settings=org.mockito.Mockito.mock(AiSettingsService.class);
        var policy=org.mockito.Mockito.mock(AiEndpointPolicy.class);
        var builder=RestClient.builder().baseUrl("https://gatecheap.vn/v1");
        var mockServer=MockRestServiceServer.bindTo(builder).build();
        var active=new GateCheapAiProvider(new AppProperties(),builder.build(),settings,policy);
        org.mockito.Mockito.when(settings.current()).thenReturn(
                new AiSettingsService.RuntimeSettings(true,"https://gatecheap.vn/v1","first-model","first-secret",20,200,1000),
                new AiSettingsService.RuntimeSettings(true,"https://gatecheap.vn/v1","second-model","second-secret",20,200,500));
        mockServer.expect(requestTo("https://gatecheap.vn/v1/chat/completions")).andExpect(header("Authorization","Bearer first-secret"))
                .andExpect(jsonPath("$.model").value("first-model")).andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}",MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://gatecheap.vn/v1/chat/completions")).andExpect(header("Authorization","Bearer second-secret"))
                .andExpect(jsonPath("$.model").value("second-model")).andExpect(jsonPath("$.max_tokens").value(500))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}",MediaType.APPLICATION_JSON));
        active.answer(question);active.answer(question);mockServer.verify();
    }
}