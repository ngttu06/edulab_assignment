package vn.edu.khtn8.ai;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Service
public class SupportAiService {
    private static final String WEBSITE = """
            EduLab là website học đa môn. Nội dung hiện có là khóa khởi động, chưa phải chương trình đầy đủ.
            Các trang công khai: / (trang chủ), /courses (tìm/lọc khóa theo môn, khối, từ khóa), /courses/{id} (chi tiết/chương trình),
            /login (đăng nhập), /register (đăng ký STUDENT hoặc PARENT).
            Sau đăng nhập: /dashboard (tổng quan), /profile (sửa họ tên/email, đổi mật khẩu), /lessons (thư viện),
            /roadmap (lộ trình, bài tiên quyết), /quizzes (PRACTICE/TEST, hạn lượt/thời gian, tự lưu), /forum (diễn đàn).
            /family dành cho STUDENT/PARENT để liên kết bằng mã dùng một lần trong 15 phút, xem báo cáo theo quyền.
            /teacher/classes chỉ dành TEACHER/ADMIN; /admin chỉ ADMIN quản lý nội dung và cấu hình.
            Muốn học: chọn khóa, xem nội dung, đăng nhập, mở bài. Bài bị khóa cần hoàn thành tiên quyết và điểm quiz yêu cầu.
            Trợ giảng AI trong bài học chỉ hoạt động nếu được bật và không trong bài TEST đang làm.
            Chưa có giá bán, thanh toán, giỏ hàng, đơn hàng, xác minh email hay khôi phục mật khẩu.
            Không khẳng định khóa miễn phí hoặc mua thành công. Chỉ tư vấn khóa, không thu tiền hay cấp quyền mua.
            Không đưa thông tin tài khoản/cấu hình admin. Không yêu cầu mật khẩu, API key, số thẻ hay dữ liệu cá nhân.
            """;
    private final JdbcTemplate jdbc;
    private final AiSettingsService settings;
    private final AiProvider provider;
    private final SupportAiQuota quota;
    public SupportAiService(JdbcTemplate jdbc,AiSettingsService settings,AiProvider provider,SupportAiQuota quota) {
        this.jdbc=jdbc;this.settings=settings;this.provider=provider;this.quota=quota;
    }
    public record Course(long id,String title,String subject,int grade,String summary,int lessons,int minutes) {}
    public record Link(String label,String path) {}
    public record Answer(String text,boolean sample,List<Link> links) {}
    public Answer chat(AuthenticatedUser user,String address,String guest,String question,List<AiProvider.ChatMessage> history) {
        var config=settings.current();
        if(!config.enabled || config.apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Trợ lý AI chưa được bật hoặc chưa cấu hình. Bạn có thể xem danh mục khóa học.");
        long request=quota.reserve(user==null?null:user.id(),address,guest,config);
        try {
            // Deliberately select public columns; no learner progress, user records or unpublished lessons.
            var courses=jdbc.query("""
                    SELECT c.id,c.title,s.name,s.grade_level,c.summary,COUNT(l.id) lesson_count,
                           COALESCE(SUM(l.duration_minutes),0) minutes
                    FROM courses c JOIN subjects s ON s.id=c.subject_id
                    LEFT JOIN topics t ON t.course_id=c.id
                    LEFT JOIN lessons l ON l.topic_id=t.id AND l.status='PUBLISHED'
                         AND EXISTS(SELECT 1 FROM lesson_versions v WHERE v.lesson_id=l.id AND v.status='PUBLISHED')
                    GROUP BY c.id,s.id ORDER BY s.grade_level,c.id LIMIT 24
                    """,(rs,row)->new Course(rs.getLong("id"),rs.getString("title"),rs.getString("name"),rs.getInt("grade_level"),
                    rs.getString("summary"),rs.getInt("lesson_count"),rs.getInt("minutes")));
            StringBuilder context=new StringBuilder(WEBSITE).append("\nDANH MỤC KHÓA HỌC CÔNG KHAI:\n");
            for(var c:courses) context.append("#").append(c.id()).append(" | ").append(c.title()).append(" | môn ").append(c.subject())
                    .append(" | lớp ").append(c.grade()).append(" | ").append(c.lessons()).append(" bài | ").append(c.minutes())
                    .append(" phút | ").append(c.summary()).append(" | /courses/").append(c.id()).append('\n');
            var answer=provider.answer(new AiProvider.AiQuestion(question,context.toString(),"SUPPORT",history));
            quota.finish(request,true);
            var links=new java.util.ArrayList<Link>();
            links.add(new Link("Khám phá khóa học","/courses"));
            // Paths are constructed by the server, never accepted from AI text or the client.
            courses.stream().filter(c->answer.text().contains(c.title()) || answer.text().contains("/courses/"+c.id()))
                    .limit(3).forEach(c->links.add(new Link(c.title(),"/courses/"+c.id())));
            if(user==null) links.add(new Link("Đăng nhập để học","/login"));
            return new Answer(answer.text(),provider.providerName().equals("stub-local"),links);
        } catch(RuntimeException ex) {quota.finish(request,false);throw ex;}
    }
}