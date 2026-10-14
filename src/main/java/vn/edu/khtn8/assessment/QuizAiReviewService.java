package vn.edu.khtn8.assessment;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.ai.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static vn.edu.khtn8.assessment.QuizModels.*;

@Service
public class QuizAiReviewService {
    private final AssessmentReviewService reviews;
    private final JdbcTemplate jdbc;
    private final AiProvider provider;
    private final AiQuotaService quota;
    private final AiSettingsService settings;
    public QuizAiReviewService(AssessmentReviewService reviews,JdbcTemplate jdbc,AiProvider provider,AiQuotaService quota,AiSettingsService settings) {
        this.reviews=reviews;this.jdbc=jdbc;this.provider=provider;this.quota=quota;this.settings=settings;
    }
    public Map<String,Object> review(AuthenticatedUser user,long id,long questionId) {
        AttemptView attempt=reviews.student(user,id);
        if(!attempt.status().equals("GRADED")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Nộp bài trước khi hỏi AI.");
        if(questionId!=0 && !attempt.answersVisible()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa đến lịch công bố đáp án; giải thích AI chưa mở.");
        QuestionView question=questionId==0?null:attempt.questions().stream().filter(q -> q.id()==questionId).findFirst().orElseThrow(AssessmentReviewService::notFound);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM quiz_attempts WHERE student_id=? AND mode_snapshot='TEST' AND status='IN_PROGRESS' AND deadline_at>CURRENT_TIMESTAMP(6))",Boolean.class,user.id())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"AI tạm khóa khi bạn đang làm bài kiểm tra, kể cả nhận xét đã lưu.");
        String type=questionId!=0?"QUESTION":attempt.answersVisible()?"SUMMARY":"GENERAL";
        var cached=jdbc.query("SELECT status,content,sample FROM quiz_ai_reviews WHERE attempt_id=? AND question_id=? AND review_type=?",new vn.edu.khtn8.common.jdbc.BooleanColumnMapRowMapper("sample"),id,questionId,type);
        if(!cached.isEmpty() && "SUCCEEDED".equals(cached.getFirst().get("status"))) return Map.of("text",cached.getFirst().get("content"),"cached",true,"sample",Boolean.TRUE.equals(cached.getFirst().get("sample")));
        if(!settings.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI đang tắt; bài làm và điểm của bạn vẫn được lưu.");
        // Short lease, no provider call while holding a database transaction or row lock.
        jdbc.update("INSERT IGNORE INTO quiz_ai_reviews(attempt_id,question_id,review_type,status) VALUES(?,?,?,'FAILED')",id,questionId,type);
        int claimed=jdbc.update("UPDATE quiz_ai_reviews SET status='PENDING',updated_at=CURRENT_TIMESTAMP(6) WHERE attempt_id=? AND question_id=? AND review_type=? AND (status='FAILED' OR (status='PENDING' AND updated_at<DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 5 MINUTE)))",id,questionId,type);
        if(claimed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Nhận xét AI đang được tạo. Hãy thử lại sau ít giây.");
        Long request=null;
        try {
            request=quota.reserve(user.id(),provider.providerName(),"QUIZ_REVIEW");
            String context="Điểm đã chấm: "+attempt.score()+"/10; số câu: "+attempt.questions().size()+". ";
            if(!attempt.answersVisible()) context+="Chưa công bố đáp án. Chỉ nhận xét tổng quan theo điểm, không suy đoán câu sai hay nội dung đề.";
            else if(question!=null) context+=questionContext(question);
            else {
                context+="Đúng: "+attempt.correctCount()+"; sai: "+attempt.wrongCount()+"; bỏ trống: "+attempt.unansweredCount()+".\n";
                // Bound each question evenly rather than silently excluding the end of a long quiz.
                int perQuestion=Math.max(40,14000/Math.max(1,attempt.questions().size()));
                for(var q:attempt.questions()) {String text=q.outcome()+": "+q.text();context+=text.substring(0,Math.min(perQuestion,text.length()))+"\n";}
            }
            String prompt=question==null?"Nhận xét bài đã nộp bằng tiếng Việt: mức độ hoàn thành, kiến thức cần ôn, 3 gợi ý luyện tập. Không tự thay đổi điểm; không bịa chủ đề khi thiếu dữ kiện.":"Giải thích câu đã nộp bằng tiếng Việt: vì sao đáp án đúng, vì sao lựa chọn học sinh sai (nếu có), hướng suy luận và một gợi ý ghi nhớ. Đáp án giáo viên là tham chiếu; nói rõ nếu phát hiện mâu thuẫn.";
            String text=provider.answer(new AiProvider.AiQuestion(prompt,context,"QUIZ_REVIEW")).text();
            if(text==null || text.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI chưa trả nhận xét hợp lệ.");
            jdbc.update("UPDATE quiz_ai_reviews SET status='SUCCEEDED',content=?,provider=?,sample=?,updated_at=CURRENT_TIMESTAMP(6) WHERE attempt_id=? AND question_id=? AND review_type=?",text,provider.providerName(),provider.providerName().equals("stub-local"),id,questionId,type);
            quota.finish(request,true);return Map.of("text",text,"cached",false,"sample",provider.providerName().equals("stub-local"));
        } catch(RuntimeException ex) {
            jdbc.update("UPDATE quiz_ai_reviews SET status='FAILED',updated_at=CURRENT_TIMESTAMP(6) WHERE attempt_id=? AND question_id=? AND review_type=?",id,questionId,type);
            if(request!=null) quota.finish(request,false);throw ex;
        }
    }
    private static String questionContext(QuestionView q) {
        StringBuilder result=new StringBuilder("Câu hỏi: ").append(bounded(q.text(),7000)).append("\n");
        for(var o:q.options()) result.append(o.text()).append(Boolean.TRUE.equals(o.correct())?" [ĐÁP ÁN GIÁO VIÊN]":"").append(Objects.equals(q.selectedOption(),o.id())?" [HỌC SINH CHỌN]":"").append("\n");
        return result.append("Trạng thái: ").append(q.outcome()).append("\nGiải thích giáo viên: ").append(bounded(q.explanation(),3000)).toString();
    }
    private static String bounded(String text,int limit) {return text==null?"":text.length()>limit?text.substring(0,limit)+" [nội dung dài đã rút gọn]":text;}
}