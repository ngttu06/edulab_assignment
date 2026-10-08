package vn.edu.khtn8.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.assessment.QuizModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizServiceTest {
    private QuizRepository repository;
    private QuizService service;
    @BeforeEach void setUp() { repository = mock(QuizRepository.class); service = new QuizService(repository); }
    @Test void enforcesMaxAttempts() {
        when(repository.list(2)).thenReturn(List.of(new Quiz(5,"Quiz",15,3,1)));
        when(repository.active(2,5)).thenReturn(Optional.empty());
        when(repository.attempts(2,5)).thenReturn(3);
        var error = assertThrows(ResponseStatusException.class, () -> service.start(2,5));
        assertEquals(409, error.getStatusCode().value());
        verify(repository).lockStudent(2);
        verify(repository, never()).questions(anyLong());
    }
    @Test void otherStudentsAttemptIsNotFound() {
        when(repository.find(9,2,true)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.submit(2,9,Map.of()));
        assertEquals(404, error.getStatusCode().value());
        verify(repository, never()).grade(anyLong(),anyMap(),any(),anyBoolean());
    }
    @Test void duplicateSubmissionReturnsOriginalResult() {
        var attempt = new Attempt(9,2,"Quiz","GRADED",Instant.now(),List.of(),Map.of(),BigDecimal.TEN,BigDecimal.TEN,false);
        when(repository.find(9,2,true)).thenReturn(Optional.of(attempt));
        assertEquals(BigDecimal.TEN,service.submit(2,9,Map.of()).score());
        verify(repository, never()).grade(anyLong(),anyMap(),any(),anyBoolean());
    }
    @Test void latePayloadCannotReplaceSavedAnswers() {
        var question=new Question(1,"Q","Why",BigDecimal.ONE,List.of(new Option(11,"A",true),new Option(12,"B",false)));
        var attempt=new Attempt(9,2,"Quiz","IN_PROGRESS",Instant.now().minusSeconds(10),List.of(question),Map.of(1L,12L),null,BigDecimal.TEN,false);
        when(repository.find(9,2,true)).thenReturn(Optional.of(attempt));
        when(repository.find(9,2,false)).thenReturn(Optional.of(new Attempt(9,2,"Quiz","GRADED",attempt.deadline(),List.of(question),attempt.answers(),BigDecimal.ZERO,BigDecimal.TEN,true)));
        service.submit(2,9,Map.of(1L,11L));
        verify(repository).grade(9,Map.of(1L,12L),new BigDecimal("0.00"),true);
    }
}
