package vn.edu.khtn8.assessment;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.ai.*;
import vn.edu.khtn8.files.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Service
public class QuizImportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final FileScanner scanner;
    private final StorageSettingsService storage;
    private final AiSettingsService settings;
    private final AiProvider provider;
    private final AiQuotaService quota;
    public QuizImportService(JdbcTemplate jdbc,ObjectMapper json,FileScanner scanner,StorageSettingsService storage,AiSettingsService settings,AiProvider provider,AiQuotaService quota) {
        this.jdbc=jdbc;this.json=json;this.scanner=scanner;this.storage=storage;this.settings=settings;this.provider=provider;this.quota=quota;
    }
    public Map<String,Object> extract(AuthenticatedUser user,MultipartFile file) throws java.io.IOException {
        SchoolService.require(user,"TEACHER","ADMIN");requireAi();
        String name=file.getOriginalFilename();
        if(name==null || name.length()>255 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) throw AssessmentReviewService.bad("Tên tệp không hợp lệ.");
        boolean pdf=name.toLowerCase(Locale.ROOT).endsWith(".pdf"),word=name.toLowerCase(Locale.ROOT).endsWith(".docx");
        if(!pdf && !word) throw AssessmentReviewService.bad("Chỉ nhận PDF hoặc Word .docx; hãy chuyển .doc cũ sang .docx/PDF.");
        if(file.isEmpty() || file.getSize()>10*1024*1024) throw AssessmentReviewService.bad("Tệp tối đa 10 MB và không được trống.");
        byte[] bytes=file.getBytes();
        if(pdf) FileValidator.validate(name,"application/pdf",bytes,5*1024*1024,storage.limits().maxPdfBytes());
        String scan=scanner.scan(bytes);
        if(!"CLEAN".equals(scan)) throw new ResponseStatusException(HttpStatus.CONFLICT,"INFECTED".equals(scan)?"Tệp bị từ chối do kiểm tra an toàn.":"Scanner chưa sẵn sàng hoặc chưa quét đạt yêu cầu. Tệp chưa được gửi AI; hãy thử lại khi admin cấu hình ClamAV.");
        String context=word?QuizDocumentReader.docx(bytes):"Đọc tất cả trang trong PDF đính kèm. Nếu không đọc đủ, trả complete=false.";
        if(pdf && !storage.limits().aiPdfEnabled()) throw AssessmentReviewService.bad("Admin chưa bật khả năng đọc PDF của model AI.");
        var attachments=pdf?List.of(new AiProvider.Attachment(name,"application/pdf","data:application/pdf;base64,"+Base64.getEncoder().encodeToString(bytes))):List.<AiProvider.Attachment>of();
        String prompt="""
                Trích xuất TOÀN BỘ câu trắc nghiệm một đáp án trong tài liệu, KHÔNG tự tạo đáp án bị thiếu.
                Chỉ trả JSON: {"complete":true,"warnings":[],"questions":[{"content":"...","options":["...","..."],"correctIndex":null,"explanation":"","points":1}]}.
                correctIndex là chỉ số 0..3 CHỈ khi tài liệu có đáp án rõ ràng; nếu không có hoặc mơ hồ dùng null.
                Giữ thứ tự, 2–4 lựa chọn, không tự bịa hoặc thêm câu. Giải thích có thể trống. points là trọng số, mặc định 1.
                Tối đa 15 câu trong một lần nhập để tránh cắt đầu ra. Nếu tài liệu có hơn 15 câu, không hỗ trợ loại câu,
                hình/công thức không đọc được, thiếu trang hoặc không đọc trọn vẹn thì complete=false và ghi warnings; không bỏ qua câu.
                """;
        JsonNode root=parse(call(user,new AiProvider.AiQuestion(prompt,context,"QUIZ_IMPORT",List.of(),attachments)));
        if(!root.path("complete").isBoolean() || !root.path("complete").asBoolean()) throw AssessmentReviewService.bad("AI chưa đọc đầy đủ đề. Hãy kiểm tra tệp, chia đề tối đa 15 câu/lần hoặc nhập CSV; chưa có câu hỏi nào được thay thế.");
        JsonNode questions=validateQuestions(root.path("questions"));
        for(var q:questions) ((com.fasterxml.jackson.databind.node.ObjectNode)q).remove(List.of("aiGenerated","reviewed"));
        JsonNode warnings=root.path("warnings");if(!warnings.isArray() || warnings.size()>20) throw AssessmentReviewService.bad("Cảnh báo trích xuất không hợp lệ.");
        for(var warning:warnings) if(!warning.isTextual() || warning.asText().length()>500) throw AssessmentReviewService.bad("Cảnh báo trích xuất không hợp lệ.");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO quiz_import_sessions(id,teacher_id,filename,questions_json,warnings_json) VALUES(?,?,?,?,?)",id,user.id(),name,questions.toString(),warnings.toString());
        return view(user,id);
    }
    public Map<String,Object> view(AuthenticatedUser user,String id) {
        var session=owned(user,id);JsonNode questions=parse((String)session.get("questions_json"));
        return Map.of("id",id,"questions",questions,"warnings",parse((String)session.get("warnings_json")),"filename",session.get("filename"),"missingCount",missing(questions).size());
    }
    public Map<String,Object> generate(AuthenticatedUser user,String id) {
        var session=owned(user,id);requireAi();
        var questions=validateQuestions(parse((String)session.get("questions_json"))).deepCopy();
        var missing=missing(questions);if(missing.isEmpty()) return view(user,id);
        int claimed=jdbc.update("UPDATE quiz_import_sessions SET generation_status='PENDING' WHERE id=? AND teacher_id=? AND generation_status='READY'",id,user.id());
        if(claimed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"AI đang tạo đáp án cho phiên nhập này. Nếu phiên bị gián đoạn hãy nhập tệp lại.");
        try {
            for(int start=0;start<missing.size();) {
                var batch=new ArrayList<Integer>();var input=json.createArrayNode();
                while(start<missing.size() && batch.size()<5) {
                    int index=missing.get(start);var q=questions.get(index);var item=json.createObjectNode();item.put("index",index);item.set("content",q.get("content"));item.set("options",q.get("options"));
                    if(input.toString().length()+item.toString().length()>15000) {
                        if(batch.isEmpty()) throw AssessmentReviewService.bad("Câu hỏi quá dài để AI đọc đầy đủ. Hãy tự nhập đáp án.");
                        break;
                    }
                    batch.add(index);input.add(item);start++;
                }
                var answer=parse(call(user,new AiProvider.AiQuestion("Giáo viên đã đồng ý đề xuất đáp án cho các câu thiếu. Chỉ trả JSON {\"answers\":[{\"index\":0,\"correctIndex\":0,\"explanation\":\"...\"}]}. Giữ index đầu vào, một đáp án 0..3 mỗi câu, giải thích ngắn tiếng Việt. Nếu không đủ dữ kiện, dùng correctIndex:null và giải thích lý do, không đoán.",input.toString(),"QUIZ_IMPORT"))).path("answers");
                if(!answer.isArray() || answer.size()!=batch.size()) throw AssessmentReviewService.bad("AI trả thiếu đáp án. Không thay đổi dữ liệu đang soạn.");
                var seen=new HashSet<Integer>();
                for(var a:answer) {
                    if(!a.path("index").isIntegralNumber() || !a.path("index").canConvertToInt() || !batch.contains(a.path("index").asInt()) || !seen.add(a.path("index").asInt())) throw AssessmentReviewService.bad("AI trả chỉ số câu hỏi không hợp lệ.");
                    int index=a.get("index").asInt();var q=(com.fasterxml.jackson.databind.node.ObjectNode)questions.get(index);
                    if(!a.has("correctIndex") || !(a.get("correctIndex").isNull() || a.get("correctIndex").isIntegralNumber() && a.get("correctIndex").canConvertToInt() && a.get("correctIndex").asInt()>=0 && a.get("correctIndex").asInt()<q.get("options").size())) throw AssessmentReviewService.bad("AI trả đáp án không hợp lệ.");
                    if(!a.path("explanation").isTextual() || a.get("explanation").asText().length()>10000) throw AssessmentReviewService.bad("AI trả giải thích không hợp lệ.");
                    q.set("correctIndex",a.get("correctIndex"));q.set("explanation",a.get("explanation"));q.put("aiGenerated",!a.get("correctIndex").isNull());q.put("reviewed",false);
                }
            }
            jdbc.update("UPDATE quiz_import_sessions SET questions_json=?,generation_status='READY' WHERE id=? AND teacher_id=?",questions.toString(),id,user.id());
            return view(user,id);
        } finally {jdbc.update("UPDATE quiz_import_sessions SET generation_status='READY' WHERE id=? AND teacher_id=?",id,user.id());}
    }
    private Map<String,Object> owned(AuthenticatedUser user,String id) {
        SchoolService.require(user,"TEACHER","ADMIN");
        var rows=jdbc.queryForList("SELECT * FROM quiz_import_sessions WHERE id=? AND teacher_id=? AND created_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 DAY)",id,user.id());
        if(rows.isEmpty()) throw AssessmentReviewService.notFound();return rows.getFirst();
    }
    private void requireAi() {
        if(!settings.enabled() || provider.providerName().equals("stub-local")) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Nhập đề cần model AI thật đang bật. AI mẫu không đọc đề; bạn vẫn có thể nhập CSV hoặc soạn thủ công.");
    }
    private String call(AuthenticatedUser user,AiProvider.AiQuestion question) {
        long request=quota.reserve(user.id(),provider.providerName(),"QUIZ_IMPORT");
        try {var answer=provider.answer(question).text();quota.finish(request,true);return answer;}
        catch(RuntimeException ex) {quota.finish(request,false);throw ex;}
    }
    JsonNode parse(String text) {
        try {
            String value=text.trim();if(value.startsWith("```")) value=value.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
            JsonNode node=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(value);
            if(node==null) throw new IllegalArgumentException("Empty JSON");return node;
        } catch(Exception ex) {throw AssessmentReviewService.bad("AI chưa trả JSON đầy đủ/hợp lệ; hãy chia đề nhỏ hơn. Dữ liệu đang soạn được giữ.");}
    }
    JsonNode validateQuestions(JsonNode questions) {
        if(!questions.isArray() || questions.isEmpty() || questions.size()>15) throw AssessmentReviewService.bad("Một lần nhập PDF/Word cần 1–15 câu hợp lệ; đề dài hãy chia tệp hoặc dùng CSV (200 câu).");
        for(var q:questions) {
            if(!q.isObject() || !q.path("content").isTextual() || q.get("content").asText().isBlank() || q.get("content").asText().length()>10000 || !q.path("options").isArray() || q.get("options").size()<2 || q.get("options").size()>4) throw AssessmentReviewService.bad("Cấu trúc câu hỏi AI không hợp lệ.");
            for(var option:q.get("options")) if(!option.isTextual() || option.asText().isBlank() || option.asText().length()>1000) throw AssessmentReviewService.bad("Lựa chọn AI không hợp lệ.");
            if(!q.has("correctIndex") || !(q.get("correctIndex").isNull() || q.get("correctIndex").isIntegralNumber() && q.get("correctIndex").canConvertToInt() && q.get("correctIndex").asInt()>=0 && q.get("correctIndex").asInt()<q.get("options").size())) throw AssessmentReviewService.bad("Đáp án AI không hợp lệ.");
            if(!q.path("explanation").isTextual() || q.get("explanation").asText().length()>10000 || !q.path("points").isNumber()) throw AssessmentReviewService.bad("Giải thích/trọng số AI không hợp lệ.");
            var points=q.get("points").decimalValue();if(points.signum()<=0 || points.compareTo(new java.math.BigDecimal("9999.99"))>0 || points.stripTrailingZeros().scale()>2) throw AssessmentReviewService.bad("Trọng số AI không hợp lệ.");
        }
        return questions;
    }
    private static List<Integer> missing(JsonNode questions) {var result=new ArrayList<Integer>();for(int i=0;i<questions.size();i++) if(questions.get(i).get("correctIndex").isNull()) result.add(i);return result;}
}