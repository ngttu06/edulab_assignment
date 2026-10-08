package vn.edu.khtn8.administration;

import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
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
import static vn.edu.khtn8.administration.AdminResources.*;

@Service
public class AdminWorkspaceService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AdminWorkspaceService(JdbcTemplate jdbc,PasswordEncoder passwords) {this.jdbc=jdbc;this.passwords=passwords;}
    public record Page(List<Map<String,Object>> items,long total,int page,int pages,int size) { }
    private static void require(AuthenticatedUser user) {SchoolService.require(user,"ADMIN");}
    private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException conflict(String message) {return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static ResponseStatusException missing() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bản ghi.");}
    private boolean exists(String sql,Object...args) {return Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,args));}
    private long count(String sql,Object...args) {return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private static String like(String text) {return "%"+text.replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private static String available(Resource r) {
        if(r.state()==null)return "1=1";
        return switch(r.state()) {
            case "enabled","active" -> "r."+r.state()+"=TRUE";
            case "deleted_at","archived_at","cancelled_at" -> "r."+r.state()+" IS NULL";
            case "status" -> "r.status NOT IN ('REMOVED','REVOKED','HIDDEN','RESOLVED','DISMISSED')";
            case "scan_status" -> "r.scan_status='CLEAN'";
            default -> "1=1";
        };
    }
    private String select(Resource r) {
        StringBuilder sql=new StringBuilder(r.table().equals("users")?"r.id,r.username,r.full_name,r.email,r.role,r.enabled,r.created_at":"r.*");
        for(var field:r.fields()) if(field.reference()!=null && field.type().equals("reference")) {
            var target=get(field.reference());
            sql.append(",(SELECT LEFT(CAST(x.").append(target.label()).append(" AS CHAR),180) FROM ").append(target.table())
                .append(" x WHERE x.id=r.").append(field.key()).append(") ").append(field.key()).append("_label");
        }
        switch(r.key()) {
            case "teachers" -> sql.append(",(SELECT COUNT(*) FROM class_teachers ct JOIN classrooms c ON c.id=ct.classroom_id WHERE ct.teacher_id=r.id AND c.archived_at IS NULL) class_count");
            case "students" -> sql.append(",(SELECT COUNT(*) FROM class_enrollments ce WHERE ce.student_id=r.id AND ce.active=TRUE) class_count");
            case "parents","administrators" -> sql.append(",NULL class_count");
            case "classes" -> sql.append(",(SELECT COUNT(*) FROM class_teachers ct WHERE ct.classroom_id=r.id) teacher_count,(SELECT COUNT(*) FROM class_enrollments ce WHERE ce.classroom_id=r.id AND ce.active=TRUE) student_count");
            case "feedback" -> sql.append(",(SELECT full_name FROM users WHERE id=r.teacher_id) teacher_name");
            case "community-posts","community-comments","forum-threads","forum-posts" -> sql.append(",(SELECT full_name FROM users WHERE id=r.author_id) author_name");
            case "audit-logs" -> sql.append(",(SELECT full_name FROM users WHERE id=r.actor_user_id) actor_name");
            default -> { }
        }
        return sql.toString();
    }
    public Page list(AuthenticatedUser user,String key,String search,String state,int page,String sort) {
        require(user);var r=get(key);search=search==null?"":search.trim();if(search.length()>160)throw bad("Từ khóa tối đa 160 ký tự.");
        if(!List.of("all","active","inactive").contains(state))throw bad("Bộ lọc không hợp lệ.");
        String where=" WHERE "+r.scope()+" AND CONCAT_WS(' ',"+Arrays.stream(r.search().split(",")).map(s->"r."+s).collect(java.util.stream.Collectors.joining(","))+") LIKE ? ESCAPE '!'";
        if(!state.equals("all"))where+=" AND "+(state.equals("inactive")?"NOT ("+available(r)+")":available(r));
        long total=count("SELECT COUNT(*) FROM "+r.table()+" r"+where,like(search));
        int pages=Math.max(1,(int)((total+19)/20));page=Math.max(0,Math.min(page,pages-1));
        String order=switch(sort){case "name"->"r."+r.label()+",r.id";case "oldest"->"r.id";default->"r.id DESC";};
        var items=jdbc.queryForList("SELECT "+select(r)+" FROM "+r.table()+" r"+where+" ORDER BY "+order+" LIMIT 20 OFFSET ?",like(search),page*20);
        items.forEach(row->normalize(r,row));return new Page(items,total,page,pages,20);
    }
    private void normalize(Resource r,Map<String,Object> row) {
        for(String key:List.of("enabled","active","published","class_only","is_homeroom_teacher")) if(row.containsKey(key))row.put(key,bool(row.get(key)));
        boolean inactive=r.state()!=null && switch(r.state()) {
            case "enabled","active" -> !bool(row.get(r.state()));
            case "deleted_at","archived_at","cancelled_at" -> row.get(r.state())!=null;
            case "status" -> List.of("REMOVED","REVOKED","HIDDEN","RESOLVED","DISMISSED").contains(String.valueOf(row.get("status")));
            case "scan_status" -> "REJECTED".equals(row.get("scan_status")) || "INFECTED".equals(row.get("scan_status"));
            default -> false;
        };
        row.put("inactive",inactive);
        row.put("stateLabel",r.state()==null?"Đang sử dụng":switch(r.state()) {
            case "enabled" -> inactive?"Đã khóa":"Hoạt động";
            case "active" -> inactive?"Ngừng sử dụng":"Hoạt động";
            case "deleted_at","archived_at" -> inactive?"Đã lưu trữ":"Hoạt động";
            case "cancelled_at" -> inactive?"Đã hủy":"Đang giao";
            default -> String.valueOf(row.get(r.state()));
        });
    }
    public Map<String,Object> detail(AuthenticatedUser user,String key,long id) {
        require(user);var r=get(key);var rows=jdbc.queryForList("SELECT "+select(r)+" FROM "+r.table()+" r WHERE r.id=? AND "+r.scope(),id);
        if(rows.isEmpty())throw missing();var row=rows.getFirst();normalize(r,row);
        if(key.equals("lessons")) {
            var versions=jdbc.queryForList("SELECT objectives,content_html FROM lesson_versions WHERE lesson_id=? ORDER BY version_number DESC LIMIT 1",id);
            if(!versions.isEmpty())row.putAll(versions.getFirst());
        }
        if(key.equals("lessons") || key.equals("quizzes")) {
            boolean lesson=key.equals("lessons");
            row.put("classIds",jdbc.queryForList("SELECT classroom_id FROM "+(lesson?"class_lessons WHERE lesson_id":"quiz_classes WHERE quiz_id")+"=?",Long.class,id));
        }
        if(key.equals("questions")) {
            var options=jdbc.queryForList("SELECT option_text,is_correct FROM question_options WHERE question_id=? ORDER BY display_order,id",id);
            row.put("options",options.stream().map(o->String.valueOf(o.get("option_text"))).collect(java.util.stream.Collectors.joining("\n")));
            for(int i=0;i<options.size();i++)if(bool(options.get(i).get("is_correct")))row.put("correctIndex",i+1);
        }
        if(key.equals("quizzes"))row.put("questionIds",jdbc.queryForList("SELECT question_id FROM quiz_items WHERE quiz_id=? ORDER BY display_order",Long.class,id));
        return row;
    }
    public List<Map<String,Object>> options(AuthenticatedUser user,String key,String search) {
        require(user);var r=get(key);if(r.readOnly())throw bad("Danh mục lựa chọn không hợp lệ.");
        if(search==null || search.length()>160)throw bad("Từ khóa không hợp lệ.");
        String label="CONCAT('#',r.id,' · ',LEFT(CAST(r."+r.label()+" AS CHAR),140))";
        if(r.table().equals("users"))label="CONCAT(r.full_name,' · ',r.username)";
        return jdbc.queryForList("SELECT r.id,"+label+" label FROM "+r.table()+" r WHERE "+r.scope()+" AND "+available(r)+" AND "+label+" LIKE ? ESCAPE '!' ORDER BY r."+r.label()+",r.id LIMIT 50",like(search.trim()));
    }
    public Map<String,List<Map<String,Object>>> selectedOptions(AuthenticatedUser user,Resource resource,Map<String,Object> item) {
        require(user);var result=new HashMap<String,List<Map<String,Object>>>();
        for(var field:resource.fields()) if(field.reference()!=null) {
            var target=get(field.reference());var ids=new ArrayList<Long>();Object value=item.get(field.key());
            if(value instanceof List<?> list)for(Object id:list)ids.add(((Number)id).longValue());else if(value instanceof Number number)ids.add(number.longValue());
            var options=new ArrayList<Map<String,Object>>();
            for(long id:ids)options.addAll(jdbc.queryForList("SELECT id,CONCAT('#',id,' · ',LEFT(CAST("+target.label()+" AS CHAR),140)) label FROM "+target.table()+" WHERE id=?",id));
            result.put(field.key(),options);
        }
        return result;
    }
    private static boolean bool(Object value) {return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()!=0 || "true".equals(value);}
    private static long id(Object value) {try{return Long.parseLong(String.valueOf(value));}catch(RuntimeException ex){throw bad("ID không hợp lệ.");}}
    private LinkedHashMap<String,Object> validate(Resource r,Map<String,Object> input,Map<String,Object> old) {
        var data=new LinkedHashMap<String,Object>();
        for(var f:r.fields()) {
            Object raw=input.get(f.key());String text=raw==null?"":String.valueOf(raw).trim();Object value=text.isEmpty()?null:text;
            try {
                switch(f.type()) {
                    case "boolean" -> {if(!(raw instanceof Boolean))throw bad(f.label()+": chọn Có hoặc Không.");value=raw;}
                    case "number" -> {
                        if(f.key().equals("required_score")) {var n=new java.math.BigDecimal(text);if(n.signum()<0 || n.compareTo(java.math.BigDecimal.TEN)>0 || n.scale()>2)throw bad("Điểm yêu cầu cần nằm trong 0–10, tối đa 2 chữ số thập phân.");value=n;}
                        else {int n=Integer.parseInt(text);int min=f.key().equals("display_order")?0:1;if(n<min || n>f.max())throw bad(f.label()+": giá trị ngoài giới hạn.");value=n;}
                    }
                    case "reference" -> {if(value!=null){long ref=id(value);Resource target=get(f.reference());boolean unchanged=old!=null && old.get(f.key()) instanceof Number number && number.longValue()==ref;
                        if(ref<1 || !exists("SELECT EXISTS(SELECT 1 FROM "+target.table()+" r WHERE id=? AND "+target.scope()+(unchanged?"":" AND "+available(target))+")",ref))throw bad(f.label()+": bản ghi không tồn tại hoặc đã ngừng hoạt động.");value=ref;}}
                    case "multiple" -> {if(raw==null)value=List.of();else {if(!(raw instanceof List<?> list) || list.size()>200)throw bad(f.label()+": tối đa 200 mục.");var ids=((List<?>)raw).stream().map(AdminWorkspaceService::id).toList();if(ids.stream().distinct().count()!=ids.size())throw bad("Không chọn trùng mục.");Resource target=get(f.reference());for(long ref:ids)if(ref<1 || !exists("SELECT EXISTS(SELECT 1 FROM "+target.table()+" r WHERE id=? AND "+target.scope()+" AND "+available(target)+")",ref))throw bad(f.label()+": lựa chọn không hợp lệ.");value=ids;}}
                    case "select" -> {if(!f.choices().contains(text))throw bad(f.label()+": lựa chọn không hợp lệ.");}
                    case "date" -> {if(value!=null)value=java.sql.Date.valueOf(LocalDate.parse(text));}
                    case "datetime-local" -> {if(value!=null)value=Timestamp.from(Instant.parse(text));}
                    case "password" -> {value=raw==null || String.valueOf(raw).isEmpty()?null:String.valueOf(raw);if(value!=null && (value.toString().length()<8 || value.toString().getBytes(StandardCharsets.UTF_8).length>72))throw bad("Mật khẩu cần ít nhất 8 ký tự, tối đa 72 byte UTF-8.");}
                    default -> {if(text.length()>f.max())throw bad(f.label()+": vượt quá "+f.max()+" ký tự.");}
                }
            } catch(NumberFormatException | DateTimeException ex) {throw bad(f.label()+": định dạng không hợp lệ.");}
            if(f.required() && value==null)throw bad(f.label()+": không được để trống.");data.put(f.key(),value);
        }
        return data;
    }
    @Transactional
    public long save(AuthenticatedUser user,String key,Long recordId,Map<String,Object> input) {
        require(user);var r=get(key);if(r.readOnly())throw bad("Phân hệ chỉ cho phép xem.");
        // Serializes administrator account edits; prevents simultaneous removal of the last admin.
        if(r.table().equals("users"))jdbc.queryForList("SELECT id FROM users WHERE role='ADMIN' ORDER BY id FOR UPDATE");
        Map<String,Object> old=null;
        if(recordId!=null) {
            var rows=jdbc.queryForList("SELECT * FROM "+r.table()+" WHERE id=? AND "+r.scope()+" FOR UPDATE",recordId);if(rows.isEmpty())throw missing();old=rows.getFirst();
            if(List.of("deleted_at","archived_at","cancelled_at").contains(r.state()==null?"":r.state()) && old.get(r.state())!=null)throw conflict("Khôi phục bản ghi trước khi chỉnh sửa.");
        }
        var data=validate(r,input,old);boolean create=recordId==null;
        if(r.table().equals("users")) {
            String username=String.valueOf(data.get("username"));if(!username.matches("[A-Za-z0-9._-]{3,80}"))throw bad("Tên đăng nhập cần 3–80 ký tự chữ, số, dấu chấm, gạch ngang hoặc gạch dưới.");
            String email=String.valueOf(data.get("email")).toLowerCase(Locale.ROOT);if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw bad("Email không hợp lệ.");data.put("email",email);
            Object password=data.remove("password");if(create && password==null)throw bad("Nhập mật khẩu cho tài khoản mới.");
            if(!create && !bool(data.get("enabled")))protectAdmin(user,recordId,old);
            if(password!=null)data.put("password_hash",passwords.encode(password.toString()));
            data.put("role",role(key));
        }
        if(key.equals("academic-years") && ((java.sql.Date)data.get("ends_on")).before((java.sql.Date)data.get("starts_on")))throw bad("Ngày kết thúc phải sau ngày bắt đầu.");
        if(key.equals("topics"))data.put("subject_id",jdbc.queryForObject("SELECT subject_id FROM courses WHERE id=?",Long.class,data.get("course_id")));
        if(key.equals("courses") && !create && !Objects.equals(((Number)old.get("subject_id")).longValue(),data.get("subject_id"))) {
            if(count("SELECT COUNT(*) FROM topics WHERE course_id=?",recordId)>0)throw conflict("Khóa đã có chủ đề; không thể đổi môn học.");
        }
        if(key.equals("parent-links")) {
            if(!create && (id(old.get("parent_id"))!=id(data.get("parent_id")) || id(old.get("student_id"))!=id(data.get("student_id"))))throw conflict("Hãy thu hồi liên kết cũ và tạo liên kết mới, không chuyển lịch sử sang người khác.");
            data.put("confirmed_at","VERIFIED".equals(data.get("status"))?Timestamp.from(Instant.now()):null);data.put("revoked_at","REVOKED".equals(data.get("status"))?Timestamp.from(Instant.now()):null);
        }
        Object classIds=data.remove("classIds"),questionIds=data.remove("questionIds");
        Object objectives=data.remove("objectives"),html=data.remove("content_html"),options=data.remove("options"),correctIndex=data.remove("correctIndex");
        if(key.equals("lessons")) {
            html=Jsoup.clean(String.valueOf(html),Safelist.basic().addTags("h2","h3","h4"));if(Jsoup.parse(html.toString()).text().isBlank())throw bad("Nội dung bài học không được rỗng.");
            validatePrerequisite(recordId,data.get("prerequisite_id"));
            if(create){data.put("slug","admin-"+UUID.randomUUID());data.put("created_by",user.id());}
        }
        if(key.equals("quizzes")) {
            if(!(questionIds instanceof List<?> ids) || ids.isEmpty())throw bad("Chọn ít nhất một câu hỏi.");
            if("TEST".equals(data.get("mode")) && data.get("reveal_after")==null)throw bad("Bài kiểm tra cần thời điểm công bố đáp án.");
            if("TEST".equals(data.get("mode")) && bool(data.get("published")) && !((Timestamp)data.get("reveal_after")).toInstant().isAfter(Instant.now().plusSeconds(((Number)data.get("duration_minutes")).longValue()*60)))throw bad("Thời điểm công bố đáp án phải sau hiện tại cộng thời lượng làm bài.");
            if(create)data.put("created_by",user.id());
            if(data.get("lesson_id")!=null) {
                long course=jdbc.queryForObject("SELECT t.course_id FROM lessons l JOIN topics t ON t.id=l.topic_id WHERE l.id=?",Long.class,data.get("lesson_id"));
                if(data.get("course_id")!=null && id(data.get("course_id"))!=course)throw bad("Khóa học không khớp bài học.");data.put("course_id",course);
            }
        }
        if((key.equals("lessons") || key.equals("quizzes")) && bool(data.get("class_only")) && ((List<?>)classIds).isEmpty())throw bad("Nội dung riêng cần ít nhất một lớp.");
        if(key.equals("quizzes") && !create && bool(data.get("class_only"))) {
            for(long assigned:jdbc.queryForList("SELECT DISTINCT classroom_id FROM class_assignments WHERE quiz_id=? AND cancelled_at IS NULL",Long.class,recordId))
                if(!((List<?>)classIds).contains(assigned))throw conflict("Không thể bỏ chia sẻ với lớp còn bài giao đang hoạt động. Hãy hủy bài giao trước.");
        }
        if(key.equals("questions"))validateOptions(String.valueOf(options),((Number)correctIndex).intValue());
        if(key.equals("assignments")) {
            if(((Timestamp)data.get("due_at")).toInstant().isBefore(Instant.now()))throw bad("Hạn nộp phải ở tương lai.");
            long quiz=id(data.get("quiz_id")),classId=id(data.get("classroom_id"));
            jdbc.queryForList("SELECT id FROM quizzes WHERE id=? FOR UPDATE",quiz);lockClass(classId);
            if(!exists("SELECT EXISTS(SELECT 1 FROM quizzes q WHERE q.id=? AND q.published=TRUE AND q.deleted_at IS NULL AND (q.class_only=FALSE OR EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.quiz_id=q.id AND qc.classroom_id=?)))",quiz,classId))throw bad("Quiz chưa xuất bản hoặc chưa chia sẻ với lớp.");
            if(!create && (id(old.get("quiz_id"))!=quiz || id(old.get("classroom_id"))!=classId))throw conflict("Chỉ được sửa hạn nộp. Hãy hủy và tạo bài giao mới để đổi lớp/quiz.");
            if(create)data.put("created_by",user.id());
        }
        if(key.equals("feedback")) {
            if(create)data.put("teacher_id",user.id());else {if(id(old.get("student_id"))!=id(data.get("student_id")))throw conflict("Không thể chuyển nhận xét sang học sinh khác.");jdbc.update("INSERT INTO teacher_feedback_revisions(feedback_id,actor_id,body) VALUES(?,?,?)",recordId,user.id(),old.get("body"));data.put("updated_at",Timestamp.from(Instant.now()));}
        }
        if(create && List.of("community-posts","community-comments","forum-threads","forum-posts").contains(key))data.put("author_id",user.id());
        if(create && key.equals("question-banks"))data.put("created_by",user.id());
        if(key.equals("content-reports")) {
            if((data.get("thread_id")==null)==(data.get("post_id")==null))throw bad("Chọn đúng một đối tượng: chủ đề hoặc trả lời hỏi đáp.");
            if(!create && (!Objects.equals(old.get("thread_id"),data.get("thread_id")) || !Objects.equals(old.get("post_id"),data.get("post_id")) || !Objects.equals(old.get("reason"),data.get("reason"))))throw conflict("Báo cáo đã tạo chỉ được đổi trạng thái, không sửa bằng chứng.");
            if(create)data.put("reporter_id",user.id());
            boolean handled=!"OPEN".equals(data.get("status"));data.put("handled_by",handled?user.id():null);data.put("handled_at",handled?Timestamp.from(Instant.now()):null);
        }
        if(key.equals("files")) {
            if(create)throw bad("Tải tệp bằng quy trình upload trực tiếp và kiểm tra an toàn.");
            String filename=String.valueOf(data.get("original_filename"));
            if(filename.contains("/") || filename.contains("\\") || filename.chars().anyMatch(Character::isISOControl))throw bad("Tên tệp không được chứa đường dẫn hoặc ký tự điều khiển.");
        }
        long saved=create?insert(r.table(),data):recordId;
        if(!create)update(r.table(),saved,data);
        if(r.table().equals("users") && !create)revokeSessions(saved);
        if(key.equals("lessons")) {
            int version=jdbc.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM lesson_versions WHERE lesson_id=?",Integer.class,saved);
            jdbc.update("UPDATE lesson_versions SET status='ARCHIVED' WHERE lesson_id=?",saved);
            jdbc.update("INSERT INTO lesson_versions(lesson_id,version_number,objectives,content_html,status,created_by) VALUES(?,?,?,?,?,?)",saved,version,objectives,html,data.get("status"),user.id());
        }
        if(key.equals("lessons") || key.equals("quizzes")) {
            String table=key.equals("lessons")?"class_lessons":"quiz_classes",column=key.equals("lessons")?"lesson_id":"quiz_id";
            jdbc.update("DELETE FROM "+table+" WHERE "+column+"=?",saved);
            for(Object classId:(List<?>)classIds)jdbc.update("INSERT INTO "+table+"("+column+",classroom_id) VALUES(?,?)",saved,classId);
        }
        if(key.equals("questions")) {
            jdbc.update("DELETE FROM question_options WHERE question_id=?",saved);
            String[] values=String.valueOf(options).split("\\R");for(int i=0;i<values.length;i++)jdbc.update("INSERT INTO question_options(question_id,option_text,is_correct,display_order) VALUES(?,?,?,?)",saved,values[i].trim(),i+1==((Number)correctIndex).intValue(),i+1);
        }
        if(key.equals("quizzes")) {
            var points=new HashMap<Long,Object>();if(!create)jdbc.queryForList("SELECT question_id,points FROM quiz_items WHERE quiz_id=?",saved).forEach(row->points.put(id(row.get("question_id")),row.get("points")));
            jdbc.update("DELETE FROM quiz_items WHERE quiz_id=?",saved);int order=0;
            for(Object question:(List<?>)questionIds) {
                if(!exists("SELECT EXISTS(SELECT 1 FROM questions q WHERE q.id=? AND q.active=TRUE AND (SELECT COUNT(*) FROM question_options WHERE question_id=q.id)>=2 AND (SELECT COUNT(*) FROM question_options WHERE question_id=q.id AND is_correct=TRUE)=1)",question))throw bad("Câu hỏi chưa có đáp án hợp lệ.");
                jdbc.update("INSERT INTO quiz_items(quiz_id,question_id,display_order,points) VALUES(?,?,?,?)",saved,question,++order,points.getOrDefault(id(question),1));
            }
        }
        audit(user,create?"CREATE":"UPDATE",key,saved);return saved;
    }
    private void validatePrerequisite(Long current,Object prerequisite) {
        if(prerequisite==null)return;long next=id(prerequisite);Set<Long> visited=new HashSet<>();if(current!=null)visited.add(current);
        while(true) {
            if(!visited.add(next))throw bad("Bài tiên quyết tạo vòng lặp.");
            var rows=jdbc.queryForList("SELECT prerequisite_id,status,deleted_at FROM lessons WHERE id=?",next);if(rows.isEmpty() || !"PUBLISHED".equals(rows.getFirst().get("status")) || rows.getFirst().get("deleted_at")!=null)throw bad("Bài tiên quyết phải được xuất bản.");
            Object parent=rows.getFirst().get("prerequisite_id");if(parent==null)break;next=id(parent);
        }
    }
    private static void validateOptions(String options,int correct) {
        String[] values=options.split("\\R");if(values.length<2 || values.length>6 || correct<1 || correct>values.length)throw bad("Cần 2–6 đáp án và một đáp án đúng hợp lệ.");
        for(String value:values)if(value.isBlank() || value.trim().length()>1000)throw bad("Mỗi đáp án cần 1–1000 ký tự.");
    }
    private void protectAdmin(AuthenticatedUser actor,long target,Map<String,Object> old) {
        if(target==actor.id())throw conflict("Không thể vô hiệu hóa chính tài khoản đang đăng nhập.");
        if("ADMIN".equals(old.get("role")) && bool(old.get("enabled")) && count("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND enabled=TRUE")<=1)throw conflict("Phải giữ ít nhất một quản trị viên hoạt động.");
    }
    private void revokeSessions(long userId) {
        jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE id=?",userId);
        jdbc.update("UPDATE refresh_sessions SET revoked_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND revoked_at IS NULL",userId);
    }
    private long insert(String table,LinkedHashMap<String,Object> data) {
        var key=new GeneratedKeyHolder();String sql="INSERT INTO "+table+"("+String.join(",",data.keySet())+") VALUES("+String.join(",",Collections.nCopies(data.size(),"?"))+")";
        jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);int i=0;for(Object value:data.values())s.setObject(++i,value);return s;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private void update(String table,long id,LinkedHashMap<String,Object> data) {
        String assignments=data.keySet().stream().map(k->k+"=?").collect(java.util.stream.Collectors.joining(","));
        var args=new ArrayList<>(data.values());args.add(id);jdbc.update("UPDATE "+table+" SET "+assignments+" WHERE id=?",args.toArray());
    }
    @Transactional
    public void remove(AuthenticatedUser user,String key,long id) {
        require(user);var r=get(key);if(r.readOnly())throw bad("Dữ liệu này chỉ cho phép xem.");
        if(r.table().equals("users"))jdbc.queryForList("SELECT id FROM users WHERE role='ADMIN' ORDER BY id FOR UPDATE");
        if(jdbc.queryForList("SELECT id FROM "+r.table()+" WHERE id=? AND "+r.scope()+" FOR UPDATE",id).isEmpty())throw missing();
        var old=detail(user,key,id);
        if(r.table().equals("users")){protectAdmin(user,id,old);jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",id);revokeSessions(id);}
        else if(r.state()==null) {
            // Foreign keys deliberately reject deletion of referenced catalogue data.
            jdbc.update("DELETE FROM "+r.table()+" WHERE id=?",id);
        } else switch(r.state()) {
            case "active" -> jdbc.update("UPDATE "+r.table()+" SET active=FALSE WHERE id=?",id);
            case "archived_at","deleted_at","cancelled_at" -> {
                if(key.equals("lessons") && count("SELECT COUNT(*) FROM lessons WHERE prerequisite_id=? AND deleted_at IS NULL",id)>0)throw conflict("Bài học đang là tiên quyết. Cập nhật các bài phụ thuộc trước khi lưu trữ.");
                jdbc.update("UPDATE "+r.table()+" SET "+r.state()+"=CURRENT_TIMESTAMP(6) WHERE id=?",id);
                if(key.equals("lessons")){jdbc.update("UPDATE lessons SET status='ARCHIVED' WHERE id=?",id);jdbc.update("UPDATE lesson_versions SET status='ARCHIVED' WHERE lesson_id=?",id);}
                if(key.equals("quizzes")) {
                    jdbc.update("UPDATE quizzes SET published=FALSE WHERE id=?",id);
                    jdbc.update("UPDATE class_assignments SET cancelled_at=CURRENT_TIMESTAMP(6) WHERE quiz_id=? AND cancelled_at IS NULL",id);
                }
            }
            case "status" -> {
                jdbc.update("UPDATE "+r.table()+" SET status=? WHERE id=?",key.equals("parent-links")?"REVOKED":key.equals("content-reports")?"DISMISSED":"REMOVED",id);
                if(key.equals("parent-links"))jdbc.update("UPDATE parent_student_links SET revoked_at=CURRENT_TIMESTAMP(6) WHERE id=?",id);
                if(key.equals("content-reports"))jdbc.update("UPDATE content_reports SET handled_by=?,handled_at=CURRENT_TIMESTAMP(6) WHERE id=?",user.id(),id);
            }
            case "scan_status" -> jdbc.update("UPDATE stored_files SET scan_status='REJECTED' WHERE id=?",id);
            default -> throw bad("Không hỗ trợ xóa dữ liệu này.");
        }
        audit(user,"REMOVE",key,id);
    }
    @Transactional
    public void restore(AuthenticatedUser user,String key,long id) {
        require(user);var r=get(key);if(r.readOnly() || r.state()==null || List.of("assignments","files").contains(key))throw bad("Không hỗ trợ khôi phục. Bài giao đã hủy hoặc tệp bị chặn cần tạo mới.");
        if(jdbc.queryForList("SELECT id FROM "+r.table()+" WHERE id=? AND "+r.scope()+" FOR UPDATE",id).isEmpty())throw missing();
        detail(user,key,id);
        switch(r.state()) {
            case "enabled","active" -> jdbc.update("UPDATE "+r.table()+" SET "+r.state()+"=TRUE WHERE id=?",id);
            case "archived_at","deleted_at","cancelled_at" -> {
                jdbc.update("UPDATE "+r.table()+" SET "+r.state()+"=NULL WHERE id=?",id);
                if(key.equals("lessons")){jdbc.update("UPDATE lessons SET status='DRAFT' WHERE id=?",id);jdbc.update("UPDATE lesson_versions SET status='DRAFT' WHERE lesson_id=? AND version_number=(SELECT v FROM (SELECT MAX(version_number) v FROM lesson_versions WHERE lesson_id=?) latest)",id,id);}
            }
            case "status" -> {
                jdbc.update("UPDATE "+r.table()+" SET status=? WHERE id=?",key.equals("parent-links")?"PENDING":key.equals("content-reports")?"OPEN":"PUBLISHED",id);
                if(key.equals("content-reports"))jdbc.update("UPDATE content_reports SET handled_by=NULL,handled_at=NULL WHERE id=?",id);
            }
            default -> throw bad("Không hỗ trợ khôi phục.");
        }
        audit(user,"RESTORE",key,id);
    }
    public Map<String,Object> overview(AuthenticatedUser user) {
        require(user);var result=new HashMap<String,Object>();
        result.put("teachers",count("SELECT COUNT(*) FROM users WHERE role='TEACHER' AND enabled=TRUE"));
        result.put("students",count("SELECT COUNT(*) FROM users WHERE role='STUDENT' AND enabled=TRUE"));
        result.put("classes",count("SELECT COUNT(*) FROM classrooms WHERE archived_at IS NULL"));
        result.put("courses",count("SELECT COUNT(*) FROM courses"));
        result.put("drafts",count("SELECT COUNT(*) FROM lessons WHERE status='DRAFT' AND deleted_at IS NULL"));
        result.put("reports",count("SELECT COUNT(*) FROM community_reports"));
        result.put("unassigned",jdbc.queryForList("SELECT c.id,c.name,y.name academic_year FROM classrooms c JOIN academic_years y ON y.id=c.academic_year_id WHERE c.archived_at IS NULL AND NOT EXISTS(SELECT 1 FROM class_teachers ct JOIN users u ON u.id=ct.teacher_id AND u.enabled=TRUE WHERE ct.classroom_id=c.id) ORDER BY c.id DESC LIMIT 6"));
        result.put("recent",jdbc.queryForList("SELECT a.action,a.resource_type,a.resource_id,a.created_at,u.full_name actor_name FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_user_id ORDER BY a.id DESC LIMIT 8"));
        result.put("classrooms",jdbc.queryForList("SELECT c.id,c.name,y.name academic_year,(SELECT COUNT(*) FROM class_enrollments e WHERE e.classroom_id=c.id AND e.active=TRUE) students,(SELECT GROUP_CONCAT(u.full_name ORDER BY ct.is_homeroom_teacher DESC SEPARATOR ', ') FROM class_teachers ct JOIN users u ON u.id=ct.teacher_id WHERE ct.classroom_id=c.id AND u.enabled=TRUE) teachers FROM classrooms c JOIN academic_years y ON y.id=c.academic_year_id WHERE c.archived_at IS NULL ORDER BY c.id DESC LIMIT 6"));
        return result;
    }
    public Map<String,Object> related(AuthenticatedUser user,String key,long id) {
        require(user);detail(user,key,id);var result=new HashMap<String,Object>();
        if(key.equals("classes")) {
            result.put("teachers",jdbc.queryForList("SELECT u.id,u.full_name,u.username,u.enabled,ct.is_homeroom_teacher FROM class_teachers ct JOIN users u ON u.id=ct.teacher_id WHERE ct.classroom_id=? ORDER BY ct.is_homeroom_teacher DESC,u.full_name",id));
            result.put("students",jdbc.queryForList("SELECT u.id,u.full_name,u.username,e.active FROM class_enrollments e JOIN users u ON u.id=e.student_id WHERE e.classroom_id=? ORDER BY e.active DESC,u.full_name LIMIT 200",id));
            result.put("assignments",jdbc.queryForList("SELECT a.id,q.title,a.due_at,a.cancelled_at FROM class_assignments a JOIN quizzes q ON q.id=a.quiz_id WHERE a.classroom_id=? ORDER BY a.id DESC LIMIT 100",id));
            result.put("results",jdbc.queryForList("SELECT u.full_name,COUNT(a.id) attempts,ROUND(AVG(a.score),2) average_score FROM class_enrollments e JOIN users u ON u.id=e.student_id LEFT JOIN quiz_attempts a ON a.student_id=e.student_id AND a.status='GRADED' AND EXISTS(SELECT 1 FROM class_assignments ca WHERE ca.classroom_id=e.classroom_id AND ca.quiz_id=a.quiz_id) WHERE e.classroom_id=? GROUP BY u.id,u.full_name ORDER BY u.full_name LIMIT 200",id));
        }
        if(key.equals("teachers")) {
            result.put("classes",jdbc.queryForList("SELECT c.id,c.name,c.grade_level,ct.is_homeroom_teacher FROM classrooms c JOIN class_teachers ct ON ct.classroom_id=c.id WHERE ct.teacher_id=? ORDER BY c.name",id));
            result.put("lessons",jdbc.queryForList("SELECT id,title,status FROM lessons WHERE created_by=? AND deleted_at IS NULL ORDER BY id DESC LIMIT 50",id));
        }
        if(key.equals("students")) {
            result.put("classes",jdbc.queryForList("SELECT c.id,c.name,c.grade_level FROM classrooms c JOIN class_enrollments e ON e.classroom_id=c.id WHERE e.student_id=? AND e.active=TRUE ORDER BY c.name",id));
            result.put("progress",jdbc.queryForList("SELECT l.title,p.completion_percent,p.status FROM lesson_progress p JOIN lessons l ON l.id=p.lesson_id WHERE p.student_id=? ORDER BY p.updated_at DESC LIMIT 100",id));
        }
        return result;
    }
    @Transactional
    public void assignTeacher(AuthenticatedUser user,long classId,long teacherId,boolean homeroom) {
        require(user);
        if(jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='TEACHER' AND enabled=TRUE FOR UPDATE",teacherId).isEmpty())throw bad("Giáo viên không tồn tại hoặc đã bị khóa.");
        lockClass(classId);
        var previous=jdbc.queryForList("SELECT teacher_id FROM class_teachers WHERE classroom_id=? AND is_homeroom_teacher=TRUE",Long.class,classId);
        if(homeroom)jdbc.update("UPDATE class_teachers SET is_homeroom_teacher=FALSE WHERE classroom_id=?",classId);
        jdbc.update("INSERT INTO class_teachers(classroom_id,teacher_id,is_homeroom_teacher) VALUES(?,?,?) ON DUPLICATE KEY UPDATE is_homeroom_teacher=?",classId,teacherId,homeroom,homeroom);
        relationshipAudit(user,homeroom?"ASSIGN_HOMEROOM":"ASSIGN_TEACHER",classId,"teacherId",teacherId,previous.isEmpty()?null:previous.getFirst());
    }
    @Transactional
    public void removeTeacher(AuthenticatedUser user,long classId,long teacherId) {
        require(user);lockClass(classId);if(jdbc.update("DELETE FROM class_teachers WHERE classroom_id=? AND teacher_id=?",classId,teacherId)==0)throw missing();
        relationshipAudit(user,"UNASSIGN_TEACHER",classId,"teacherId",teacherId,null);
    }
    @Transactional
    public void enrollStudent(AuthenticatedUser user,long classId,long studentId,boolean active) {
        require(user);lockClass(classId);
        if(!exists("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND role='STUDENT'"+(active?" AND enabled=TRUE":"")+")",studentId))throw bad("Học sinh không hợp lệ.");
        jdbc.update("INSERT INTO class_enrollments(classroom_id,student_id,active) VALUES(?,?,?) ON DUPLICATE KEY UPDATE active=?",classId,studentId,active,active);
        relationshipAudit(user,active?"ENROLL":"UNENROLL",classId,"studentId",studentId,null);
    }
    private void lockClass(long id) {
        var rows=jdbc.queryForList("SELECT id,archived_at FROM classrooms WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw missing();if(rows.getFirst().get("archived_at")!=null)throw conflict("Lớp đã lưu trữ, không thể thay đổi thành viên.");
    }
    public Page teachingAssignments(AuthenticatedUser user,String search,int page) {
        require(user);if(search.length()>160)throw bad("Từ khóa quá dài.");
        String from=" FROM class_teachers ct JOIN classrooms c ON c.id=ct.classroom_id JOIN users u ON u.id=ct.teacher_id JOIN academic_years y ON y.id=c.academic_year_id WHERE CONCAT_WS(' ',c.name,u.full_name,u.username,y.name) LIKE ? ESCAPE '!'";
        long total=count("SELECT COUNT(*)"+from,like(search));int pages=Math.max(1,(int)((total+19)/20));page=Math.max(0,Math.min(page,pages-1));
        var rows=jdbc.queryForList("SELECT c.id class_id,c.name class_name,u.id teacher_id,u.full_name,u.username,ct.is_homeroom_teacher,y.name academic_year,c.archived_at"+from+" ORDER BY c.id DESC,ct.is_homeroom_teacher DESC,u.full_name LIMIT 20 OFFSET ?",like(search),page*20);
        return new Page(rows,total,page,pages,20);
    }
    private void audit(AuthenticatedUser actor,String action,String type,long id) {jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,?,?,?)",actor.id(),"ADMIN_"+action,type,String.valueOf(id));}
    private void relationshipAudit(AuthenticatedUser actor,String action,long classId,String memberKey,long memberId,Long previousLead) {
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id,metadata_json) VALUES(?,?,'classroom',?,JSON_OBJECT(?,?, 'previousLeadId',?))",actor.id(),"ADMIN_"+action,String.valueOf(classId),memberKey,memberId,previousLead);
    }
}