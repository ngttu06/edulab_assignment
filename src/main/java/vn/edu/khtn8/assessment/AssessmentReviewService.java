package vn.edu.khtn8.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;
import static vn.edu.khtn8.assessment.QuizModels.*;

@Service
public class AssessmentReviewService {
    private final JdbcTemplate jdbc;
    private final QuizRepository repository;
    private final QuizService quizzes;
    public AssessmentReviewService(JdbcTemplate jdbc,QuizRepository repository,QuizService quizzes) {
        this.jdbc=jdbc;this.repository=repository;this.quizzes=quizzes;
    }
    public List<Map<String,Object>> history(AuthenticatedUser user) {
        SchoolService.require(user,"STUDENT");
        return jdbc.queryForList("SELECT id,title_snapshot title,status,score,started_at,submitted_at,attempt_number,mode_snapshot mode FROM quiz_attempts WHERE student_id=? ORDER BY id DESC LIMIT 200",user.id());
    }
    @Transactional
    public int tabEvent(AuthenticatedUser user,long id,String eventId) {
        SchoolService.require(user,"STUDENT");
        var attempt=repository.find(id,user.id(),true).orElseThrow(AssessmentReviewService::notFound);
        if(!"TEST".equals(attempt.mode())) throw bad("Chỉ ghi nhận rời tab cho bài kiểm tra.");
        if(attempt.status().equals("IN_PROGRESS") && Instant.now().isBefore(attempt.deadline()))
            jdbc.update("INSERT IGNORE INTO quiz_tab_events(attempt_id,event_id) VALUES(?,?)",id,eventId);
        return tabCount(id);
    }
    public int tabCount(long id) {return jdbc.queryForObject("SELECT COUNT(*) FROM quiz_tab_events WHERE attempt_id=?",Integer.class,id);}
    public AttemptView student(AuthenticatedUser user,long id) {
        SchoolService.require(user,"STUDENT");return quizzes.get(user.id(),id);
    }
    public AttemptView teacher(AuthenticatedUser user,long id) {
        SchoolService.require(user,"TEACHER","ADMIN");
        var rows=jdbc.queryForList("SELECT student_id,quiz_id FROM quiz_attempts WHERE id=?",id);
        if(rows.isEmpty()) throw notFound();
        long student=((Number)rows.getFirst().get("student_id")).longValue();
        long quiz=((Number)rows.getFirst().get("quiz_id")).longValue();
        var classes=allowedClasses(user,quiz,0);
        if(!SchoolService.hasRole(user,"ADMIN") && classes.stream().noneMatch(c -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM class_enrollments WHERE classroom_id=? AND student_id=?)",Boolean.class,((Number)c.get("id")).longValue(),student)))) throw notFound();
        return QuizService.view(repository.find(id,student,false).orElseThrow(AssessmentReviewService::notFound),true);
    }
    public Map<String,Object> teacherDetail(AuthenticatedUser user,long id) {
        var attempt=teacher(user,id);
        return Map.of("attempt",attempt,"student",jdbc.queryForMap("SELECT u.full_name,u.username,a.quiz_id,a.started_at,a.submitted_at FROM quiz_attempts a JOIN users u ON u.id=a.student_id WHERE a.id=?",id),
                "events",jdbc.queryForList("SELECT received_at FROM quiz_tab_events WHERE attempt_id=? ORDER BY received_at",id),
                "reviews",jdbc.query("SELECT question_id,review_type,content,provider,sample FROM quiz_ai_reviews WHERE attempt_id=? AND status='SUCCEEDED' ORDER BY question_id",new vn.edu.khtn8.common.jdbc.BooleanColumnMapRowMapper("sample"),id));
    }
    public List<Map<String,Object>> allowedClasses(AuthenticatedUser user,long quiz,long classId) {
        SchoolService.require(user,"TEACHER","ADMIN");
        var rows=jdbc.queryForList("""
                SELECT c.id,c.name FROM classrooms c WHERE (?=0 OR c.id=?) AND
                  (EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.classroom_id=c.id AND qc.quiz_id=?) OR
                   EXISTS(SELECT 1 FROM class_assignments ca WHERE ca.classroom_id=c.id AND ca.quiz_id=?))
                  AND (? OR EXISTS(SELECT 1 FROM class_teachers ct WHERE ct.classroom_id=c.id AND ct.teacher_id=?)) ORDER BY c.id
                """,classId,classId,quiz,quiz,SchoolService.hasRole(user,"ADMIN"),user.id());
        if(rows.isEmpty()) throw notFound();
        return rows;
    }
    public Map<String,Object> report(AuthenticatedUser user,long quiz,long classId,String selection) {
        if(!Set.of("latest","best","all").contains(selection)) throw bad("Cách tính phổ điểm không hợp lệ.");
        var classes=allowedClasses(user,quiz,classId);
        String ids=classes.stream().map(c -> String.valueOf(((Number)c.get("id")).longValue())).collect(java.util.stream.Collectors.joining(","));
        var students=jdbc.queryForList("SELECT DISTINCT u.id,u.full_name,u.username FROM users u JOIN class_enrollments e ON e.student_id=u.id WHERE e.classroom_id IN ("+ids+") ORDER BY u.full_name,u.id");
        var attempts=jdbc.queryForList("""
                SELECT a.id,a.student_id,u.full_name,u.username,a.attempt_number,a.status,a.score,a.started_at,a.submitted_at,a.mode_snapshot,
                  (SELECT COUNT(*) FROM quiz_tab_events t WHERE t.attempt_id=a.id) tab_count
                FROM quiz_attempts a JOIN users u ON u.id=a.student_id WHERE a.quiz_id=? AND EXISTS(
                SELECT 1 FROM class_enrollments e WHERE e.student_id=a.student_id AND e.classroom_id IN (
                """+ids+")) ORDER BY a.id DESC",quiz);
        var selected=new LinkedHashMap<Long,BigDecimal>();var scores=new ArrayList<BigDecimal>();
        var started=new HashSet<Long>();var graded=new HashSet<Long>();
        for(var a:attempts) {
            long student=((Number)a.get("student_id")).longValue();started.add(student);
            if(!"GRADED".equals(a.get("status"))) continue;
            graded.add(student);BigDecimal score=(BigDecimal)a.get("score");
            if(selection.equals("all")) scores.add(score);
            else if(selection.equals("best")) selected.merge(student,score,BigDecimal::max);
            else selected.putIfAbsent(student,score);
        }
        if(!selection.equals("all")) scores.addAll(selected.values());
        for(var student:students) {
            long sid=((Number)student.get("id")).longValue();
            student.put("result_status",graded.contains(sid)?"GRADED":started.contains(sid)?"IN_PROGRESS":"NOT_STARTED");
        }
        int[] counts=new int[10];for(var score:scores) counts[Math.min(9,Math.max(0,score.intValue()))]++;
        var bins=new ArrayList<Map<String,Object>>();
        for(int i=0;i<10;i++) bins.add(Map.of("label",i==9?"9–10":i+"–<"+(i+1),"count",counts[i]));
        var stats=new LinkedHashMap<String,Object>();stats.put("studentCount",students.size());stats.put("completed",graded.size());stats.put("notStarted",students.size()-started.size());stats.put("inProgress",started.size()-graded.size());stats.put("sampleCount",scores.size());
        stats.put("average",scores.isEmpty()?null:scores.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(scores.size()),2,java.math.RoundingMode.HALF_UP));
        stats.put("highest",scores.stream().max(BigDecimal::compareTo).orElse(null));stats.put("lowest",scores.stream().min(BigDecimal::compareTo).orElse(null));
        return Map.of("quiz",jdbc.queryForMap("SELECT id,title,mode FROM quizzes WHERE id=?",quiz),"classes",allowedClasses(user,quiz,0),"students",students,"attempts",attempts,"bins",bins,"stats",stats,"selection",selection,"classId",classId);
    }
    static ResponseStatusException notFound() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài làm hoặc không thuộc phạm vi lớp của bạn.");}
    static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}