package vn.edu.khtn8.files;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.config.AppProperties;
import vn.edu.khtn8.community.ForumService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Service
public class FileService {
    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final AppProperties properties;
    private final ForumService forum;
    public FileService(JdbcTemplate jdbc, FileStorage storage, AppProperties properties, ForumService forum) {
        this.jdbc = jdbc; this.storage = storage; this.properties = properties; this.forum = forum;
    }
    public record Download(Resource resource, String filename, long length) { }

    @Transactional(rollbackFor = IOException.class)
    public Map<String, Object> upload(AuthenticatedUser user, MultipartFile file) throws IOException {
        var config = properties.getUpload();
        long max = Math.max(config.getMaxImageBytes(), config.getMaxPdfBytes());
        if (file.getSize() > max) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp quá lớn.");
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user.id());
        long used = jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM stored_files WHERE uploaded_by=?", Long.class, user.id());
        if (used + file.getSize() > 100L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Đã đạt hạn mức lưu trữ 100 MB của tài khoản.");
        byte[] bytes;
        try (var stream = file.getInputStream()) { bytes = stream.readNBytes((int)max + 1); }
        String mime = FileValidator.validate(file.getOriginalFilename(), file.getContentType(), bytes, config.getMaxImageBytes(), config.getMaxPdfBytes());
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        String key = storage.store(bytes);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { storage.delete(key); }
                    catch (IOException ex) { org.slf4j.LoggerFactory.getLogger(FileService.class).warn("Failed to clean rolled-back upload"); }
                }
            }
        });
        String scanStatus = config.isDevScanBypass() ? "CLEAN" : "PENDING_SCAN";
        GeneratedKeyHolder generated = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO stored_files(storage_key,original_filename,content_type,size_bytes,sha256,scan_status,uploaded_by) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, key); statement.setString(2, file.getOriginalFilename()); statement.setString(3, mime);
            statement.setLong(4, bytes.length); statement.setString(5, digest); statement.setString(6, scanStatus); statement.setLong(7, user.id());
            return statement;
        }, generated);
        return Map.of("id", java.util.Objects.requireNonNull(generated.getKey()).longValue(), "scanStatus", scanStatus,
                "message", config.isDevScanBypass() ? "Chế độ local: bỏ qua quét mã độc, chỉ dùng tệp thử nghiệm tin cậy." : "Đang cách ly chờ quét; chưa thể tải xuống.");
    }
    public Download download(AuthenticatedUser user, long id) throws IOException {
        var rows = jdbc.queryForList("SELECT * FROM stored_files WHERE id=? AND scan_status='CLEAN'", id);
        if (rows.isEmpty()) throw notFound();
        var file = rows.getFirst();
        if (((Number)file.get("uploaded_by")).longValue() != user.id() && !forum.canDownload(user, id)) throw notFound();
        return new Download(storage.load((String)file.get("storage_key")), (String)file.get("original_filename"), ((Number)file.get("size_bytes")).longValue());
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tệp không khả dụng hoặc bạn không có quyền xem."); }
}
