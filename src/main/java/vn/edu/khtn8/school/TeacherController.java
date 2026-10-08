package vn.edu.khtn8.school;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static vn.edu.khtn8.school.AuthoringModels.*;

@Controller
public class TeacherController {
    private final TeacherAuthoringService authoring;
    private final TeacherClassService classes;
    private final QuizCsv csv;
    public TeacherController(TeacherAuthoringService authoring,TeacherClassService classes,QuizCsv csv) {this.authoring=authoring;this.classes=classes;this.csv=csv;}
    @GetMapping("/teacher/{kind:lessons|quizzes}")
    public String workspace(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String kind,Model model) {
        model.addAttribute("data",authoring.data(user));model.addAttribute("kind",kind);
        return page(model,"teacher/content-list",kind.equals("lessons")?"Bài học":"Quiz");
    }
    @GetMapping("/teacher/{kind:lessons|quizzes}/new")
    public String newContent(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String kind,Model model) {
        model.addAttribute("data",authoring.data(user));model.addAttribute("kind",kind);
        return page(model,"teacher/"+(kind.equals("lessons")?"lesson-form":"quiz-form"),"Tạo mới");
    }
    @GetMapping("/teacher/quizzes/import")
    public String importContent(@AuthenticationPrincipal AuthenticatedUser user,Model model) {
        model.addAttribute("data",authoring.data(user));model.addAttribute("kind","quizzes");model.addAttribute("importMode",true);
        return page(model,"teacher/quiz-form","Nhập quiz từ CSV");
    }
    @GetMapping("/teacher/{kind:lessons|quizzes}/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String kind,@PathVariable long id,Model model) {
        model.addAttribute("item",kind.equals("lessons")?authoring.lesson(user,id):authoring.quiz(user,id));model.addAttribute("kind",kind);
        return page(model,"teacher/content-detail","Chi tiết nội dung");
    }
    @GetMapping("/teacher/{kind:lessons|quizzes}/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String kind,@PathVariable long id,Model model) {
        var item=kind.equals("lessons")?authoring.lesson(user,id):authoring.quiz(user,id);
        if(item.get("deleted_at")!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Nội dung đã xóa.");
        model.addAttribute("data",authoring.data(user));model.addAttribute("kind",kind);model.addAttribute("editId",id);
        return page(model,"teacher/"+(kind.equals("lessons")?"lesson-form":"quiz-form"),"Chỉnh sửa");
    }
    @DeleteMapping("/api/v1/teacher/lessons/{id}") @ResponseBody
    public void deleteLesson(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {authoring.deleteLesson(user,id);}
    @DeleteMapping("/api/v1/teacher/quizzes/{id}") @ResponseBody
    public void deleteQuiz(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {authoring.deleteQuiz(user,id);}
    @PostMapping("/api/v1/teacher/lessons/{id}/duplicate") @ResponseBody
    public Map<String,Long> duplicateLesson(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return Map.of("id",authoring.duplicateLesson(user,id));}
    public record Preview(@NotNull @Size(max=100000) String html) { }
    @PostMapping("/api/v1/teacher/lessons/preview") @ResponseBody
    public Map<String,String> previewLesson(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Preview input) {
        SchoolService.require(user,"TEACHER","ADMIN");
        return Map.of("html",org.jsoup.Jsoup.clean(input.html(),org.jsoup.safety.Safelist.basic().addTags("h2","h3","h4")));
    }
    @PostMapping("/api/v1/teacher/quizzes/{id}/duplicate") @ResponseBody
    public Map<String,Long> duplicateQuiz(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return Map.of("id",authoring.duplicateQuiz(user,id));}
    @GetMapping("/api/v1/teacher/lessons/{id}") @ResponseBody
    public Map<String,Object> lesson(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return authoring.lesson(user,id);}
    @PostMapping("/api/v1/teacher/lessons") @ResponseBody
    public Map<String,Long> createLesson(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody LessonInput input) {return Map.of("id",authoring.saveLesson(user,null,input));}
    @PutMapping("/api/v1/teacher/lessons/{id}") @ResponseBody
    public Map<String,Long> updateLesson(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody LessonInput input) {return Map.of("id",authoring.saveLesson(user,id,input));}
    @GetMapping("/api/v1/teacher/quizzes/{id}") @ResponseBody
    public Map<String,Object> quiz(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return authoring.quiz(user,id);}
    @PostMapping("/api/v1/teacher/quizzes") @ResponseBody
    public Map<String,Long> createQuiz(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody QuizInput input) {return Map.of("id",authoring.saveQuiz(user,null,input));}
    @PutMapping("/api/v1/teacher/quizzes/{id}") @ResponseBody
    public Map<String,Long> updateQuiz(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody QuizInput input) {return Map.of("id",authoring.saveQuiz(user,id,input));}
    @GetMapping("/teacher/quizzes/template.csv")
    public ResponseEntity<ClassPathResource> template(@AuthenticationPrincipal AuthenticatedUser user) {
        SchoolService.require(user,"TEACHER","ADMIN");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=quiz-mau.csv")
                .body(new ClassPathResource("templates/quiz-mau.csv"));
    }
    @PostMapping(value="/api/v1/teacher/quizzes/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseBody
    public List<QuestionInput> preview(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam MultipartFile file) throws IOException {
        SchoolService.require(user,"TEACHER","ADMIN");
        if(file.getSize()>1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"File CSV không được vượt quá 1 MB.");
        if(file.getOriginalFilename()==null || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hãy chọn file .csv UTF-8.");
        return csv.parse(file.getBytes());
    }
    public record Enrollment(@NotBlank @Size(max=80) String username) { }
    public record Membership(boolean active) { }
    @PostMapping("/api/v1/teacher/classes/{id}/students") @ResponseBody
    public void enroll(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Enrollment input) {classes.enroll(user,id,input.username());}
    @PutMapping("/api/v1/teacher/classes/{id}/students/{studentId}") @ResponseBody
    public void membership(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long studentId,@RequestBody Membership input) {classes.membership(user,id,studentId,input.active());}
    @GetMapping("/teacher/classes/{id}/students/{studentId}")
    public String report(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long studentId,Model model) {
        model.addAttribute("student",classes.member(user,id,studentId));model.addAttribute("classId",id);model.addAttribute("studentId",studentId);
        return page(model,"teacher/student-detail","Thành viên lớp học");
    }
    @GetMapping("/api/v1/teacher/classes/{id}/students/{studentId}/report") @ResponseBody
    public Map<String,Object> reportApi(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long studentId) {return classes.report(user,id,studentId);}
    private String page(Model model,String view,String title) {model.addAttribute("contentPage","/WEB-INF/jsp/"+view+".jsp");model.addAttribute("pageTitle",title+" · EduLab");return "layout/default";}
}