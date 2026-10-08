package vn.edu.khtn8.files;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.community.ForumService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImagePreviewTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final FileStorage storage=mock(FileStorage.class);
    final ForumService forum=mock(ForumService.class);
    final FileService files=new FileService(jdbc,storage,forum,mock(StorageSettingsService.class),mock(R2Client.class),mock(vn.edu.khtn8.community.SocialCommunityService.class));
    final AuthenticatedUser owner=new AuthenticatedUser(1L,"owner","Owner","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    final AuthenticatedUser other=new AuthenticatedUser(2L,"other","Other","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    final byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jX1sAAAAASUVORK5CYII=");
    void fixture(String name,String mime,byte[] bytes) throws Exception {
        when(jdbc.queryForList("SELECT original_filename,content_type,size_bytes,sha256 FROM stored_files WHERE id=? AND scan_status='CLEAN'",7L))
                .thenReturn(List.of(Map.of("original_filename",name,"content_type",mime,"size_bytes",(long)bytes.length,"sha256",S3Signer.sha256(bytes))));
        when(jdbc.queryForList("SELECT * FROM stored_files WHERE id=? AND scan_status='CLEAN'",7L))
                .thenReturn(List.of(Map.of("original_filename",name,"content_type",mime,"size_bytes",(long)bytes.length,"sha256",S3Signer.sha256(bytes),"uploaded_by",1L,"storage_key","local")));
        when(storage.load("local")).thenReturn(new ByteArrayResource(bytes));
    }
    @Test void cleanImageHasInlineTypeButDownloadStillForcesAttachment() throws Exception {
        fixture("one.png","image/png",png);
        var controller=new FileController(files,mock(DirectUploadService.class));
        var preview=controller.image(7L,owner);
        assertEquals("image/png",preview.getHeaders().getContentType().toString());
        assertTrue(preview.getHeaders().getFirst("Content-Disposition").startsWith("inline;"));
        assertEquals("private, no-store",preview.getHeaders().getFirst("Cache-Control"));
        assertEquals("nosniff",preview.getHeaders().getFirst("X-Content-Type-Options"));
        assertArrayEquals(png,preview.getBody().getContentAsByteArray());
        assertTrue(controller.download(7L,owner).getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));
    }
    @Test void previewAuthorizationFollowsForumAndDeniesRevokedAccess() throws Exception {
        fixture("one.png","image/png",png);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->files.image(other,7)).getStatusCode().value());
        when(forum.canDownload(other,7)).thenReturn(true);
        assertNotNull(files.image(other,7));
        when(forum.canDownload(other,7)).thenReturn(false);
        assertThrows(ResponseStatusException.class,()->files.image(other,7));
    }
    @Test void pdfSvgUnscannedAndForgedImagesNeverRenderInline() throws Exception {
        fixture("one.pdf","application/pdf","%PDF-1.4".getBytes());assertThrows(ResponseStatusException.class,()->files.image(owner,7));
        fixture("one.svg","image/svg+xml","<svg/>".getBytes());assertThrows(ResponseStatusException.class,()->files.image(owner,7));
        fixture("one.png","image/png","not an image".getBytes());assertThrows(ResponseStatusException.class,()->files.image(owner,7));
        when(jdbc.queryForList("SELECT original_filename,content_type,size_bytes,sha256 FROM stored_files WHERE id=? AND scan_status='CLEAN'",7L)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class,()->files.image(owner,7));
    }
    @Test void tamperedStoredBytesAreRejected() throws Exception {
        fixture("one.png","image/png",png);
        when(storage.load("local")).thenReturn(new ByteArrayResource(new byte[png.length]));
        assertThrows(ResponseStatusException.class,()->files.image(owner,7));
    }
    @Test void allowedJpegAndWebpSignaturesUseTheirVerifiedMimeTypes() throws Exception {
        fixture("one.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,1});
        assertEquals("image/jpeg",files.image(owner,7).contentType());
        fixture("one.webp","image/webp","RIFF0000WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("image/webp",files.image(owner,7).contentType());
    }
}