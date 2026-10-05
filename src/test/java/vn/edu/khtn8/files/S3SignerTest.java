package vn.edu.khtn8.files;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class S3SignerTest {
    private final StorageSettingsService.Profile profile=new StorageSettingsService.Profile(1,"https://example.r2.cloudflarestorage.com","edulab","auto","edulab","test-access-id","test-secret-key-not-real");
    @Test void presignedPutBindsObjectMethodTypeExpiryAndDoesNotExposeSecret() {
        var time=Instant.parse("2026-10-05T01:02:03Z");
        var url=S3Signer.sign(profile,"PUT","edulab/staging/abc",Map.of("Content-Type","image/png"),300,time).toString();
        assertTrue(url.contains("/edulab/edulab/staging/abc?"));
        assertTrue(url.contains("X-Amz-Date=20261005T010203Z"));
        assertTrue(url.contains("X-Amz-Expires=300"));
        assertTrue(url.contains("X-Amz-SignedHeaders=content-type%3Bhost"));
        assertFalse(url.contains(profile.secretKey));
        assertNotEquals(url,S3Signer.sign(profile,"GET","edulab/staging/abc",Map.of("Content-Type","image/png"),300,time).toString());
        assertNotEquals(url,S3Signer.sign(profile,"PUT","edulab/staging/abc",Map.of("Content-Type","image/jpeg"),300,time).toString());
        assertEquals(url,S3Signer.sign(profile,"PUT","edulab/staging/abc",Map.of("content-type","image/png"),300,time).toString());
    }
    @Test void rejectsUnsafeKeysAndInvalidExpiry() {
        assertThrows(IllegalArgumentException.class,()->S3Signer.sign(profile,"PUT","../secret",Map.of(),300,Instant.now()));
        assertThrows(IllegalArgumentException.class,()->S3Signer.sign(profile,"PUT","safe",Map.of(),0,Instant.now()));
        assertEquals("a%20b%2B%2F%C4%91",S3Signer.encode("a b+/đ"));
    }
    @Test void cipherUsesRandomNonceAndAuthenticatedStorageDomain() {
        var cipher=new StorageSecretCipher(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        String a=cipher.encrypt("private-s3-key"),b=cipher.encrypt("private-s3-key");
        assertNotEquals(a,b);assertEquals("private-s3-key",cipher.decrypt(a));assertFalse(a.contains("private-s3-key"));
        var ai=new vn.edu.khtn8.ai.AiSecretCipher(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ai.decrypt(a));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new StorageSecretCipher("").encrypt("secret"));
    }
    @Test void endpointAndCspOnlyTrustExactOperatorHosts() {
        var policy=new StorageEndpointPolicy("example.r2.cloudflarestorage.com");
        assertEquals(profile.endpoint,policy.validate(profile.endpoint+"/"));
        assertEquals(profile.endpoint,policy.cspSources());
        for(String endpoint:java.util.List.of("http://example.r2.cloudflarestorage.com","https://evil.test","https://example.r2.cloudflarestorage.com/path","https://user@example.r2.cloudflarestorage.com","https://example.r2.cloudflarestorage.com?key=value"))
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->policy.validate(endpoint));
    }
}