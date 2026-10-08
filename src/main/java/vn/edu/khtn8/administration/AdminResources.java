package vn.edu.khtn8.administration;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Closed registry: SQL identifiers and expressions never come from HTTP input. */
public final class AdminResources {
    private AdminResources() { }
    public record Field(String key,String label,String type,boolean required,int max,String reference,List<String> choices,String help) { }
    public record Column(String key,String label) { }
    public record Resource(String key,String title,String group,String icon,String table,String label,String search,
            String scope,String state,List<Field> fields,List<Column> columns,boolean readOnly) { }
    private static Field text(String key,String label,int max) {return new Field(key,label,"text",true,max,null,List.of(),"");}
    private static Field area(String key,String label,int max,boolean required) {return new Field(key,label,"textarea",required,max,null,List.of(),"");}
    private static Field number(String key,String label,int max) {return new Field(key,label,"number",true,max,null,List.of(),"");}
    private static Field ref(String key,String label,String resource,boolean required) {return new Field(key,label,"reference",required,0,resource,List.of(),"");}
    private static Field multi(String key,String label,String resource) {return new Field(key,label,"multiple",false,200,resource,List.of(),"");}
    private static Field choice(String key,String label,String...values) {return new Field(key,label,"select",true,0,null,List.of(values),"");}
    private static Field flag(String key,String label) {return new Field(key,label,"boolean",true,0,null,List.of(),"");}
    private static Field date(String key,String label,String type) {return new Field(key,label,type,true,0,null,List.of(),"");}
    private static List<Column> cols(String...pairs) {
        var result=new ArrayList<Column>();for(int i=0;i<pairs.length;i+=2)result.add(new Column(pairs[i],pairs[i+1]));return List.copyOf(result);
    }
    private static final Map<String,Resource> ALL=new LinkedHashMap<>();
    private static void add(String key,String title,String group,String icon,String table,String label,String search,String scope,String state,List<Field> fields,List<Column> columns) {
        ALL.put(key,new Resource(key,title,group,icon,table,label,search,scope,state,fields,columns,false));
    }
    private static void users(String key,String title,String role) {
        add(key,title,"Con người & lớp học","users","users","full_name","full_name,username,email","role='"+role+"'","enabled",
                List.of(text("full_name","Họ và tên",160),text("username","Tên đăng nhập",80),new Field("email","Email","email",true,160,null,List.of(),""),
                        new Field("password","Mật khẩu mới","password",false,72,null,List.of(),"Bắt buộc khi tạo. Để trống khi sửa để giữ mật khẩu. Tối thiểu 8 ký tự, tối đa 72 byte UTF-8."),flag("enabled","Tài khoản hoạt động")),
                cols("full_name","Họ và tên","username","Tên đăng nhập","email","Email","class_count","Số lớp"));
    }
    static {
        users("teachers","Giáo viên","TEACHER");users("students","Học sinh","STUDENT");users("parents","Phụ huynh","PARENT");
        add("classes","Lớp học","Con người & lớp học","cap","classrooms","name","name","1=1","archived_at",
                List.of(text("name","Tên lớp",80),ref("academic_year_id","Năm học","academic-years",true),number("grade_level","Khối lớp (1–12)",12)),
                cols("name","Lớp học","academic_year_id_label","Năm học","grade_level","Khối","teacher_count","Giáo viên","student_count","Học sinh"));
        add("academic-years","Năm học","Con người & lớp học","book","academic_years","name","name","1=1","active",
                List.of(text("name","Tên năm học",30),date("starts_on","Ngày bắt đầu","date"),date("ends_on","Ngày kết thúc","date"),flag("active","Đang hoạt động")),cols("name","Năm học","starts_on","Bắt đầu","ends_on","Kết thúc"));
        add("parent-links","Liên kết gia đình","Con người & lớp học","users","parent_student_links","id","status","1=1","status",
                List.of(ref("parent_id","Phụ huynh","parents",true),ref("student_id","Học sinh","students",true),choice("status","Trạng thái","PENDING","VERIFIED","REVOKED")),cols("parent_id_label","Phụ huynh","student_id_label","Học sinh"));
        add("subjects","Môn học","Nội dung & học tập","book","subjects","name","name,description","1=1",null,
                List.of(text("name","Tên môn học",160),number("grade_level","Khối lớp (1–12)",12),area("description","Mô tả",5000,false)),cols("name","Môn học","grade_level","Khối","description","Mô tả"));
        add("courses","Khóa học","Nội dung & học tập","cap","courses","title","title,summary","1=1",null,
                List.of(text("title","Tên khóa học",200),area("summary","Tóm tắt",500,true),ref("subject_id","Môn học","subjects",true),ref("teacher_id","Giáo viên phụ trách","teachers",false),choice("kind","Loại khóa","FOUNDATION","TOPIC","REVISION"),choice("theme","Màu nhận diện","blue","purple","green","orange")),
                cols("title","Khóa học","subject_id_label","Môn học","teacher_id_label","Giáo viên","kind","Loại"));
        add("topics","Chủ đề","Nội dung & học tập","book","topics","name","name","1=1",null,
                List.of(text("name","Tên chủ đề",160),ref("course_id","Khóa học","courses",true),number("display_order","Thứ tự (từ 0)",10000)),cols("name","Chủ đề","course_id_label","Khóa học","display_order","Thứ tự"));
        add("lessons","Bài học","Nội dung & học tập","book","lessons","title","title,summary","1=1","deleted_at",
                List.of(text("title","Tiêu đề",200),ref("topic_id","Chủ đề","topics",true),area("summary","Tóm tắt",500,true),number("duration_minutes","Thời lượng (phút)",180),
                        area("objectives","Mục tiêu",2000,true),area("content_html","Nội dung HTML (được lọc an toàn)",100000,true),choice("status","Xuất bản","DRAFT","PUBLISHED","ARCHIVED"),
                        ref("prerequisite_id","Bài tiên quyết","lessons",false),number("required_score","Điểm yêu cầu (0–10)",10),flag("class_only","Chỉ chia sẻ theo lớp"),multi("classIds","Lớp được chia sẻ","classes")),
                cols("title","Bài học","topic_id_label","Chủ đề","status","Xuất bản","class_only","Theo lớp"));
        add("question-banks","Ngân hàng câu hỏi","Nội dung & học tập","quiz","question_banks","name","name","1=1",null,
                List.of(text("name","Tên ngân hàng",200),ref("subject_id","Môn học","subjects",false)),cols("name","Ngân hàng","subject_id_label","Môn học"));
        add("questions","Câu hỏi","Nội dung & học tập","quiz","questions","content","content,explanation","1=1","active",
                List.of(ref("bank_id","Ngân hàng","question-banks",true),area("content","Câu hỏi",5000,true),area("explanation","Giải thích",5000,true),choice("difficulty","Độ khó","EASY","MEDIUM","HARD"),
                        area("options","Đáp án (mỗi dòng một đáp án, 2–6 dòng)",6000,true),number("correctIndex","Số thứ tự đáp án đúng (từ 1)",6),flag("active","Cho phép sử dụng")),cols("content","Câu hỏi","bank_id_label","Ngân hàng","difficulty","Độ khó"));
        add("quizzes","Quiz & bài kiểm tra","Nội dung & học tập","quiz","quizzes","title","title","1=1","deleted_at",
                List.of(text("title","Tiêu đề",200),ref("lesson_id","Bài học","lessons",false),ref("course_id","Khóa học","courses",false),number("duration_minutes","Thời lượng (phút)",180),number("max_attempts","Số lượt tối đa",100),choice("mode","Chế độ","PRACTICE","TEST"),
                        new Field("reveal_after","Công bố đáp án (bắt buộc với bài kiểm tra)","datetime-local",false,0,null,List.of(),"Giờ trên thiết bị của bạn."),flag("published","Xuất bản"),flag("class_only","Chỉ chia sẻ theo lớp"),multi("classIds","Lớp được chia sẻ","classes"),multi("questionIds","Câu hỏi (mỗi câu 1 điểm khi tạo mới)","questions")),
                cols("title","Quiz","mode","Chế độ","published","Xuất bản","duration_minutes","Phút"));
        add("assignments","Bài đã giao","Nội dung & học tập","quiz","class_assignments","id","id","1=1","cancelled_at",
                List.of(ref("classroom_id","Lớp học","classes",true),ref("quiz_id","Quiz","quizzes",true),date("due_at","Hạn nộp","datetime-local")),cols("classroom_id_label","Lớp học","quiz_id_label","Quiz","due_at","Hạn nộp"));
        add("feedback","Nhận xét học tập","Nội dung & học tập","chat","teacher_feedback","body","body","1=1","deleted_at",
                List.of(ref("student_id","Học sinh","students",true),area("body","Nhận xét",2000,true)),cols("student_id_label","Học sinh","body","Nhận xét","teacher_name","Người viết"));
        add("forum-categories","Danh mục hỏi đáp","Vận hành","chat","forum_categories","name","name,description","1=1","active",
                List.of(text("name","Tên danh mục",120),area("description","Mô tả",500,false),ref("classroom_id","Giới hạn theo lớp","classes",false),flag("active","Hoạt động")),cols("name","Danh mục","description","Mô tả","classroom_id_label","Lớp"));
        add("forum-threads","Hỏi đáp học tập","Vận hành","chat","forum_threads","title","title,body","1=1","status",
                List.of(ref("category_id","Danh mục","forum-categories",true),text("title","Tiêu đề",240),area("body","Nội dung",10000,true),choice("status","Trạng thái","PENDING","PUBLISHED","HIDDEN","LOCKED","REMOVED")),cols("title","Tiêu đề","category_id_label","Danh mục","author_name","Tác giả"));
        add("forum-posts","Trả lời hỏi đáp","Vận hành","chat","forum_posts","body","body","1=1","status",
                List.of(ref("thread_id","Chủ đề hỏi đáp","forum-threads",true),area("body","Nội dung",10000,true),choice("status","Trạng thái","PUBLISHED","HIDDEN","REMOVED")),cols("body","Nội dung","thread_id_label","Chủ đề","author_name","Tác giả"));
        add("community-posts","Bài viết cộng đồng","Vận hành","chat","community_posts","body","body","1=1","status",
                List.of(area("body","Nội dung",3000,true),choice("status","Trạng thái","PUBLISHED","HIDDEN","REMOVED")),cols("body","Bài viết","author_name","Tác giả","created_at","Ngày tạo"));
        add("community-comments","Bình luận","Vận hành","chat","community_comments","body","body","1=1","status",
                List.of(ref("post_id","Bài viết","community-posts",true),area("body","Nội dung",1000,true),choice("status","Trạng thái","PUBLISHED","REMOVED")),cols("body","Bình luận","post_id_label","Bài viết","author_name","Tác giả"));
        add("content-reports","Báo cáo hỏi đáp","Vận hành","chat","content_reports","reason","reason","1=1","status",
                List.of(ref("thread_id","Chủ đề hỏi đáp","forum-threads",false),ref("post_id","Trả lời hỏi đáp","forum-posts",false),area("reason","Lý do",500,true),choice("status","Trạng thái","OPEN","RESOLVED","DISMISSED")),cols("reason","Lý do","thread_id_label","Chủ đề","post_id_label","Trả lời","created_at","Ngày báo cáo"));
        users("administrators","Tài khoản quản trị","ADMIN");
        ALL.put("audit-logs",new Resource("audit-logs","Nhật ký hoạt động","Hệ thống","chart","audit_logs","action","action,resource_type,resource_id","1=1",null,List.of(),cols("actor_name","Người thao tác","action","Thao tác","resource_type","Đối tượng","resource_id","ID","created_at","Thời gian"),true));
        add("files","Tài liệu & tệp tải lên","Nội dung & học tập","book","stored_files","original_filename","original_filename,content_type","1=1","scan_status",List.of(text("original_filename","Tên hiển thị của tệp",255)),cols("original_filename","Tên tệp","content_type","Định dạng","size_bytes","Dung lượng (byte)","created_at","Ngày tải"));
    }
    public static Resource get(String key) {var r=ALL.get(key);if(r==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phân hệ.");return r;}
    public static List<Resource> all() {return List.copyOf(ALL.values());}
    public static String role(String key) {return switch(key){case "teachers"->"TEACHER";case "students"->"STUDENT";case "parents"->"PARENT";case "administrators"->"ADMIN";default->throw new IllegalArgumentException();};}
}