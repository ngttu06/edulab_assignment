package vn.edu.khtn8.ai;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.config.AppProperties;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Service
public class AiService {
    private final AppProperties properties;
    private final AiProvider provider;
    private final LessonService lessons;
    private final JdbcTemplate jdbc;
    public AiService(AppProperties properties, AiProvider provider, LessonService lessons, JdbcTemplate jdbc) {
        this.properties = properties; this.provider = provider; this.lessons = lessons; this.jdbc = jdbc;
    }
    public record Answer(String provider, boolean sample, String text, String[] sourceLabels, boolean needsTeacherReview) { }

    @Transactional
    public Answer ask(AuthenticatedUser user, long lessonId, String question) {
        SchoolService.require(user, "STUDENT", "PARENT", "TEACHER");
        if (!properties.getAi().isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Trợ giúp AI đang tắt. Hãy hỏi giáo viên hoặc dùng diễn đàn.");
        var lesson = lessons.detail(user.id(), lessonId);
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user.id());
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND created_at>=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 DAY)", Integer.class, user.id());
        if (count >= properties.getAi().getDailyRequestLimit()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Đã đạt giới hạn trợ giúp AI trong 24 giờ.");
        // This MVP deliberately supports the local stub only; no student data leaves the server.
        if (!provider.providerName().equals("stub-local")) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Nhà cung cấp AI thật chưa được cấu hình và đánh giá dữ liệu.");
        var answer = provider.answer(new AiProvider.AiQuestion(question, org.jsoup.Jsoup.parse(lesson.contentHtml()).text(), "HINT"));
        jdbc.update("INSERT INTO ai_requests(user_id,provider,status,request_type) VALUES(?,?,'SUCCEEDED','HINT')", user.id(), provider.providerName());
        // Keep quota metadata only: free-text questions may contain children's personal data.
        return new Answer(provider.providerName(), true, answer.text(), new String[]{lesson.title()}, true);
    }
}
