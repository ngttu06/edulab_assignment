package vn.edu.khtn8.ai;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
    private final AiQuotaService quota;
    private final vn.edu.khtn8.content.service.LearningPathService path;
    @org.springframework.beans.factory.annotation.Autowired
    private AiSettingsService runtimeSettings;
    public AiService(AppProperties properties, AiProvider provider, LessonService lessons, AiQuotaService quota,
            vn.edu.khtn8.content.service.LearningPathService path) {
        this.properties = properties; this.provider = provider; this.lessons = lessons; this.quota = quota;
        this.path = path;
    }
    public record Answer(String provider, boolean sample, String text, String[] sourceLabels, boolean needsTeacherReview) { }

    public Answer ask(AuthenticatedUser user, long lessonId, String question) {
        SchoolService.require(user, "STUDENT", "PARENT", "TEACHER", "ADMIN");
        if (!(runtimeSettings==null?properties.getAi().isEnabled():runtimeSettings.enabled())) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Trợ giúp AI đang tắt. Hãy hỏi giáo viên hoặc dùng diễn đàn.");
        if(SchoolService.hasRole(user,"STUDENT")) path.requireUnlocked(user.id(),lessonId);
        var lesson = lessons.detail(user.id(), lessonId);
        long requestId = quota.reserve(user.id(), provider.providerName());
        try {
            String context="Môn học: "+lesson.subjectName()+"; khối lớp: "+lesson.gradeLevel()+"; bài: "+lesson.title()+"\n"+org.jsoup.Jsoup.parse(lesson.contentHtml()).text();
            var answer = provider.answer(new AiProvider.AiQuestion(question, context, "HINT"));
            quota.finish(requestId, true);
            return new Answer(provider.providerName(), provider.providerName().equals("stub-local"), answer.text(), new String[]{lesson.title()}, true);
        } catch (RuntimeException ex) {
            quota.finish(requestId, false);
            throw ex;
        }
    }
}
