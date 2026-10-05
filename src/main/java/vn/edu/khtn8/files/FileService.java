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
    private final StorageSettingsService storageSettings;
    private final R2Client r2;
    public FileService(JdbcTemplate jdbc, FileStorage storage, ForumService forum,StorageSettingsService storageSettings,R2Client r2) {
        this.jdbc = jdbc; this.storage = storage; this.forum = forum;this.storageSettings=storageSettings;this.r2=r2;
    }
    public record Download(Resource resource, String filename, long length) { }

    public Download download(AuthenticatedUser user, long id) throws IOException {
        var rows = jdbc.queryForList("SELECT * FROM stored_files WHERE id=? AND scan_status='CLEAN'", id);
        if (rows.isEmpty()) throw notFound();
        var file = rows.getFirst();
        if (((Number)file.get("uploaded_by")).longValue() != user.id() && !forum.canDownload(user, id)) throw notFound();
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
