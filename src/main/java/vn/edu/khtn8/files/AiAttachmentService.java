package vn.edu.khtn8.files;

import java.util.List;
import java.util.ArrayList;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.ai.AiProvider;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Service
public class AiAttachmentService {
    private final JdbcTemplate jdbc;
    private final StorageSettingsService settings;
    private final R2Client r2;
    private final FileStorage local;
    public AiAttachmentService(JdbcTemplate jdbc,StorageSettingsService settings,R2Client r2,FileStorage local) { this.jdbc=jdbc;this.settings=settings;this.r2=r2;this.local=local; }
    public List<AiProvider.Attachment> load(AuthenticatedUser user,List<Long> ids) {
        if(ids==null || ids.size()>3 || ids.stream().anyMatch(id->id==null || id<=0)) throw StorageSettingsService.bad("Tối đa 3 file hợp lệ.");
        var limits=settings.limits();var result=new ArrayList<AiProvider.Attachment>();long total=0;
        for(long id:ids.stream().distinct().toList()) {
            var files=jdbc.queryForList("SELECT * FROM stored_files WHERE id=? AND uploaded_by=?",id,user.id());
            if(files.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy file của tài khoản.");
            var file=files.getFirst();
            if(!"CLEAN".equals(file.get("scan_status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"File chưa quét đạt yêu cầu. Chờ quét xong rồi gửi AI lại.");
            String mime=(String)file.get("content_type");boolean pdf=mime.equals("application/pdf");
            if(pdf?!limits.aiPdfEnabled():!limits.aiImagesEnabled()) throw StorageSettingsService.bad(pdf?"Admin chưa bật khả năng đọc PDF của model. Upload vẫn được lưu; hãy gửi ảnh hoặc hỏi giáo viên.":"Admin chưa bật khả năng đọc ảnh của model AI.");
            long size=((Number)file.get("size_bytes")).longValue();total+=size;
            if(total>15*1024*1024) throw StorageSettingsService.bad("Tổng file gửi AI tối đa 15 MB.");
            byte[] bytes;
            try {
                if(file.get("storage_profile_id")!=null) bytes=r2.read(settings.profile(((Number)file.get("storage_profile_id")).longValue()),(String)file.get("storage_key"),size);
                else try(var stream=local.load((String)file.get("storage_key")).getInputStream()) { bytes=stream.readNBytes((int)Math.min(size,10*1024*1024)+1); }
            } catch(java.io.IOException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không đọc được file đính kèm."); }
            if(bytes.length!=size || !S3Signer.sha256(bytes).equals(file.get("sha256"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Nội dung file đã thay đổi; không gửi AI.");
            result.add(new AiProvider.Attachment((String)file.get("original_filename"),mime,"data:"+mime+";base64,"+Base64.getEncoder().encodeToString(bytes)));
        }
        return List.copyOf(result);
    }
}