package vn.edu.khtn8.school;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

/** Page composition only; services authorize both page and API access. */
@Controller
public class TeacherWorkspaceController {
    private final SchoolService school;
    private final TeacherClassService classes;
    private final AssignmentService assignments;
    private final vn.edu.khtn8.assessment.QuizService quizzes;
    private final TeacherInsightsService insights;
    public TeacherWorkspaceController(SchoolService school,TeacherClassService classes,AssignmentService assignments,vn.edu.khtn8.assessment.QuizService quizzes,TeacherInsightsService insights) {
        this.school=school;this.classes=classes;this.assignments=assignments;this.quizzes=quizzes;this.insights=insights;
    }
    private String page(Model model,String view,String title) {
        model.addAttribute("contentPage","/WEB-INF/jsp/teacher/"+view+".jsp");model.addAttribute("pageTitle",title+" · EduLab");return "layout/default";
    }
    private void classroom(AuthenticatedUser user,long id,Model model) {
        model.addAttribute("classroom",school.classroom(user,id));model.addAttribute("classId",id);
    }
    @GetMapping("/teacher")
    public String dashboard(@AuthenticationPrincipal AuthenticatedUser user,Model model) {
        model.addAttribute("overview",insights.overview(user));return page(model,"dashboard","Tổng quan giảng dạy");
    }
    @GetMapping("/teacher/assignments")
    public String allAssignments(@AuthenticationPrincipal AuthenticatedUser user,Model model) {
        model.addAttribute("assignments",insights.assignments(user));return page(model,"all-assignments","Bài đã giao");
    }
    @GetMapping("/teacher/classes/new")
    public String newClass(@AuthenticationPrincipal AuthenticatedUser user,Model model) {
        SchoolService.require(user,"ADMIN");model.addAttribute("years",school.years(user));return page(model,"class-form","Tạo lớp");
    }
    @GetMapping("/teacher/classes/{id}/edit")
    public String editClass(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,Model model) {
        SchoolService.require(user,"ADMIN");school.requireActiveClass(user,id);classroom(user,id,model);
        model.addAttribute("years",school.years(user));return page(model,"class-form","Chỉnh sửa lớp");
    }
    @GetMapping("/teacher/classes/{id}/teachers")
    public String teachers(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,Model model) {
        SchoolService.require(user,"ADMIN");classroom(user,id,model);
        model.addAttribute("members",school.members(user));model.addAttribute("teachers",school.teachers(user,id));return page(model,"class-teachers","Phân công giáo viên");
    }
    @GetMapping("/teacher/classes/{id}/students")
    public String students(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,Model model) {
        classroom(user,id,model);model.addAttribute("roster",classes.roster(user,id,search,page));return page(model,"students","Học sinh trong lớp");
    }
    @GetMapping("/teacher/classes/{id}/students/new")
    public String enroll(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,Model model) {
        school.requireActiveClass(user,id);classroom(user,id,model);return page(model,"student-form","Thêm học sinh vào lớp");
    }
    @GetMapping("/teacher/classes/{id}/{section:progress|results}")
    public String overview(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable String section,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,Model model) {
        classroom(user,id,model);model.addAttribute("roster",classes.roster(user,id,search,page));model.addAttribute("section",section);
        if(section.equals("results")) model.addAttribute("results",insights.results(user,id));
        return page(model,"class-results",section.equals("progress")?"Tiến độ bài học":"Kết quả quiz");
    }
    @GetMapping(value={"/teacher/classes/{id}/results.csv","/admin/classes/{id}/results.csv"},produces="text/csv;charset=UTF-8")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> exportResults(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {
        var rows=insights.results(user,id);
        var csv=new StringBuilder("\uFEFFHọc sinh,Tên đăng nhập,Trạng thái,Quiz,Số lượt,Đã chấm,Điểm cao nhất /10\r\n");
        for(var row:rows) {
            Object[] values={row.get("full_name"),row.get("username"),Boolean.TRUE.equals(row.get("active"))?"Đang học":"Đã ngừng tham gia",row.get("title"),row.get("attempts"),row.get("graded_count"),row.get("best_score")};
            for(int i=0;i<values.length;i++) {if(i>0) csv.append(',');csv.append(csvCell(values[i]));}csv.append("\r\n");
        }
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=ket-qua-lop-"+id+".csv")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String csvCell(Object value) {
        String text=value==null?"":String.valueOf(value);
        if(!text.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0))>=0) text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
    @GetMapping("/teacher/classes/{id}/students/{studentId}/{section:progress|results}")
    public String report(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long studentId,@PathVariable String section,Model model) {
        classroom(user,id,model);model.addAttribute("studentId",studentId);model.addAttribute("section",section);
        model.addAttribute("report",classes.report(user,id,studentId));return page(model,"report",section.equals("progress")?"Tiến độ bài học":"Kết quả quiz");
    }
    @GetMapping("/teacher/classes/{id}/assignments")
    public String assignments(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,Model model) {
        classroom(user,id,model);model.addAttribute("assignments",assignments.forClass(user,id));return page(model,"assignments","Bài đã giao");
    }
    @GetMapping("/teacher/classes/{id}/assignments/new")
    public String newAssignment(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,Model model) {
        school.requireActiveClass(user,id);classroom(user,id,model);var ids=classes.assignableQuizIds(user,id);
        model.addAttribute("quizzes",quizzes.list(user.id()).stream().filter(q->ids.contains(q.id())).toList());return page(model,"assignment-form","Giao quiz");
    }
    @GetMapping({"/teacher/classes/{id}/assignments/{assignmentId}","/teacher/classes/{id}/assignments/{assignmentId}/edit"})
    public String assignment(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long assignmentId,jakarta.servlet.http.HttpServletRequest request,Model model) {
        classroom(user,id,model);var item=assignments.assignment(user,id,assignmentId);model.addAttribute("item",item);
        boolean edit=request.getRequestURI().endsWith("/edit");
        if(edit) {
            school.requireActiveClass(user,id);
            if(item.get("cancelled_at")!=null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Bài giao đã hủy.");
        }
        if(!edit) model.addAttribute("submissions",insights.submissionStatus(user,id,assignmentId));
        return page(model,edit?"assignment-form":"assignment-detail",edit?"Sửa hạn nộp":"Chi tiết bài giao");
    }
    @GetMapping("/teacher/students/{studentId}/feedback")
    public String feedback(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,Model model) {
        SchoolService.require(user,"TEACHER","ADMIN");model.addAttribute("feedback",assignments.feedback(user,studentId));
        model.addAttribute("studentName",school.studentName(user,studentId));
        model.addAttribute("studentId",studentId);model.addAttribute("actorId",user.id());return page(model,"feedback","Nhận xét học tập");
    }
    @GetMapping("/teacher/students/{studentId}/feedback/new")
    public String newFeedback(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,Model model) {
        SchoolService.require(user,"TEACHER","ADMIN");school.authorizeStudent(user,studentId);model.addAttribute("studentId",studentId);return page(model,"feedback-form","Thêm nhận xét");
    }
    @GetMapping({"/teacher/students/{studentId}/feedback/{id}","/teacher/students/{studentId}/feedback/{id}/edit"})
    public String feedbackDetail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,@PathVariable long id,jakarta.servlet.http.HttpServletRequest request,Model model) {
        var item=assignments.feedbackDetail(user,studentId,id);model.addAttribute("item",item);model.addAttribute("studentId",studentId);model.addAttribute("actorId",user.id());
        boolean edit=request.getRequestURI().endsWith("/edit");
        if(edit && !SchoolService.hasRole(user,"ADMIN") && ((Number)item.get("teacher_id")).longValue()!=user.id()) throw new org.springframework.security.access.AccessDeniedException("Not feedback author");
        return page(model,edit?"feedback-form":"feedback-detail",edit?"Sửa nhận xét":"Chi tiết nhận xét");
    }
}