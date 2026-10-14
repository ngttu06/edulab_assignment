package vn.edu.khtn8.assessment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Controller
public class AssessmentReviewController {
    private final AssessmentReviewService reviews;
    private final QuizAiReviewService ai;
    public AssessmentReviewController(AssessmentReviewService reviews,QuizAiReviewService ai) {this.reviews=reviews;this.ai=ai;}
    public record TabEvent(@Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") @jakarta.validation.constraints.NotNull String eventId) {}
    @GetMapping("/quizzes/history")
    public String history(@AuthenticationPrincipal AuthenticatedUser user,Model model) {
        model.addAttribute("attempts",reviews.history(user));return page(model,"quiz/history","Lịch sử làm bài");
    }
    @PostMapping("/api/v1/quiz-attempts/{id}/tab-events") @ResponseBody
    public Map<String,Integer> event(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody TabEvent event) {return Map.of("count",reviews.tabEvent(user,id,event.eventId()));}
    @GetMapping("/api/v1/quiz-attempts/{id}/tab-events") @ResponseBody
    public Map<String,Integer> eventCount(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {reviews.student(user,id);return Map.of("count",reviews.tabCount(id));}
    @PostMapping("/api/v1/quiz-attempts/{id}/ai-review") @ResponseBody
    public Map<String,Object> summary(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return ai.review(user,id,0);}
    @PostMapping("/api/v1/quiz-attempts/{id}/questions/{questionId}/ai-explanation") @ResponseBody
    public Map<String,Object> explanation(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long questionId) {
        if(questionId<=0) throw AssessmentReviewService.bad("Câu hỏi không hợp lệ.");return ai.review(user,id,questionId);
    }
    @GetMapping("/teacher/quiz-attempts/{id}")
    public String teacherAttempt(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,Model model) {
        var detail=reviews.teacherDetail(user,id);model.addAllAttributes(detail);model.addAttribute("teacherReview",true);
        return page(model,"quiz/attempt","Chi tiết bài làm học sinh");
    }
    @GetMapping("/api/v1/teacher/quiz-attempts/{id}") @ResponseBody
    public Map<String,Object> teacherAttemptApi(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return reviews.teacherDetail(user,id);}
    @GetMapping("/teacher/quizzes/{id}/report")
    public String report(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestParam(defaultValue="0") long classId,@RequestParam(defaultValue="latest") String selection,Model model) {
        model.addAttribute("report",reviews.report(user,id,classId,selection));return page(model,"teacher/quiz-report","Báo cáo quiz");
    }
    @GetMapping("/api/v1/teacher/quizzes/{id}/report") @ResponseBody
    public Map<String,Object> reportApi(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestParam(defaultValue="0") long classId,@RequestParam(defaultValue="latest") String selection) {return reviews.report(user,id,classId,selection);}
    @GetMapping("/teacher/quizzes/{id}/report.csv")
    public ResponseEntity<String> csv(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestParam(defaultValue="0") long classId) {
        var report=reviews.report(user,id,classId,"all");StringBuilder out=new StringBuilder("\uFEFFHọc sinh,Tên đăng nhập,Lượt,Trạng thái,Điểm,Số lần rời tab,Nộp lúc UTC\r\n");
        @SuppressWarnings("unchecked") var attempts=(List<Map<String,Object>>)report.get("attempts");
        for(var a:attempts) {StringJoiner line=new StringJoiner(",");for(String key:List.of("full_name","username","attempt_number","status","score","tab_count","submitted_at")) line.add(csvCell(a.get(key)));out.append(line).append("\r\n");}
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=quiz-report-"+id+".csv").body(out.toString());
    }
    private static String csvCell(Object value) {String text=value==null?"":value.toString(),trimmed=text.stripLeading();if(!trimmed.isEmpty() && "=+@-".indexOf(trimmed.charAt(0))>=0 || text.startsWith("\t") || text.startsWith("\r")) text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
    private String page(Model model,String view,String title) {model.addAttribute("contentPage","/WEB-INF/jsp/"+view+".jsp");model.addAttribute("pageTitle",title+" · EduLab");return "layout/default";}
}