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
}