package vn.edu.khtn8.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
class AiSettingsMysqlTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));
        p.add("spring.datasource.username",()->"edulab_test");p.add("spring.datasource.password",()->"local-test-only-password");
        p.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
        p.add("EDULAB_AI_ENCRYPTION_KEY",()->java.util.Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired AiSettingsService settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor scheduler;
    @Test @Transactional void supportQuotaResetsAtDatabaseUtcMidnightAndRotatesHashes() {
        assertEquals("+00:00",jdbc.queryForObject("SELECT @@session.time_zone",String.class));
        jdbc.update("UPDATE ai_settings SET enabled=TRUE WHERE id=1");
        var quota=new SupportAiQuota(jdbc,"test-only-quota-key");
        var config=new AiSettingsService.RuntimeSettings(true,"url","model","unused",1,10000,500);
        String address="midnight-test-"+java.util.UUID.randomUUID();
        long midnight=java.time.Instant.parse("2030-01-02T00:00:00Z").getEpochSecond();
        try {
            jdbc.execute("SET timestamp="+(midnight-10));
            long before=quota.reserve(null,address,"same-guest",config);
            quota.finish(before,true);
            assertEquals(429,assertThrows(ResponseStatusException.class,()->quota.reserve(null,address,"rotated-guest",config)).getStatusCode().value());
            var previous=jdbc.queryForMap("SELECT actor_hash,address_hash FROM support_ai_requests WHERE id=?",before);
            jdbc.execute("SET timestamp="+(midnight+10));
            long after=quota.reserve(null,address,"same-guest",config);
            var current=jdbc.queryForMap("SELECT actor_hash,address_hash FROM support_ai_requests WHERE id=?",after);
            assertNotEquals(previous.get("actor_hash"),current.get("actor_hash"));
            assertNotEquals(previous.get("address_hash"),current.get("address_hash"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE created_at>=UTC_DATE()",Integer.class));
        } finally { jdbc.execute("SET timestamp=0"); }
    }
    @Test @Transactional void scheduledCleanupDeletesExpiredMetadataButKeepsRecentRequests() {
        jdbc.update("UPDATE ai_settings SET enabled=TRUE WHERE id=1");
        var quota=new SupportAiQuota(jdbc,"test-only-quota-key");
        var config=new AiSettingsService.RuntimeSettings(true,"url","model","unused",100,10000,500);
        String address="cleanup-test-"+java.util.UUID.randomUUID();
        long expired=quota.reserve(null,address,"guest",config);
        jdbc.update("UPDATE support_ai_requests SET created_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 3 DAY) WHERE id=?",expired);
        long recent=quota.reserve(null,address,"guest",config);
        quota.cleanup();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE id=?",Integer.class,expired));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE id=?",Integer.class,recent));
        assertTrue(scheduler.getScheduledTasks().stream().anyMatch(task->task.getTask().getRunnable().toString().contains("SupportAiQuota.cleanup")));
    }
    @Test @Transactional void adminSaveEncryptsKeyPersistsMetadataAuditsAndAppliesImmediately() {
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username='admin'",Long.class);
        var admin=new AuthenticatedUser(id,"admin","Admin","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var saved=settings.save(admin,new AiSettingsService.Input(true,"https://gatecheap.vn/v1/","gpt-6.1-sol","test-only-private-secret",false,10,100,500));
        assertTrue(saved.enabled());assertTrue(saved.keyConfigured());assertTrue(saved.encryptionReady());
        assertEquals("https://gatecheap.vn/v1",saved.baseUrl());
        assertEquals("test-only-private-secret",settings.current().apiKey);
        assertFalse(jdbc.queryForObject("SELECT encrypted_api_key FROM ai_settings WHERE id=1",String.class).contains("test-only-private-secret"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor_user_id=? AND action='UPDATE_AI_SETTINGS'",Integer.class,id)>0);
        settings.save(admin,new AiSettingsService.Input(true,saved.baseUrl(),"other-model","",false,8,80,600));
        assertEquals("test-only-private-secret",settings.current().apiKey);assertEquals("other-model",settings.current().model);
        assertEquals(8,settings.dailyLimit());
        settings.save(admin,new AiSettingsService.Input(false,saved.baseUrl(),"other-model","",true,8,80,600));
        assertFalse(settings.enabled());assertFalse(settings.view(admin).keyConfigured());
    }
    @Test void databaseQuotaStoresOnlyHashesAndCapsSameIpAcrossGuests() {
        // Independent transactions commit the budget; delete only the records created by this test.
        var quota=new SupportAiQuota(jdbc,"test-only-quota-key");
        boolean wasEnabled=settings.enabled();
        jdbc.update("UPDATE ai_settings SET enabled=TRUE WHERE id=1");
        String address="quota-test-"+java.util.UUID.randomUUID();
        var config=new AiSettingsService.RuntimeSettings(true,"url","model","unused",2,10000,500);
        var ids=new java.util.ArrayList<Long>();
        try {
            for(int i=0;i<2;i++) ids.add(quota.reserve(null,address,"guest-"+i,config));
            assertEquals(429,assertThrows(ResponseStatusException.class,()->quota.reserve(null,address,"rotated-guest",config)).getStatusCode().value());
            var row=jdbc.queryForMap("SELECT * FROM support_ai_requests WHERE id=?",ids.getFirst());
            assertEquals(64,((String)row.get("address_hash")).length());assertFalse(row.toString().contains(address));
            quota.finish(ids.getFirst(),false);
            assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM support_ai_requests WHERE id=?",String.class,ids.getFirst()));
        } finally {
            for(long id:ids) jdbc.update("DELETE FROM support_ai_requests WHERE id=?",id);
            jdbc.update("UPDATE ai_settings SET enabled=? WHERE id=1",wasEnabled);
        }
    }
}