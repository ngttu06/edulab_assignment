package vn.edu.khtn8.files;

import java.util.List;
import java.util.Map;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
@Transactional
class DirectUploadMysqlTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));
        p.add("spring.datasource.username",()->"edulab_test");p.add("spring.datasource.password",()->"local-test-only-password");
        p.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
        p.add("EDULAB_STORAGE_ENCRYPTION_KEY",()->java.util.Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired DirectUploadService uploads;
    @Autowired StorageSettingsService settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiAttachmentService attachments;
    @Autowired FileService files;
    @Autowired vn.edu.khtn8.community.ForumService forum;
    @Autowired vn.edu.khtn8.community.SocialCommunityService social;
    @MockBean R2Client r2;
    @MockBean FileScanner scanner;
    AuthenticatedUser admin,student,other;
    private final byte[] png={(byte)137,80,78,71,13,10,26,10,1};
    @BeforeEach void setup() {
        admin=user("admin","ADMIN");student=user("hocsinh","STUDENT");other=user("phuhuynh","PARENT");
        save(true,true,false,104857600);
        when(r2.presign(any(),eq("PUT"),anyString(),anyMap(),eq(300))).thenReturn(URI.create(StorageEndpointPolicy.DEFAULT_ENDPOINT+"/edulab/mock-url"));
        when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(png.length,"image/png","etag"));
        when(r2.read(any(),anyString(),anyLong())).thenReturn(png);
        when(scanner.scan(any())).thenReturn("CLEAN");
    }
    AuthenticatedUser user(String username,String role) {
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,username);
        return new AuthenticatedUser(id,username,username,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    void save(boolean enabled,boolean images,boolean pdf,long quota) {
        settings.save(admin,new StorageSettingsService.Input(enabled,StorageEndpointPolicy.DEFAULT_ENDPOINT,"edulab","auto","edulab","test-access-key","test-secret-not-real",5242880,10485760,quota,images,pdf));
    }
    @Test void encryptedSettingsNeverExposeKeysAndKeepOldFileLocations() {
        var view=settings.view(admin);assertTrue(view.keyConfigured());assertTrue(view.enabled());
        assertFalse(view.toString().contains("test-secret"));
        long first=settings.limits().profileId();
        String encrypted=jdbc.queryForObject("SELECT encrypted_secret_key FROM storage_profiles WHERE id=?",String.class,first);
        assertFalse(encrypted.contains("test-secret"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->settings.view(student));
        assertThrows(ResponseStatusException.class,()->settings.save(admin,new StorageSettingsService.Input(true,StorageEndpointPolicy.DEFAULT_ENDPOINT,"another-bucket","auto","edulab","","",5242880,10485760,104857600,true,false)));
        settings.save(admin,new StorageSettingsService.Input(true,StorageEndpointPolicy.DEFAULT_ENDPOINT,"another-bucket","auto","edulab","new-access-key","new-secret-not-real",5242880,10485760,104857600,true,false));
        assertNotEquals(first,settings.limits().profileId());assertEquals("edulab",settings.profile(first).bucket);
    }
    @Test void completeIsIdempotentUsesCopiedDestinationAndValidatesHash() {
        var ticket=uploads.presign(student,new DirectUploadService.Input("exercise.png","image/png",png.length));
        assertEquals("PUT",ticket.method());assertEquals("image/png",ticket.headers().get("Content-Type"));
        assertThrows(ResponseStatusException.class,()->uploads.complete(other,ticket.uploadId()));
        var status=uploads.complete(student,ticket.uploadId());assertEquals("CLEAN",status.scanStatus());assertNotNull(status.fileId());
        assertEquals(status.fileId(),uploads.complete(student,ticket.uploadId()).fileId());
        verify(r2,times(1)).copy(any(),contains("/staging/"),contains("/files/"));
        verify(r2).read(any(),contains("/files/"),eq((long)png.length));
        assertEquals(S3Signer.sha256(png),jdbc.queryForObject("SELECT sha256 FROM stored_files WHERE id=?",String.class,status.fileId()));
        var input=attachments.load(student,List.of(status.fileId()));assertEquals(1,input.size());assertTrue(input.getFirst().dataUrl().startsWith("data:image/png;base64,"));
        assertThrows(ResponseStatusException.class,()->attachments.load(other,List.of(status.fileId())));
        when(r2.read(any(),anyString(),anyLong())).thenReturn(new byte[]{1});
        assertThrows(ResponseStatusException.class,()->attachments.load(student,List.of(status.fileId())));
    }
    @Test void quotaIncludesOutstandingReservationsAndCancellationReleasesReservation() {
        save(true,false,false,1048576);
        long used=jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM stored_files WHERE uploaded_by=?",Long.class,student.id());
        long remaining=1048576-used;
        org.junit.jupiter.api.Assumptions.assumeTrue(remaining>0);
        var ticket=uploads.presign(student,new DirectUploadService.Input("big.png","image/png",remaining));
        assertEquals(429,assertThrows(ResponseStatusException.class,()->uploads.presign(student,new DirectUploadService.Input("next.png","image/png",1))).getStatusCode().value());
        assertEquals("CANCELLED",uploads.cancel(student,ticket.uploadId()).status());
        assertNotNull(uploads.presign(student,new DirectUploadService.Input("next.png","image/png",1)));
    }
    @Test void headMismatchAndFakeContentAreRejectedAndCannotBecomeAttachments() {
        var ticket=uploads.presign(student,new DirectUploadService.Input("a.png","image/png",png.length));
        when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(100,"image/png","etag"));
        assertEquals("REJECTED",uploads.complete(student,ticket.uploadId()).status());verify(r2,never()).copy(any(),anyString(),anyString());
        when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(png.length,"image/png","etag"));
        when(r2.read(any(),anyString(),anyLong())).thenReturn(new byte[png.length]);
        var fake=uploads.presign(student,new DirectUploadService.Input("fake.png","image/png",png.length));
        assertNull(uploads.complete(student,fake.uploadId()).fileId());
    }
    @Test void pendingScanIsAttachableButNeverSentToAiAndDownloadRightsFollowForum() {
        when(scanner.scan(any())).thenReturn("PENDING_SCAN");
        var ticket=uploads.presign(student,new DirectUploadService.Input("a.png","image/png",png.length));
        var status=uploads.complete(student,ticket.uploadId());assertEquals("PENDING_SCAN",status.scanStatus());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->attachments.load(student,List.of(status.fileId()))).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->files.image(student,status.fileId()));
        long category=((Number)forum.categories(student).getFirst().get("id")).longValue();
        long thread=forum.create(student,category,"File sample","Body",null,List.of(status.fileId()));
        assertTrue(forum.canDownload(other,status.fileId()));
        assertEquals(1,((List<?>)forum.thread(student,thread).get("files")).size());
        jdbc.update("UPDATE forum_threads SET status='HIDDEN' WHERE id=?",thread);
        assertFalse(forum.canDownload(other,status.fileId()));
        assertThrows(ResponseStatusException.class,()->forum.create(other,category,"Stolen","Body",null,List.of(status.fileId())));
    }
    @Test void cleanR2ImagePreviewFollowsAttachmentPermissionsAndRejectsHiddenThreads() throws Exception {
        var ticket=uploads.presign(student,new DirectUploadService.Input("preview.png","image/png",png.length));
        long file=uploads.complete(student,ticket.uploadId()).fileId();
        assertEquals("image/png",files.image(student,file).contentType());
        assertThrows(ResponseStatusException.class,()->files.image(other,file));
        long category=((Number)forum.categories(student).getFirst().get("id")).longValue();
        long thread=forum.create(student,category,"Image preview test","Body",null,List.of(file));
        assertEquals(1,((List<?>)forum.feed(other,category,null,10).items().getFirst().get("files")).size());
        assertArrayEquals(png,files.image(other,file).download().resource().getContentAsByteArray());
        forum.moderate(admin,thread,"HIDDEN");
        assertThrows(ResponseStatusException.class,()->files.image(other,file));
        assertNotNull(files.image(student,file),"Uploader retains their original download rights");
    }
    @Test void disabledStorageAndInvalidMetadataNeverIssueUrls() {
        save(false,false,false,104857600);
        assertEquals(503,assertThrows(ResponseStatusException.class,()->uploads.presign(student,new DirectUploadService.Input("a.png","image/png",png.length))).getStatusCode().value());
        save(true,false,false,104857600);
        assertThrows(ResponseStatusException.class,()->uploads.presign(student,new DirectUploadService.Input("../a.png","image/png",1)));
        assertThrows(ResponseStatusException.class,()->uploads.presign(student,new DirectUploadService.Input("a.png","application/pdf",1)));
        verify(r2,never()).presign(any(),anyString(),anyString(),anyMap(),anyInt());
    }
    @Test void infectedFilesCannotReachAiOrForumAndExpiredTicketsDoNotReadR2() {
        when(scanner.scan(any())).thenReturn("INFECTED");
        var ticket=uploads.presign(student,new DirectUploadService.Input("infected.png","image/png",png.length));
        var status=uploads.complete(student,ticket.uploadId());assertEquals("INFECTED",status.scanStatus());
        assertThrows(ResponseStatusException.class,()->attachments.load(student,List.of(status.fileId())));
        long category=((Number)forum.categories(student).getFirst().get("id")).longValue();
        assertThrows(ResponseStatusException.class,()->forum.create(student,category,"Unsafe","Body",null,List.of(status.fileId())));
        var expired=uploads.presign(student,new DirectUploadService.Input("expired.png","image/png",png.length));
        jdbc.update("UPDATE file_uploads SET expires_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 MINUTE) WHERE id=?",expired.uploadId());
        clearInvocations(r2);assertEquals("EXPIRED",uploads.complete(student,expired.uploadId()).status());verifyNoInteractions(r2);
    }
    @Test void pdfCapabilityMustBeExplicitlyEnabledAndTotalAiPayloadIsBounded() {
        byte[] pdf="%PDF-1.4\nfixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(pdf.length,"application/pdf","etag"));
        when(r2.read(any(),anyString(),anyLong())).thenReturn(pdf);
        var status=uploads.complete(student,uploads.presign(student,new DirectUploadService.Input("sample.pdf","application/pdf",pdf.length)).uploadId());
        assertThrows(ResponseStatusException.class,()->attachments.load(student,List.of(status.fileId())));
        save(true,true,true,104857600);
        assertEquals("application/pdf",attachments.load(student,List.of(status.fileId())).getFirst().contentType());
        assertThrows(ResponseStatusException.class,()->attachments.load(student,List.of(status.fileId(),status.fileId(),status.fileId(),status.fileId())));
    }
    private byte[] communityJpeg() throws Exception {
        var out=new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32,24,java.awt.image.BufferedImage.TYPE_INT_RGB),"jpeg",out));
        return out.toByteArray();
    }
    @Test void dedicatedCommunityUploadNeverCallsScannerAndCanPublishAndPreview() throws Exception {
        byte[] bytes=communityJpeg();String name="community-00000000-0000-4000-8000-000000000001.jpg";
        when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(bytes.length,"image/jpeg","etag"));
        when(r2.read(any(),anyString(),anyLong())).thenReturn(bytes);
        clearInvocations(scanner);
        var ticket=uploads.presignCommunity(student,new DirectUploadService.Input(name,"image/jpeg",bytes.length));
        var completed=uploads.complete(student,ticket.uploadId());
        assertEquals("PENDING_SCAN",completed.scanStatus());assertTrue(completed.communityReady());
        assertEquals("Ảnh đã sẵn sàng cho Cộng đồng.",completed.message());
        assertTrue(uploads.complete(student,ticket.uploadId()).communityReady());
        verify(scanner,never()).scan(any());
        long file=completed.fileId();
        assertArrayEquals(bytes,files.image(student,file).download().resource().getContentAsByteArray());
        assertThrows(ResponseStatusException.class,()->files.image(other,file));
        long post=social.create(student,"Ảnh đăng ngay",List.of(file));
        var image=(Map<?,?>)((List<?>)social.post(other,post).get("images")).getFirst();
        assertEquals(true,image.get("image_ready"));assertFalse(image.containsKey("storage_key"));
        assertArrayEquals(bytes,files.image(other,file).download().resource().getContentAsByteArray());
        long category=((Number)forum.categories(student).getFirst().get("id")).longValue();
        assertThrows(ResponseStatusException.class,()->forum.create(student,category,"No bypass","Body",null,List.of(file)));
        long thread=forum.create(student,category,"Normal thread","Body");
        assertThrows(ResponseStatusException.class,()->forum.reply(student,thread,"No bypass",List.of(file)));
        assertThrows(ResponseStatusException.class,()->attachments.load(student,List.of(file)));
        jdbc.update("UPDATE stored_files SET scan_status='CLEAN' WHERE id=?",file);
        assertThrows(ResponseStatusException.class,()->attachments.load(student,List.of(file)),"Even mislabelled community files cannot bypass AI scanning");
        jdbc.update("UPDATE stored_files SET scan_status='PENDING_SCAN' WHERE id=?",file);
        when(scanner.ready()).thenReturn(true);clearInvocations(scanner);uploads.scanPending();verify(scanner,never()).scan(any());
        social.moderate(admin,post,true);assertThrows(ResponseStatusException.class,()->files.image(other,file));
        assertNotNull(files.image(student,file));
        jdbc.update("UPDATE stored_files SET scan_status='INFECTED' WHERE id=?",file);
        assertThrows(ResponseStatusException.class,()->files.image(student,file));
    }
    @Test void genericUploadsWithCommunityFilenameStillRequireAntivirus() throws Exception {
        byte[] bytes=communityJpeg();String name="community-00000000-0000-4000-8000-000000000002.jpg";
        when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(bytes.length,"image/jpeg","etag"));
        when(r2.read(any(),anyString(),anyLong())).thenReturn(bytes);when(scanner.scan(any())).thenReturn("PENDING_SCAN");
        var ticket=uploads.presign(student,new DirectUploadService.Input(name,"image/jpeg",bytes.length));
        var completed=uploads.complete(student,ticket.uploadId());
        assertFalse(completed.communityReady());verify(scanner).scan(bytes);
        assertThrows(ResponseStatusException.class,()->social.create(student,"No filename bypass",List.of(completed.fileId())));
        assertThrows(ResponseStatusException.class,()->files.image(student,completed.fileId()));
    }
    @Test void malformedCommunityImagesAreRejectedBeforeScanOrPublication() {
        String name="community-00000000-0000-4000-8000-000000000003.jpg";
        byte[] fake={(byte)255,(byte)216,(byte)255,1};
        when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(fake.length,"image/jpeg","etag"));
        when(r2.read(any(),anyString(),anyLong())).thenReturn(fake);
        var ticket=uploads.presignCommunity(student,new DirectUploadService.Input(name,"image/jpeg",fake.length));
        var completed=uploads.complete(student,ticket.uploadId());assertEquals("REJECTED",completed.status());assertNull(completed.fileId());assertFalse(completed.communityReady());
        verify(scanner,never()).scan(any());
        assertThrows(ResponseStatusException.class,()->uploads.presignCommunity(student,new DirectUploadService.Input("one.png","image/png",9)));
        assertThrows(ResponseStatusException.class,()->uploads.presignCommunity(student,new DirectUploadService.Input(name,"image/jpeg",512001)));
    }
    @Test void uploadFailuresHaveSpecificMessagesAndNeverReadCancelledOrExpiredObjects() {
        var cancelled=uploads.presign(student,new DirectUploadService.Input("cancelled.png","image/png",png.length));
        assertTrue(uploads.cancel(student,cancelled.uploadId()).message().startsWith("Đã hủy upload."));
        clearInvocations(r2);
        assertEquals("CANCELLED",uploads.complete(student,cancelled.uploadId()).status());verifyNoInteractions(r2);
        var expired=uploads.presign(student,new DirectUploadService.Input("expired.png","image/png",png.length));
        jdbc.update("UPDATE file_uploads SET expires_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 MINUTE) WHERE id=?",expired.uploadId());
        clearInvocations(r2);
        assertTrue(uploads.complete(student,expired.uploadId()).message().startsWith("Lượt upload đã hết hạn."));verifyNoInteractions(r2);
        var invalid=uploads.presign(student,new DirectUploadService.Input("invalid.png","image/png",png.length));
        when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(100,"image/png","etag"));
        var result=uploads.complete(student,invalid.uploadId());assertEquals("REJECTED",result.status());
        assertTrue(result.message().startsWith("File không hợp lệ"));assertFalse(result.message().contains("hết hạn"));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="edulab.test.browserUploads",matches="true")
    void browserProducedJpegsCompletePublishAndPreviewWithoutMetadataExceptions() throws Exception {
        var directory=java.nio.file.Path.of("target/community-upload-fixtures");
        try(var paths=java.nio.file.Files.list(directory)) {
            var fixtures=paths.filter(path->path.getFileName().toString().matches("browser-[1-4]\\.jpg")).sorted().toList();
            assertEquals(4,fixtures.size(),"Run social-community-ui.cjs first");
            for(var path:fixtures) {
                byte[] bytes=java.nio.file.Files.readAllBytes(path);
                String name="community-"+java.util.UUID.randomUUID()+".jpg";
                when(r2.head(any(),anyString())).thenReturn(new R2Client.Metadata(bytes.length,"image/jpeg","etag"));
                when(r2.read(any(),anyString(),anyLong())).thenReturn(bytes);
                var ticket=uploads.presignCommunity(student,new DirectUploadService.Input(name,"image/jpeg",bytes.length));
                var completed=uploads.complete(student,ticket.uploadId());
                assertEquals("COMPLETED",completed.status());assertTrue(completed.communityReady());
                assertEquals(completed.fileId(),uploads.complete(student,ticket.uploadId()).fileId());
                long post=social.create(student,"Browser JPEG regression",List.of(completed.fileId()));
                assertEquals(true,((Map<?,?>)((List<?>)social.post(other,post).get("images")).getFirst()).get("image_ready"));
                assertArrayEquals(bytes,files.image(other,completed.fileId()).download().resource().getContentAsByteArray());
            }
            verify(scanner,never()).scan(any());
        }
    }
}