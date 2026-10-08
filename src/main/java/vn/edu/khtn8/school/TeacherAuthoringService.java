package vn.edu.khtn8.school;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import jakarta.validation.Validator;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static vn.edu.khtn8.school.AuthoringModels.*;

@Service
public class TeacherAuthoringService {
    private final JdbcTemplate jdbc;
    private final SchoolService school;
    private final Validator validator;
    public TeacherAuthoringService(JdbcTemplate jdbc,SchoolService school,Validator validator) {this.jdbc=jdbc;this.school=school;this.validator=validator;}
    public Map<String,Object> data(AuthenticatedUser user) {
        SchoolService.require(user,"TEACHER","ADMIN");
        return Map.of("classes",school.classrooms(user),
                "topics",jdbc.queryForList("SELECT t.id,CONCAT(s.name,' · ',c.title,' · ',t.name) name FROM topics t JOIN courses c ON c.id=t.course_id JOIN subjects s ON s.id=c.subject_id ORDER BY s.grade_level,c.id,t.display_order"),
                "lessons",jdbc.queryForList("SELECT id,title,status FROM lessons WHERE class_only=TRUE AND (? OR created_by=?) ORDER BY id DESC",SchoolService.hasRole(user,"ADMIN"),user.id()),
                "quizzes",jdbc.queryForList("SELECT id,title,published,mode FROM quizzes WHERE class_only=TRUE AND (? OR created_by=?) ORDER BY id DESC",SchoolService.hasRole(user,"ADMIN"),user.id()));
    }
    private void validate(Object input) {if(!validator.validate(input).isEmpty()) throw bad("Dữ liệu không hợp lệ. Kiểm tra các trường bắt buộc và giới hạn.");}
    private void classes(AuthenticatedUser user,List<Long> ids) {
        if(ids.stream().distinct().count()!=ids.size()) throw bad("Không chọn lớp trùng nhau.");
        for(long id:ids) school.authorizeClass(user,id);
    }
    private Map<String,Object> owned(AuthenticatedUser user,String table,long id,boolean lock) {
        SchoolService.require(user,"TEACHER","ADMIN");
        var rows=jdbc.queryForList("SELECT * FROM "+table+" WHERE id=? AND class_only=TRUE"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy nội dung.");
        var row=rows.getFirst();
        if(!SchoolService.hasRole(user,"ADMIN") && (row.get("created_by")==null || ((Number)row.get("created_by")).longValue()!=user.id())) throw new AccessDeniedException("Not content owner");
        // A former teacher must not change content still shared with a class they no longer teach.
        String links=table.equals("lessons")?"class_lessons":"quiz_classes",column=table.equals("lessons")?"lesson_id":"quiz_id";
        for(long classId:jdbc.queryForList("SELECT classroom_id FROM "+links+" WHERE "+column+"=?",Long.class,id)) school.authorizeClass(user,classId);
        return row;
    }
    public Map<String,Object> lesson(AuthenticatedUser user,long id) {
        var row=new HashMap<>(owned(user,"lessons",id,false));
        row.putAll(jdbc.queryForMap("SELECT objectives,content_html FROM lesson_versions WHERE lesson_id=? ORDER BY version_number DESC LIMIT 1",id));
        row.put("classIds",jdbc.queryForList("SELECT classroom_id FROM class_lessons WHERE lesson_id=?",Long.class,id));return row;
    }
    @Transactional
    public long saveLesson(AuthenticatedUser user,Long id,LessonInput input) {
        SchoolService.require(user,"TEACHER","ADMIN"); validate(input); classes(user,input.classIds());
        if(id!=null) owned(user,"lessons",id,true);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM topics WHERE id=?)",Boolean.class,input.topicId()))) throw bad("Chủ đề không tồn tại.");
        String html=Jsoup.clean(input.contentHtml(),Safelist.basic().addTags("h2","h3","h4"));
        if(Jsoup.parse(html).text().isBlank()) throw bad("Nội dung bài học không được để trống.");
        if(id==null) id=insert("INSERT INTO lessons(topic_id,title,slug,summary,duration_minutes,status,created_by,class_only) VALUES(?,?,?,?,?,?,?,TRUE)",input.topicId(),input.title().trim(),"teacher-"+UUID.randomUUID(),input.summary().trim(),input.durationMinutes(),input.status(),user.id());
        else jdbc.update("UPDATE lessons SET topic_id=?,title=?,summary=?,duration_minutes=?,status=? WHERE id=?",input.topicId(),input.title().trim(),input.summary().trim(),input.durationMinutes(),input.status(),id);
        int version=jdbc.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM lesson_versions WHERE lesson_id=?",Integer.class,id);
        jdbc.update("UPDATE lesson_versions SET status='ARCHIVED' WHERE lesson_id=?",id);
        jdbc.update("INSERT INTO lesson_versions(lesson_id,version_number,objectives,content_html,status,created_by) VALUES(?,?,?,?,?,?)",id,version,input.objectives(),html,input.status(),user.id());
        jdbc.update("DELETE FROM class_lessons WHERE lesson_id=?",id);
        for(long classId:input.classIds()) jdbc.update("INSERT INTO class_lessons(classroom_id,lesson_id) VALUES(?,?)",classId,id);
        audit(user,"SAVE_LESSON","lesson",id);return id;
    }
    public Map<String,Object> quiz(AuthenticatedUser user,long id) {
        var row=new HashMap<>(owned(user,"quizzes",id,false));
        row.put("questions",jdbc.query("SELECT q.id,q.content,q.explanation,i.points FROM questions q JOIN quiz_items i ON i.question_id=q.id WHERE i.quiz_id=? ORDER BY i.display_order",(rs,n)->{
            var opts=jdbc.query("SELECT option_text,is_correct FROM question_options WHERE question_id=? ORDER BY display_order",new vn.edu.khtn8.common.jdbc.BooleanColumnMapRowMapper("is_correct"),rs.getLong("id"));
            int correct=0;for(int i=0;i<opts.size();i++) if(Boolean.TRUE.equals(opts.get(i).get("is_correct"))) correct=i;
            return new QuestionInput(rs.getString("content"),rs.getString("explanation"),opts.stream().map(o->(String)o.get("option_text")).toList(),correct,rs.getBigDecimal("points"));
        },id));
        row.put("classIds",jdbc.queryForList("SELECT classroom_id FROM quiz_classes WHERE quiz_id=?",Long.class,id));return row;
    }
    @Transactional
    public long saveQuiz(AuthenticatedUser user,Long id,QuizInput input) {
        SchoolService.require(user,"TEACHER","ADMIN");validate(input);classes(user,input.classIds());
        if(id!=null) owned(user,"quizzes",id,true);
        if(input.published() && input.mode().equals("TEST") && (input.revealAfter()==null || !input.revealAfter().isAfter(Instant.now().plusSeconds(input.durationMinutes()*60L)))) throw bad("Giờ công bố đáp án phải sau thời điểm hiện tại cộng thời gian làm bài.");
        for(var q:input.questions()) if(q.correctIndex()>=q.options().size()) throw bad("Đáp án đúng không trỏ tới lựa chọn hợp lệ.");
        var reveal=input.revealAfter()==null?null:Timestamp.from(input.revealAfter());
        if(id==null) id=insert("INSERT INTO quizzes(title,duration_minutes,max_attempts,mode,reveal_after,published,created_by,class_only) VALUES(?,?,?,?,?,?,?,TRUE)",input.title().trim(),input.durationMinutes(),input.maxAttempts(),input.mode(),reveal,input.published(),user.id());
        else {
            jdbc.update("UPDATE quizzes SET title=?,duration_minutes=?,max_attempts=?,mode=?,reveal_after=?,published=? WHERE id=?",input.title().trim(),input.durationMinutes(),input.maxAttempts(),input.mode(),reveal,input.published(),id);
            jdbc.update("UPDATE questions SET active=FALSE WHERE id IN (SELECT question_id FROM quiz_items WHERE quiz_id=?)",id);
            jdbc.update("DELETE FROM quiz_items WHERE quiz_id=?",id);
        }
        // New question IDs preserve snapshots and historical attempts when editing a quiz.
        long bank=insert("INSERT INTO question_banks(name,created_by) VALUES(?,?)",input.title().trim(),user.id());
        int order=0;
        for(var q:input.questions()) {
            long question=insert("INSERT INTO questions(bank_id,content,explanation) VALUES(?,?,?)",bank,q.content().trim(),q.explanation().trim());
            for(int i=0;i<q.options().size();i++) jdbc.update("INSERT INTO question_options(question_id,option_text,is_correct,display_order) VALUES(?,?,?,?)",question,q.options().get(i).trim(),i==q.correctIndex(),i);
            jdbc.update("INSERT INTO quiz_items(quiz_id,question_id,display_order,points) VALUES(?,?,?,?)",id,question,order++,q.points());
        }
        // Do not leave assignments pointing at classes no longer allowed to access this quiz.
        for(long assigned:jdbc.queryForList("SELECT DISTINCT classroom_id FROM class_assignments WHERE quiz_id=?",Long.class,id))
            if(!input.classIds().contains(assigned)) throw bad("Không thể bỏ lớp đã được giao quiz này. Có thể ẩn quiz bằng cách lưu nháp.");
        jdbc.update("DELETE FROM quiz_classes WHERE quiz_id=?",id);
        for(long classId:input.classIds()) jdbc.update("INSERT INTO quiz_classes(classroom_id,quiz_id) VALUES(?,?)",classId,id);
        audit(user,"SAVE_QUIZ","quiz",id);return id;
    }
    private void audit(AuthenticatedUser user,String action,String type,long id) {jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,?,?,?)",user.id(),action,type,String.valueOf(id));}
    private long insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);return s;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}