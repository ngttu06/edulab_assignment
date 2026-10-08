package vn.edu.khtn8.community;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.files.FileStorage;
import vn.edu.khtn8.files.S3Signer;
import vn.edu.khtn8.files.StorageSettingsService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
@Transactional
class SocialCommunityMysqlTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));
        registry.add("spring.datasource.username",()->"edulab_test");
        registry.add("spring.datasource.password",()->"local-test-only-password");
        registry.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1kZXBsb3ltZW50LXNlY3JldC1rZXktMzJ4");
        registry.add("edulab.upload.root",()->{try{return java.nio.file.Files.createTempDirectory("social-community-test-").toString();}catch(Exception ex){throw new IllegalStateException(ex);}});
        registry.add("EDULAB_STORAGE_ENCRYPTION_KEY",()->java.util.Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired SocialCommunityService social;
    @Autowired JdbcTemplate jdbc;
    @Autowired FileStorage storage;
    @Autowired StorageSettingsService settings;
    AuthenticatedUser user(String name,String role) {
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
        return new AuthenticatedUser(id,name,name,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    long number(Object value) {return ((Number)value).longValue();}

    @Test void socialPostsAreIndependentFromQuizAndForumAndCursorIsStable() {
        var author=user("hocsinh","STUDENT");var other=user("phuhuynh","PARENT");
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM forum_threads",Long.class);
        var a=social.create(author,"Một ngày vui",List.of());var b=social.create(other,"Chào cả nhà",List.of());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM forum_threads",Long.class));
        assertEquals(List.of(b),social.feed(author,false,null,1).items().stream().map(i->number(i.get("id"))).toList());
        assertEquals(a,number(social.feed(author,false,b,10).items().getFirst().get("id")));
        assertEquals(a,number(social.feed(author,true,null,10).items().getFirst().get("id")));
        assertFalse(social.feed(other,true,null,10).items().stream().anyMatch(i->number(i.get("id"))==a));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->social.feed(author,false,-1L,10)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->social.create(author,"",List.of()));
        assertThrows(AccessDeniedException.class,()->social.edit(other,a,"Không được sửa"));
        social.edit(author,a,"Đã sửa");assertEquals("Đã sửa",social.post(author,a).get("body"));
        social.remove(author,a);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->social.post(other,a)).getStatusCode().value());
    }
    @Test void likesCommentsReportsAndModerationRespectVisibility() {
        var author=user("hocsinh","STUDENT");var other=user("phuhuynh","PARENT");var admin=user("admin","ADMIN");
        var id=social.create(author,"Xin chào",List.of());
        assertEquals(1,social.like(other,id,true).count());assertEquals(1,social.like(other,id,true).count());
        assertEquals(0,social.like(other,id,false).count());
        long comment=social.comment(other,id,"Chúc vui!");assertEquals(1,social.comments(author,id,null,10).items().size());
        assertThrows(AccessDeniedException.class,()->social.deleteComment(author,id,comment));
        social.report(other,id,"Nội dung cần kiểm tra");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM community_reports WHERE post_id=?",Long.class,id));
        social.deleteComment(other,id,comment);assertTrue(social.comments(author,id,null,10).items().isEmpty());
        assertThrows(AccessDeniedException.class,()->social.moderate(other,id,true));
        social.moderate(admin,id,true);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->social.post(other,id)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->social.comment(other,id,"No"));
        social.moderate(admin,id,false);assertNotNull(social.post(other,id));
        social.resolveReports(admin,id);
        assertTrue(social.reports(admin).stream().noneMatch(r->number(r.get("post_id"))==id));
    }
    @Test void onlySmallCleanMetadataFreeJpegsBelongToPublishedPosts() throws Exception {
        var author=user("hocsinh","STUDENT");var other=user("phuhuynh","PARENT");
        var image=new BufferedImage(32,24,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.setColor(Color.ORANGE);graphics.fillRect(0,0,32,24);graphics.dispose();
        var out=new ByteArrayOutputStream();assertTrue(ImageIO.write(image,"jpeg",out));byte[] bytes=out.toByteArray();
        assertFalse(SocialCommunityService.hasExif(bytes));
        // The storage key is a local test fixture. The file is never exposed through the web.
        String key=storage.store(bytes);
        try {
            jdbc.update("INSERT INTO stored_files(storage_key,original_filename,content_type,size_bytes,sha256,scan_status,uploaded_by) VALUES(?,?,?,?,?,?,?)",
                    key,"community-00000000-0000-4000-8000-000000000001.jpg","image/jpeg",bytes.length,S3Signer.sha256(bytes),"CLEAN",author.id());
            long fileId=jdbc.queryForObject("SELECT id FROM stored_files WHERE storage_key=?",Long.class,key);
            var admin=user("admin","ADMIN");
            settings.save(admin,new StorageSettingsService.Input(true,"https://c9da795e23235545fa54e8c7ee61d3cf.r2.cloudflarestorage.com","edulab","auto","edulab","test-access-key","test-secret-not-real",5242880,10485760,104857600,false,false));
            var profileId=settings.limits().profileId();
            String ticket=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO file_uploads(id,user_id,profile_id,staging_key,final_key,filename,content_type,expected_bytes,status,stored_file_id,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY))",
                    ticket,author.id(),profileId,"edulab/staging/"+ticket,"edulab/files/"+ticket,"community-00000000-0000-4000-8000-000000000001.jpg","image/jpeg",bytes.length,"COMPLETED",fileId);
            assertThrows(ResponseStatusException.class,()->social.create(other,"Ảnh người khác",List.of(fileId)));
            long post=social.create(author,"",List.of(fileId));
            assertTrue(social.canDownload(other,fileId));
            assertEquals(1,((List<?>)social.post(other,post).get("images")).size());
            assertThrows(ResponseStatusException.class,()->social.create(author,"trùng ảnh",List.of(fileId)));
            social.remove(author,post);assertFalse(social.canDownload(other,fileId));
        } finally {storage.delete(key);}
    }
    @Test void reactionsArePersistentIdempotentAndPreserveLegacyLikes() {
        var author=user("hocsinh","STUDENT");var other=user("phuhuynh","PARENT");var admin=user("admin","ADMIN");
        long id=social.create(author,"Bảy cảm xúc",List.of());
        // Existing insert shape still defaults to LIKE after the additive migration.
        jdbc.update("INSERT INTO community_likes(post_id,user_id) VALUES(?,?)",id,author.id());
        for(var type:SocialCommunityService.ReactionType.values()) {
            var result=social.react(other,id,type);assertEquals(type,result.reaction());assertEquals(2,result.count());
            assertEquals(2,social.react(other,id,type).count());
            assertEquals(type.name(),social.post(other,id).get("reaction"));
            assertEquals(type.name(),social.feed(other,false,null,20).items().stream().filter(p->number(p.get("id"))==id).findFirst().orElseThrow().get("reaction"));
            assertEquals(type==SocialCommunityService.ReactionType.LIKE?2L:1L,result.counts().get(type.name()));
        }
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM community_likes WHERE post_id=?",Long.class,id));
        assertEquals(1,social.react(other,id,null).count());assertEquals(1,social.react(other,id,null).count());
        assertEquals("",social.post(other,id).get("reaction"));
        assertEquals("LIKE",social.post(author,id).get("reaction"));
        assertEquals(2,social.like(other,id,true).count());
        assertEquals("LIKE",social.post(other,id).get("reaction"));
        social.moderate(admin,id,true);
        assertThrows(ResponseStatusException.class,()->social.react(other,id,SocialCommunityService.ReactionType.LOVE));
        assertThrows(ResponseStatusException.class,()->social.react(other,id,null));
        social.moderate(admin,id,false);social.remove(author,id);
        assertThrows(ResponseStatusException.class,()->social.react(other,id,SocialCommunityService.ReactionType.LOVE));
    }
    @Test void commentEditingIsOwnerOnlyAndCursorSurvivesNewComment() {
        var author=user("hocsinh","STUDENT");var other=user("phuhuynh","PARENT");var admin=user("admin","ADMIN");
        long id=social.create(author,"Bình luận",List.of()),elsewhere=social.create(author,"Bài khác",List.of());
        long first=social.comment(other,id,"Đầu tiên");
        for(int i=0;i<11;i++) social.comment(author,id,"Bình luận "+i);
        var page=social.comments(other,id,null,10);assertTrue(page.hasMore());
        long newest=social.comment(other,id,"<script>alert(1)</script>");
        assertEquals(newest,number(social.commentView(other,id,newest).get("id")));
        assertEquals(true,social.commentView(other,id,newest).get("mine"));
        assertEquals(3,social.comments(other,id,page.nextCursor(),10).items().size());
        assertThrows(AccessDeniedException.class,()->social.editComment(author,id,first,"Không phải tác giả"));
        assertThrows(AccessDeniedException.class,()->social.editComment(admin,id,first,"Admin không được sửa"));
        assertThrows(ResponseStatusException.class,()->social.editComment(other,elsewhere,first,"Sai bài"));
        assertThrows(ResponseStatusException.class,()->social.editComment(other,id,first," "));
        assertThrows(ResponseStatusException.class,()->social.editComment(other,id,first,"x".repeat(1001)));
        social.editComment(other,id,first,"  Đã sửa  ");assertEquals("Đã sửa",social.commentView(other,id,first).get("body"));
        social.deleteComment(admin,id,first);assertThrows(ResponseStatusException.class,()->social.editComment(other,id,first,"Đã xóa"));
        social.moderate(admin,id,true);assertThrows(ResponseStatusException.class,()->social.editComment(other,id,newest,"Đã ẩn"));
    }
}