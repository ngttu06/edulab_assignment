package vn.edu.khtn8.ai;

import java.util.Base64;
import java.util.List;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSettingsTest {
    private final String key=Base64.getEncoder().encodeToString(new byte[32]);
    private AuthenticatedUser actor(String role) {return new AuthenticatedUser(1L,"admin","Admin","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    @Test void encryptionRoundTripsUsesFreshNoncesAndDetectsTampering() {
        var cipher=new AiSecretCipher(key);
        String encrypted=cipher.encrypt("private-api-key");
        assertEquals("private-api-key",cipher.decrypt(encrypted));
        assertFalse(encrypted.contains("private-api-key"));
        assertNotEquals(encrypted,cipher.encrypt("private-api-key"));
        assertThrows(ResponseStatusException.class,()->cipher.decrypt(encrypted.substring(0,20)+"A"+encrypted.substring(21)));
        assertThrows(ResponseStatusException.class,()->new AiSecretCipher("").encrypt("secret"));
        assertThrows(IllegalStateException.class,()->new AiSecretCipher("aW52YWxpZA=="));
    }
    @Test void endpointRejectsUntrustedInsecureAndAmbiguousUrls() {
        var policy=new AiEndpointPolicy("gatecheap.vn");
        assertEquals("https://gatecheap.vn/v1",policy.validate("https://gatecheap.vn/v1/"));
        for(String url:List.of("http://gatecheap.vn/v1","https://127.0.0.1/v1","https://gatecheap.vn.evil.test/v1",
                "https://secret@gatecheap.vn/v1","https://gatecheap.vn/v1?key=x","https://gatecheap.vn/v1#x",
                "https://gatecheap.vn:444/v1","https://gatecheap.vn/../admin","https://gatecheap.vn/%2e%2e"))
            assertThrows(ResponseStatusException.class,()->policy.validate(url),url);
        assertThrows(ResponseStatusException.class,()->new AiEndpointPolicy("localhost").requirePublicAddress("https://localhost/v1"));
    }
    @Test void nonAdminCannotReadOrSaveSettings() {
        var jdbc=mock(JdbcTemplate.class);
        var service=new AiSettingsService(jdbc,new AiSecretCipher(key),new AiEndpointPolicy("gatecheap.vn"));
        assertThrows(AccessDeniedException.class,()->service.view(actor("STUDENT")));
        assertThrows(AccessDeniedException.class,()->service.save(actor("PARENT"),input(true,"https://gatecheap.vn/v1","secret-new-key",false)));
        verifyNoInteractions(jdbc);
    }
    @Test void changingHostRequiresNewKeyAndEnablingRequiresStoredKey() {
        var jdbc=mock(JdbcTemplate.class);
        var previous=new HashMap<String,Object>();previous.put("base_url","https://gatecheap.vn/v1");previous.put("encrypted_api_key","encrypted");
        when(jdbc.queryForMap(anyString())).thenReturn(previous);
        var service=new AiSettingsService(jdbc,new AiSecretCipher(key),new AiEndpointPolicy("gatecheap.vn,api.openai.com"));
        assertThrows(ResponseStatusException.class,()->service.save(actor("ADMIN"),input(true,"https://api.openai.com/v1","",false)));
        previous.put("encrypted_api_key",null);
        assertThrows(ResponseStatusException.class,()->service.save(actor("ADMIN"),input(true,"https://gatecheap.vn/v1","",false)));
        assertThrows(ResponseStatusException.class,()->service.save(actor("ADMIN"),input(true,"https://gatecheap.vn/v1","secret-new-key",true)));
        verify(jdbc,never()).update(startsWith("UPDATE ai_settings"),any(Object[].class));
    }
    private AiSettingsService.Input input(boolean enabled,String url,String apiKey,boolean clear) {
        return new AiSettingsService.Input(enabled,url,"gpt-6.1-sol",apiKey,clear,20,200,1000);
    }
    @Test void modelDiscoveryKeepsStoredKeyOnSameBaseUrlAndRequiresNewKeyWhenChangingProvider() {
        var jdbc=mock(JdbcTemplate.class);var cipher=new AiSecretCipher(key);
        var stored=new HashMap<String,Object>();stored.put("base_url","https://gatecheap.vn/v1");stored.put("encrypted_api_key",cipher.encrypt("stored-secret"));
        when(jdbc.queryForMap(anyString())).thenReturn(stored);
        var service=new AiSettingsService(jdbc,cipher,new AiEndpointPolicy("gatecheap.vn,api.openai.com"));
        assertEquals("stored-secret",service.modelConnection(actor("ADMIN"),new AiSettingsService.ModelsInput("https://gatecheap.vn/v1/","")).apiKey);
        assertThrows(ResponseStatusException.class,()->service.modelConnection(actor("ADMIN"),new AiSettingsService.ModelsInput("https://api.openai.com/v1","")));
        assertEquals("new-secret",service.modelConnection(actor("ADMIN"),new AiSettingsService.ModelsInput("https://api.openai.com/v1","new-secret")).apiKey);
        assertThrows(AccessDeniedException.class,()->service.modelConnection(actor("TEACHER"),new AiSettingsService.ModelsInput("https://gatecheap.vn/v1","")));
        assertThrows(ResponseStatusException.class,()->service.modelConnection(actor("ADMIN"),new AiSettingsService.ModelsInput("https://evil.test/v1","new-secret")));
        assertThrows(ResponseStatusException.class,()->service.modelConnection(actor("ADMIN"),new AiSettingsService.ModelsInput("https://gatecheap.vn/v1","bad\r\nkey")));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void savePersistsPromptsPreservesOmittedValuesAndResetsBlankToDefault() {
        var jdbc=mock(JdbcTemplate.class);var previous=new HashMap<String,Object>();
        previous.put("base_url","https://gatecheap.vn/v1");previous.put("system_prompt","old lesson");previous.put("support_system_prompt","old support");
        when(jdbc.queryForMap(anyString())).thenReturn(previous);
        var service=new AiSettingsService(jdbc,new AiSecretCipher(key),new AiEndpointPolicy("gatecheap.vn"));
        service.save(actor("ADMIN"),new AiSettingsService.Input(false,"https://gatecheap.vn/v1","model","",false,10,100,500," new lesson "," "));
        verify(jdbc).update(startsWith("UPDATE ai_settings"),eq(false),eq("https://gatecheap.vn/v1"),eq("model"),isNull(),eq(10),eq(100),eq(500),eq("new lesson"),isNull());
        service.save(actor("ADMIN"),input(false,"https://gatecheap.vn/v1","",false));
        verify(jdbc).update(startsWith("UPDATE ai_settings"),eq(false),eq("https://gatecheap.vn/v1"),eq("gpt-6.1-sol"),isNull(),eq(20),eq(200),eq(1000),eq("old lesson"),eq("old support"));
        assertThrows(ResponseStatusException.class,()->service.save(actor("ADMIN"),new AiSettingsService.Input(false,"https://gatecheap.vn/v1","model","",false,10,100,500,"x".repeat(8001),"")));
    }
}