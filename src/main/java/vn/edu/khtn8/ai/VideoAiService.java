package vn.edu.khtn8.ai;

import java.util.List;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.content.video.LessonVideoService;
import vn.edu.khtn8.files.StorageSettingsService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Service
public class VideoAiService {
    private final LessonVideoService videos;
    private final LessonService lessons;
    private final AiSettingsService settings;
    private final StorageSettingsService storage;
    private final AiQuotaService quota;
    private final AiProvider provider;
    private final VideoFramePolicy frames;
    public VideoAiService(LessonVideoService videos, LessonService lessons, AiSettingsService settings,
            StorageSettingsService storage, AiQuotaService quota, AiProvider provider, VideoFramePolicy frames) {
        this.videos=videos; this.lessons=lessons; this.settings=settings; this.storage=storage;
        this.quota=quota; this.provider=provider; this.frames=frames;
    }
    public record Answer(String text, boolean sample, boolean frameAnalyzed, long positionMs,
                         String[] sourceLabels, boolean needsTeacherReview) { }

    public Answer chat(AuthenticatedUser user, long lessonId, VideoAiController.Question question) {
        SchoolService.require(user, "STUDENT", "PARENT", "TEACHER", "ADMIN");
        if (!settings.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Trợ giúp AI đang tắt. Hãy hỏi giáo viên hoặc dùng diễn đàn.");
        // Enforces publication/access/unlock and current revision; no source URLs go to the AI.
        var video=videos.video(user, lessonId);
        if (video.id()!=question.videoId()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Video đã thay đổi. Hãy tải lại trang trước khi hỏi AI.");
        long timestamp=question.frame()==null ? question.positionMs() : question.frame().timestampMs();
        if (timestamp<0 || timestamp>video.durationSeconds()*1000L || question.positionMs()>video.durationSeconds()*1000L)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mốc thời gian nằm ngoài video.");
        var lesson=lessons.detail(user.id(), lessonId);
        boolean sample=provider.providerName().equals("stub-local");
        if (question.frame()!=null && (sample || !storage.limits().aiImagesEnabled()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI chưa hỗ trợ đọc ảnh. Admin cần bật khả năng đọc ảnh và cấu hình model có vision.");
        long request=quota.reserve(user.id(), provider.providerName()); // Also blocks an in-progress TEST.
        try {
            List<AiProvider.Attachment> images=question.frame()==null ? List.of() : List.of(frames.image(question.frame().dataUrl()));
            String text=Jsoup.parse(lesson.contentHtml()).text();
            String context="Môn học: "+lesson.subjectName()+"; khối lớp: "+lesson.gradeLevel()+"; bài: "+lesson.title()
                    +"\nMốc video do người dùng cung cấp: "+timestamp+" ms / "+video.durationSeconds()+" giây."
                    + (images.isEmpty() ? "\nKhông có khung hình đính kèm. Không khẳng định đã xem video hoặc thấy nội dung tại mốc này."
                            : "\nCó một ảnh khung hình do người dùng chia sẻ tại mốc này. Chỉ giải thích những gì thấy trong ảnh; không suy diễn rằng đã xem toàn bộ video. Nếu ảnh mờ hoặc thiếu dữ kiện hãy nói rõ.")
                    +"\nNội dung bài học:\n"+text.substring(0,Math.min(text.length(),12000));
            var history=question.history().stream().map(m->new AiProvider.ChatMessage(m.role(), m.content())).toList();
            var answer=provider.answer(new AiProvider.AiQuestion(question.question().trim(), context, "HINT", history, images));
            quota.finish(request, true);
            return new Answer(answer.text(), sample, !images.isEmpty(), timestamp, new String[]{lesson.title()}, true);
        } catch (RuntimeException ex) { quota.finish(request, false); throw ex; }
    }
}