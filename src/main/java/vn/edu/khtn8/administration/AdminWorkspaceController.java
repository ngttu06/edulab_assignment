package vn.edu.khtn8.administration;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Controller
public class AdminWorkspaceController {
    private final AdminWorkspaceService service;
    public AdminWorkspaceController(AdminWorkspaceService service) {this.service=service;}
    private String page(Model model,String view,String title,String active) {
        model.addAttribute("adminNavigation",AdminResources.all());model.addAttribute("adminActive",active);
        model.addAttribute("adminTitle",title);model.addAttribute("pageTitle",title+" · EduLab Admin");
        model.addAttribute("contentPage","/WEB-INF/jsp/admin/"+view+".jsp");return "layout/admin";
    }
    @GetMapping("/admin")
    public String dashboard(@AuthenticationPrincipal AuthenticatedUser user,Model model) {
        model.addAttribute("overview",service.overview(user));return page(model,"dashboard","Tổng quan hệ thống","dashboard");
    }
    @GetMapping("/admin/settings/{kind:ai|storage}")
    public String settings(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String kind,Model model) {
        SchoolService.require(user,"ADMIN");model.addAttribute("settingsKind",kind);return page(model,"settings",kind.equals("ai")?"Cấu hình trợ lý AI":"Cấu hình lưu trữ",kind);
    }
    @GetMapping("/admin/teaching-assignments")
    public String assignments(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("listing",service.teachingAssignments(user,search,page));model.addAttribute("search",search);
        return page(model,"teaching-assignments","Phân công giảng dạy","teaching-assignments");
    }
    @GetMapping("/admin/{resource}")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="all") String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="newest") String sort,Model model) {
        var r=AdminResources.get(resource);model.addAttribute("resource",r);model.addAttribute("listing",service.list(user,resource,search,state,page,sort));
        model.addAttribute("search",search);model.addAttribute("state",state);model.addAttribute("sort",sort);return page(model,"list",r.title(),resource);
    }
    @GetMapping("/admin/{resource}/new")
    public String create(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,Model model) {
        SchoolService.require(user,"ADMIN");var r=AdminResources.get(resource);if(r.readOnly())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED);
        if(resource.equals("files"))return page(model,"upload","Tải tài liệu lên","files");
        var item=new HashMap<String,Object>();for(var f:r.fields()) {
            if(f.type().equals("boolean"))item.put(f.key(),!List.of("class_only","published").contains(f.key()));
            if(f.type().equals("number"))item.put(f.key(),switch(f.key()){case "grade_level"->8;case "duration_minutes"->30;case "max_attempts"->3;case "correctIndex"->1;default->0;});
            if(f.type().equals("select"))item.put(f.key(),f.choices().getFirst());
        }
        model.addAttribute("resource",r);model.addAttribute("item",item);model.addAttribute("selectedOptions",Map.of());
        return page(model,"form","Thêm mới · "+r.title(),resource);
    }
    @GetMapping("/admin/{resource}/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@PathVariable long id,Model model) {
        var r=AdminResources.get(resource);if(r.readOnly())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED);
        var item=service.detail(user,resource,id);model.addAttribute("resource",r);model.addAttribute("item",item);model.addAttribute("editing",true);
        model.addAttribute("selectedOptions",service.selectedOptions(user,r,item));return page(model,"form","Chỉnh sửa · "+r.title(),resource);
    }
    @GetMapping("/admin/{resource}/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@PathVariable long id,Model model) {
        var r=AdminResources.get(resource);model.addAttribute("resource",r);model.addAttribute("item",service.detail(user,resource,id));
        model.addAttribute("related",service.related(user,resource,id));return page(model,"detail","Chi tiết · "+r.title(),resource);
    }
    @GetMapping("/api/v1/admin/workspace/options/{resource}") @ResponseBody
    public List<Map<String,Object>> options(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@RequestParam(defaultValue="") String search) {return service.options(user,resource,search);}
    @GetMapping("/api/v1/admin/workspace/{resource}") @ResponseBody
    public AdminWorkspaceService.Page listApi(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="all") String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="newest") String sort) {
        return service.list(user,resource,search,state,page,sort);
    }
    @GetMapping("/api/v1/admin/workspace/{resource}/{id}") @ResponseBody
    public Map<String,Object> detailApi(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@PathVariable long id) {return service.detail(user,resource,id);}
    @PostMapping("/api/v1/admin/workspace/{resource}") @ResponseBody
    public Map<String,Long> createApi(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@RequestBody Map<String,Object> input) {return Map.of("id",service.save(user,resource,null,input));}
    @PutMapping("/api/v1/admin/workspace/{resource}/{id}") @ResponseBody
    public Map<String,Long> updateApi(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@PathVariable long id,@RequestBody Map<String,Object> input) {return Map.of("id",service.save(user,resource,id,input));}
    @DeleteMapping("/api/v1/admin/workspace/{resource}/{id}") @ResponseBody
    public void delete(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@PathVariable long id) {service.remove(user,resource,id);}
    @PostMapping("/api/v1/admin/workspace/{resource}/{id}/restore") @ResponseBody
    public void restore(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String resource,@PathVariable long id) {service.restore(user,resource,id);}
    public record Teacher(@Positive long classId,@Positive long teacherId,boolean homeroom) { }
    public record Student(@Positive long studentId,boolean active) { }
    @PostMapping("/api/v1/admin/workspace/teaching-assignments") @ResponseBody
    public void assign(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Teacher input) {service.assignTeacher(user,input.classId(),input.teacherId(),input.homeroom());}
    @DeleteMapping("/api/v1/admin/workspace/classes/{id}/teachers/{teacherId}") @ResponseBody
    public void unassign(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long teacherId) {service.removeTeacher(user,id,teacherId);}
    @PostMapping("/api/v1/admin/workspace/classes/{id}/students") @ResponseBody
    public void enroll(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Student input) {service.enrollStudent(user,id,input.studentId(),input.active());}
}