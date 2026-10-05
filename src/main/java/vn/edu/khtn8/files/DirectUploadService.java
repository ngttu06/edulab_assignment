package vn.edu.khtn8.files;

import jakarta.validation.constraints.*;
import java.sql.Statement;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Service
public class DirectUploadService {
    private final JdbcTemplate jdbc;
    private final StorageSettingsService settings;
    private final R2Client r2;
    private final FileScanner scanner;
    private final TransactionTemplate tx;
    public DirectUploadService(JdbcTemplate jdbc,StorageSettingsService settings,R2Client r2,FileScanner scanner,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.settings=settings;this.r2=r2;this.scanner=scanner;tx=new TransactionTemplate(manager);
    }
    public record Input(@NotBlank @Size(max=255) String filename,@NotBlank @Size(max=100) String contentType,@Positive long size) {}
    public record Ticket(String uploadId,String url,String method,Map<String,String> headers,int expiresInSeconds) {
        @Override public String toString() { return "UploadTicket[redacted]"; }
    }
    public record Status(String uploadId,Long fileId,String filename,String status,String scanStatus,String message) {}
    public Ticket presign(AuthenticatedUser user,Input input) {
        return tx.execute(transaction->{
            jdbc.queryForObject("SELECT id FROM users WHERE id=? AND enabled=TRUE FOR UPDATE",Long.class,user.id());
            var limits=settings.limits();
            if(!limits.enabled() || limits.profileId()==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Upload trực tiếp R2 đang tắt. Nhờ admin cấu hình storage.");
            FileValidator.validateMetadata(input.filename(),input.contentType(),input.size(),limits.maxImageBytes(),limits.maxPdfBytes());
            long used=jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM stored_files WHERE uploaded_by=?",Long.class,user.id());
            long reserved=jdbc.queryForObject("SELECT COALESCE(SUM(expected_bytes),0) FROM file_uploads WHERE user_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP(6)",Long.class,user.id());
            int active=jdbc.queryForObject("SELECT COUNT(*) FROM file_uploads WHERE user_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP(6)",Integer.class,user.id());
            int recent=jdbc.queryForObject("SELECT COUNT(*) FROM file_uploads WHERE user_id=? AND created_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR)",Integer.class,user.id());
            if(active>=6 || recent>=30 || input.size()>limits.userQuotaBytes()-used-reserved) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Đã đạt hạn mức storage hoặc số lượt upload. Hủy lượt chưa dùng hoặc thử lại sau.");
            var p=settings.profile(limits.profileId());String id=UUID.randomUUID().toString();
            String source=p.prefix+"/staging/"+id,target=p.prefix+"/files/"+id;
            jdbc.update("INSERT INTO file_uploads(id,user_id,profile_id,staging_key,final_key,filename,content_type,expected_bytes,expires_at) VALUES(?,?,?,?,?,?,?,?,DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 10 MINUTE))",id,user.id(),p.id,source,target,input.filename(),input.contentType(),input.size());
            var headers=Map.of("Content-Type",input.contentType());
            // Browser sets Content-Length for the Blob body; JS must not set this forbidden header.
            var signed=Map.of("Content-Type",input.contentType(),"Content-Length",String.valueOf(input.size()));
            return new Ticket(id,r2.presign(p,"PUT",source,signed,300).toString(),"PUT",headers,300);
        });
    }
    public Status status(AuthenticatedUser user,String id) { return status(row(user.id(),id,false)); }
    public Status complete(AuthenticatedUser user,String id) {
        return tx.execute(transaction->{
            // Same user lock order as presign; repeated complete calls serialize.
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user.id());
            var upload=row(user.id(),id,true);
            if(!"PENDING".equals(upload.get("status"))) return status(upload);
            if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT expires_at>CURRENT_TIMESTAMP(6) FROM file_uploads WHERE id=?",Boolean.class,id))) {
                jdbc.update("UPDATE file_uploads SET status='EXPIRED' WHERE id=?",id);return status(row(user.id(),id,false));
            }
            var p=settings.profile(((Number)upload.get("profile_id")).longValue());
            String source=(String)upload.get("staging_key"),target=(String)upload.get("final_key");
            long expected=((Number)upload.get("expected_bytes")).longValue();String mime=(String)upload.get("content_type");
            var head=r2.head(p,source);
            if(head.size()!=expected || !mime.equals(head.contentType())) return reject(upload,p);
            r2.copy(p,source,target);
            var destination=r2.head(p,target);
            if(destination.size()!=expected || !mime.equals(destination.contentType())) return reject(upload,p);
            byte[] bytes=r2.read(p,target,expected);
            if(bytes.length!=expected) return reject(upload,p);
            try { FileValidator.validate((String)upload.get("filename"),mime,bytes,5242880,10485760); }
            catch(ResponseStatusException ex) { return reject(upload,p); }
            String scan=scanner.scan(bytes);
            var generated=new GeneratedKeyHolder();
            jdbc.update(connection->{
                var s=connection.prepareStatement("INSERT INTO stored_files(storage_key,original_filename,content_type,size_bytes,sha256,scan_status,uploaded_by,storage_profile_id) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
                s.setString(1,target);s.setString(2,(String)upload.get("filename"));s.setString(3,mime);s.setLong(4,bytes.length);s.setString(5,S3Signer.sha256(bytes));s.setString(6,scan);s.setLong(7,user.id());s.setLong(8,p.id);return s;
            },generated);
            long fileId=Objects.requireNonNull(generated.getKey()).longValue();
            jdbc.update("UPDATE file_uploads SET status='COMPLETED',stored_file_id=? WHERE id=?",fileId,id);
            return status(row(user.id(),id,false));
        });
    }
    private Status reject(Map<String,Object> upload,StorageSettingsService.Profile p) {
        jdbc.update("UPDATE file_uploads SET status='REJECTED' WHERE id=?",upload.get("id"));
        safeDelete(p,(String)upload.get("final_key"));
        return status(row(((Number)upload.get("user_id")).longValue(),(String)upload.get("id"),false));
    }
    public Status cancel(AuthenticatedUser user,String id) {
        return tx.execute(transaction->{
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user.id());
            var upload=row(user.id(),id,true);
            if("PENDING".equals(upload.get("status"))) jdbc.update("UPDATE file_uploads SET status='CANCELLED' WHERE id=?",id);
            return status(row(user.id(),id,false));
        });
    }
    private Map<String,Object> row(long userId,String id,boolean lock) {
        if(id==null || !id.matches("[a-f0-9-]{36}")) throw notFound();
        var rows=jdbc.queryForList("SELECT * FROM file_uploads WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),id,userId);
        if(rows.isEmpty()) throw notFound();return rows.getFirst();
    }
    private Status status(Map<String,Object> upload) {
        Long fileId=upload.get("stored_file_id")==null?null:((Number)upload.get("stored_file_id")).longValue();
        String state=(String)upload.get("status"),scan=fileId==null?null:jdbc.queryForObject("SELECT scan_status FROM stored_files WHERE id=?",String.class,fileId);
        String message=fileId==null ? (state.equals("PENDING")?"Đang chờ upload trực tiếp R2.":"Upload hết hạn, bị hủy hoặc file không hợp lệ. Chọn file và thử lại.")
                : switch(scan) { case "CLEAN"->"File đã sẵn sàng.";case "PENDING_SCAN"->"File đã upload; đang cách ly chờ ClamAV. Chưa thể tải hoặc gửi AI.";default->"File bị từ chối; không thể tải hoặc gửi AI."; };
        return new Status((String)upload.get("id"),fileId,(String)upload.get("filename"),state,scan,message);
    }
    @Scheduled(fixedDelay=60000)
    public void cleanup() {
        tx.executeWithoutResult(transaction->jdbc.update("UPDATE file_uploads SET status='EXPIRED' WHERE status='PENDING' AND expires_at<CURRENT_TIMESTAMP(6)"));
        // Wait until well after PUT expiry before deleting staging: an old URL remains reusable until then.
        // Retain one day's tickets for hourly rate limiting and retry/status lookup.
        var rows=jdbc.queryForList("SELECT * FROM file_uploads WHERE status<>'PENDING' AND expires_at<DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 DAY) ORDER BY expires_at LIMIT 20");
        for(var upload:rows) {
            try {
                var p=settings.profile(((Number)upload.get("profile_id")).longValue());
                r2.delete(p,(String)upload.get("staging_key"));
                if(!"COMPLETED".equals(upload.get("status"))) r2.delete(p,(String)upload.get("final_key"));
                jdbc.update("DELETE FROM file_uploads WHERE id=? AND status<>'PENDING'",upload.get("id"));
            } catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("R2 temporary cleanup deferred"); }
        }
    }
    @Scheduled(fixedDelay=30000)
    public void scanPending() {
        if(!scanner.ready()) return;
        for(var file:jdbc.queryForList("SELECT * FROM stored_files WHERE storage_profile_id IS NOT NULL AND scan_status='PENDING_SCAN' ORDER BY id LIMIT 10")) {
            try {
                var p=settings.profile(((Number)file.get("storage_profile_id")).longValue());
                byte[] bytes=r2.read(p,(String)file.get("storage_key"),((Number)file.get("size_bytes")).longValue());
                String scan=S3Signer.sha256(bytes).equals(file.get("sha256"))?scanner.scan(bytes):"REJECTED";
                if(!scan.equals("PENDING_SCAN")) jdbc.update("UPDATE stored_files SET scan_status=? WHERE id=? AND scan_status='PENDING_SCAN'",scan,file.get("id"));
            } catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("R2 file scan deferred"); }
        }
    }
    private void safeDelete(StorageSettingsService.Profile p,String key) {
        try { r2.delete(p,key); } catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("R2 rejected object cleanup deferred"); }
    }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lượt upload của tài khoản."); }
}