package vn.edu.khtn8.administration;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public AdminService(JdbcTemplate jdbc,PasswordEncoder encoder) { this.jdbc=jdbc; this.encoder=encoder; }
    public Map<String,Object> data(AuthenticatedUser user) {
        SchoolService.require(user,"ADMIN");
        return Map.of("users",jdbc.queryForList("SELECT id,username,full_name,role,enabled FROM users ORDER BY id DESC LIMIT 200"),
                "topics",jdbc.queryForList("SELECT id,name FROM topics ORDER BY display_order,id"),
                "lessons",jdbc.queryForList("SELECT id,title,status,prerequisite_id,required_score FROM lessons WHERE deleted_at IS NULL ORDER BY id DESC LIMIT 200"),
                "quizzes",jdbc.queryForList("SELECT id,title,published FROM quizzes WHERE deleted_at IS NULL ORDER BY id DESC LIMIT 200"),
                "questions",jdbc.queryForList("SELECT id,content,difficulty FROM questions WHERE active=TRUE ORDER BY id DESC LIMIT 200"));
    }

    @Transactional
    public long createUser(AuthenticatedUser actor,AdminController.UserInput input) {
        SchoolService.require(actor,"ADMIN");
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw bad("Mật khẩu vượt 72 byte UTF-8.");
        if (!List.of("TEACHER","STUDENT","PARENT").contains(input.role())) throw bad("Vai trò tạo tài khoản không hợp lệ.");
        long id=insert("INSERT INTO users(username,email,password_hash,full_name,role) VALUES(?,?,?,?,?)",input.username().trim(),input.email().trim().toLowerCase(java.util.Locale.ROOT),encoder.encode(input.password()),input.fullName().trim(),input.role());
        audit(actor,"CREATE_USER","user",id); return id;
    }

    @Transactional
    public void enableUser(AuthenticatedUser actor,long id,boolean enabled) {
        SchoolService.require(actor,"ADMIN");
        // Protect all administrator accounts from this basic user-management screen.
        if (jdbc.update("UPDATE users SET enabled=?,auth_version=auth_version+1 WHERE id=? AND role<>'ADMIN'",enabled,id)==0)
            throw bad("Không thể thay đổi tài khoản này.");
        jdbc.update("UPDATE refresh_sessions SET revoked_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND revoked_at IS NULL",id);
        audit(actor,enabled?"ENABLE_USER":"DISABLE_USER","user",id);
    }

    @Transactional
    public long createLesson(AuthenticatedUser actor,AdminController.LessonInput input) {
        SchoolService.require(actor,"ADMIN");
        if (input.prerequisiteId()!=null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM lessons WHERE id=? AND status='PUBLISHED')",Boolean.class,input.prerequisiteId())))
            throw bad("Bài tiên quyết phải được xuất bản.");
        String html=Jsoup.clean(input.contentHtml(),Safelist.basic().addTags("h2","h3","h4"));
        if (Jsoup.parse(html).text().isBlank()) throw bad("Nội dung bài học không được để trống.");
        long id=insert("INSERT INTO lessons(topic_id,title,slug,summary,duration_minutes,prerequisite_id,required_score) VALUES(?,?,?,?,?,?,?)",
                input.topicId(),input.title().trim(),"lesson-"+UUID.randomUUID(),input.summary().trim(),input.durationMinutes(),input.prerequisiteId(),input.requiredScore());
        insert("INSERT INTO lesson_versions(lesson_id,version_number,objectives,content_html,status,created_by) VALUES(?,1,?,?,'DRAFT',?)",id,input.objectives().trim(),html,actor.id());
        audit(actor,"CREATE_LESSON","lesson",id); return id;
    }

    @Transactional
    public void publishLesson(AuthenticatedUser actor,long id,boolean publish) {
        SchoolService.require(actor,"ADMIN");
        if (jdbc.queryForList("SELECT id FROM lessons WHERE id=? FOR UPDATE",id).isEmpty()) throw bad("Không tìm thấy bài học.");
        if (publish && jdbc.queryForList("SELECT id FROM lesson_versions WHERE lesson_id=?",id).isEmpty()) throw bad("Bài học chưa có nội dung.");
        String status=publish?"PUBLISHED":"ARCHIVED";
        if(jdbc.update("UPDATE lessons SET status=? WHERE id=? AND deleted_at IS NULL",status,id)==0) throw bad("Bài học không tồn tại hoặc đã xóa.");
        jdbc.update("UPDATE lesson_versions SET status=? WHERE lesson_id=?",status,id);
        audit(actor,publish?"PUBLISH_LESSON":"ARCHIVE_LESSON","lesson",id);
    }

    @Transactional
    public long createQuestion(AuthenticatedUser actor,AdminController.QuestionInput input) {
        SchoolService.require(actor,"ADMIN");
        if (input.correctIndex()<0 || input.correctIndex()>=input.options().size()) throw bad("Chỉ số đáp án đúng không hợp lệ.");
        var banks=jdbc.queryForList("SELECT id FROM question_banks ORDER BY id LIMIT 1",Long.class);
        long bank=banks.isEmpty()?insert("INSERT INTO question_banks(name,created_by) VALUES('Ngân hàng KHTN 8',?)",actor.id()):banks.getFirst();
        long id=insert("INSERT INTO questions(bank_id,content,explanation,difficulty) VALUES(?,?,?,?)",bank,input.content().trim(),input.explanation().trim(),input.difficulty());
        for(int i=0;i<input.options().size();i++) insert("INSERT INTO question_options(question_id,option_text,is_correct,display_order) VALUES(?,?,?,?)",id,input.options().get(i).trim(),i==input.correctIndex(),i);
        audit(actor,"CREATE_QUESTION","question",id); return id;
    }

    @Transactional
    public long createQuiz(AuthenticatedUser actor,AdminController.QuizInput input) {
        SchoolService.require(actor,"ADMIN");
        if(input.questionIds().stream().distinct().count()!=input.questionIds().size()) throw bad("Không chọn câu hỏi trùng nhau.");
        if(input.mode().equals("TEST") && (input.revealAfter()==null || !input.revealAfter().isAfter(java.time.Instant.now()))) throw bad("Bài kiểm tra cần thời điểm công bố đáp án ở tương lai.");
        for(long id:input.questionIds()) {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM questions q WHERE q.id=? AND q.active=TRUE AND (SELECT COUNT(*) FROM question_options o WHERE o.question_id=q.id)>=2 AND (SELECT COUNT(*) FROM question_options o WHERE o.question_id=q.id AND o.is_correct=TRUE)=1)",Boolean.class,id)))
                throw bad("Câu hỏi chưa hợp lệ.");
        }
        long id=insert("INSERT INTO quizzes(title,lesson_id,duration_minutes,max_attempts,created_by,mode,reveal_after) VALUES(?,?,?,?,?,?,?)",input.title().trim(),input.lessonId(),input.durationMinutes(),input.maxAttempts(),actor.id(),input.mode(),input.revealAfter()==null?null:java.sql.Timestamp.from(input.revealAfter()));
        int order=0; for(long question:input.questionIds()) jdbc.update("INSERT INTO quiz_items(quiz_id,question_id,display_order,points) VALUES(?,?,?,1)",id,question,++order);
        audit(actor,"CREATE_QUIZ","quiz",id); return id;
    }

    public void publishQuiz(AuthenticatedUser actor,long id,boolean publish) {
        SchoolService.require(actor,"ADMIN");
        if(jdbc.update("UPDATE quizzes SET published=? WHERE id=? AND deleted_at IS NULL",publish,id)==0) throw bad("Quiz không tồn tại hoặc đã xóa.");
        audit(actor,publish?"PUBLISH_QUIZ":"HIDE_QUIZ","quiz",id);
    }
    private void audit(AuthenticatedUser actor,String action,String type,long id) {
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,?,?,?)",actor.id(),action,type,String.valueOf(id));
    }
    private long insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder(); jdbc.update(c->{var statement=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]); return statement;},key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}