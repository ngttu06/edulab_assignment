package vn.edu.khtn8.files;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.community.ForumService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Service
public class FileService {
    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final ForumService forum;
    private final vn.edu.khtn8.community.SocialCommunityService social;
    private final StorageSettingsService storageSettings;
    private final R2Client r2;
    public FileService(JdbcTemplate jdbc, FileStorage storage, ForumService forum,StorageSettingsService storageSettings,R2Client r2,
                       vn.edu.khtn8.community.SocialCommunityService social) {
        this.jdbc = jdbc; this.storage = storage; this.forum = forum;this.storageSettings=storageSettings;this.r2=r2;this.social=social;
    }
    public record Download(Resource resource, String filename, long length) { }
    public record ImagePreview(Download download, String contentType) { }

    public ImagePreview image(AuthenticatedUser user, long id) throws IOException {
        var rows = jdbc.queryForList("SELECT original_filename,content_type,size_bytes,sha256 FROM stored_files WHERE id=? AND scan_status='CLEAN'", id);
        if (rows.isEmpty()) throw notFound();
        var file = rows.getFirst();
        String mime = (String)file.get("content_type");
        long length = ((Number)file.get("size_bytes")).longValue();
        if (!java.util.List.of("image/jpeg", "image/png", "image/webp").contains(mime) || length <= 0 || length > 5242880) throw notFound();
        // Reuse download authorization; never expose an object-store URL or unscanned file.
        var download = download(user, id);
        byte[] bytes;
        try (var input = download.resource().getInputStream()) { bytes = input.readNBytes((int)length + 1); }
        if (bytes.length != length || !S3Signer.sha256(bytes).equals(file.get("sha256"))) throw notFound();
        try { FileValidator.validate((String)file.get("original_filename"), mime, bytes, 5242880, 10485760); }
        catch (ResponseStatusException invalid) { throw notFound(); }
        return new ImagePreview(new Download(new org.springframework.core.io.ByteArrayResource(bytes), download.filename(), length), mime);
    }

    public Download download(AuthenticatedUser user, long id) throws IOException {
        var rows = jdbc.queryForList("SELECT * FROM stored_files WHERE id=? AND scan_status='CLEAN'", id);
        if (rows.isEmpty()) throw notFound();
        var file = rows.getFirst();
        if (((Number)file.get("uploaded_by")).longValue() != user.id() && !vn.edu.khtn8.school.SchoolService.hasRole(user,"ADMIN") && !forum.canDownload(user, id) && !social.canDownload(user,id)) throw notFound();
        long length=((Number)file.get("size_bytes")).longValue();
        Resource resource;
        if(file.get("storage_profile_id")==null) resource=storage.load((String)file.get("storage_key"));
        else {
            byte[] bytes=r2.read(storageSettings.profile(((Number)file.get("storage_profile_id")).longValue()),(String)file.get("storage_key"),length);
            if(bytes.length!=length || !S3Signer.sha256(bytes).equals(file.get("sha256"))) throw notFound();
            resource=new org.springframework.core.io.ByteArrayResource(bytes);
        }
        return new Download(resource, (String)file.get("original_filename"), length);
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tệp không khả dụng hoặc bạn không có quyền xem."); }
}
