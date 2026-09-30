package vn.edu.khtn8.assessment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.assessment.QuizModels.*;

@Service
public class QuizService {
    private final QuizRepository quizzes;
    public QuizService(QuizRepository quizzes) { this.quizzes = quizzes; }
    public List<Quiz> list() { return quizzes.list(); }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView start(long studentId, long quizId) {
        quizzes.lockStudent(studentId);
        Quiz quiz = quizzes.list().stream().filter(q -> q.id() == quizId).findFirst().orElseThrow(QuizService::notFound);
        if("TEST".equals(quiz.mode()) && (quiz.revealAfter()==null || !Instant.now().plus(quiz.durationMinutes(),ChronoUnit.MINUTES).isBefore(quiz.revealAfter())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã quá thời điểm bắt đầu bài kiểm tra trước lịch công bố đáp án.");
        var active = quizzes.active(studentId, quizId);
        if (active.isPresent()) {
            if (!Instant.now().isBefore(active.get().deadline())) return finalizeSaved(active.get());
            return view(active.get());
        }
        int count = quizzes.attempts(studentId, quizId);
        if (count >= quiz.maxAttempts()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã hết lượt làm quiz này.");
        List<Question> questions = quizzes.questions(quizId);
        if (questions.isEmpty() || questions.size() > 200 || quiz.durationMinutes() <= 0 ||
                questions.stream().anyMatch(q -> q.points().signum() <= 0 || q.options().size() < 2 ||
                        q.options().stream().filter(Option::correct).count() != 1)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz chưa được cấu hình hợp lệ.");
        }
        long id = quizzes.create(studentId, quiz, count + 1, questions, Instant.now().plus(quiz.durationMinutes(), ChronoUnit.MINUTES));
        return view(quizzes.find(id, studentId, false).orElseThrow(QuizService::notFound));
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView get(long studentId, long id) {
        Attempt attempt=quizzes.find(id,studentId,true).orElseThrow(QuizService::notFound);
        if (attempt.status().equals("IN_PROGRESS") && !Instant.now().isBefore(attempt.deadline())) return finalizeSaved(attempt);
        return view(attempt);
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView submit(long studentId, long id, Map<Long, Long> answers) {
        Attempt attempt = quizzes.find(id, studentId, true).orElseThrow(QuizService::notFound);
        if (!attempt.status().equals("IN_PROGRESS")) return view(attempt);
        boolean expired = !Instant.now().isBefore(attempt.deadline());
        Map<Long,Long> accepted=expired?attempt.answers():answers;
        var score = QuizScorer.grade(attempt.questions(), accepted, false);
        quizzes.grade(id, accepted, score, expired);
        return view(quizzes.find(id, studentId, false).orElseThrow(QuizService::notFound));
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView save(long studentId,long id,Map<Long,Long> answers) {
        Attempt attempt=quizzes.find(id,studentId,true).orElseThrow(QuizService::notFound);
        if (!attempt.status().equals("IN_PROGRESS")) return view(attempt);
        if (!Instant.now().isBefore(attempt.deadline())) return finalizeSaved(attempt);
        QuizScorer.grade(attempt.questions(),answers,false); // validates ownership of questions/options
        quizzes.save(id,answers);
        return view(quizzes.find(id,studentId,false).orElseThrow(QuizService::notFound));
    }

    private AttemptView finalizeSaved(Attempt attempt) {
        var score=QuizScorer.grade(attempt.questions(),attempt.answers(),false);
        quizzes.grade(attempt.id(),attempt.answers(),score,true);
        return view(quizzes.find(attempt.id(),attempt.studentId(),false).orElseThrow(QuizService::notFound));
    }

    @Transactional
    public void expire(long studentId,long id) {
        quizzes.find(id,studentId,true).filter(a->a.status().equals("IN_PROGRESS")&&!Instant.now().isBefore(a.deadline())).ifPresent(this::finalizeSaved);
    }

    static AttemptView view(Attempt attempt) {
        boolean graded = attempt.status().equals("GRADED");
        boolean reveal=graded && (!"TEST".equals(attempt.mode()) || attempt.revealAfter()!=null && !Instant.now().isBefore(attempt.revealAfter()));
        List<QuestionView> questions = attempt.questions().stream().map(q -> new QuestionView(q.id(), q.text(),
                reveal ? q.explanation() : null, q.points(), q.options().stream()
                .map(o -> new OptionView(o.id(), o.text(), reveal ? o.correct() : null)).toList(),
                attempt.answers().get(q.id()))).toList();
        return new AttemptView(attempt.id(), attempt.title(), attempt.status(), attempt.deadline(), questions,
                graded ? attempt.score() : null, attempt.maxScore(), attempt.expired());
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy quiz hoặc lượt làm bài.");
    }
}
