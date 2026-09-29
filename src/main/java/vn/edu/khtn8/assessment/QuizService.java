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
        var active = quizzes.active(studentId, quizId);
        if (active.isPresent()) return view(active.get());
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

    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView get(long studentId, long id) {
        return view(quizzes.find(id, studentId, false).orElseThrow(QuizService::notFound));
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView submit(long studentId, long id, Map<Long, Long> answers) {
        Attempt attempt = quizzes.find(id, studentId, true).orElseThrow(QuizService::notFound);
        if (!attempt.status().equals("IN_PROGRESS")) return view(attempt);
        boolean expired = !Instant.now().isBefore(attempt.deadline());
        var score = QuizScorer.grade(attempt.questions(), answers, expired);
        quizzes.grade(id, answers, score, expired);
        return view(quizzes.find(id, studentId, false).orElseThrow(QuizService::notFound));
    }

    static AttemptView view(Attempt attempt) {
        boolean graded = attempt.status().equals("GRADED");
        List<QuestionView> questions = attempt.questions().stream().map(q -> new QuestionView(q.id(), q.text(),
                graded ? q.explanation() : null, q.points(), q.options().stream()
                .map(o -> new OptionView(o.id(), o.text(), graded ? o.correct() : null)).toList(),
                graded ? attempt.answers().get(q.id()) : null)).toList();
        return new AttemptView(attempt.id(), attempt.title(), attempt.status(), attempt.deadline(), questions,
                graded ? attempt.score() : null, attempt.maxScore(), attempt.expired());
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy quiz hoặc lượt làm bài.");
    }
}
