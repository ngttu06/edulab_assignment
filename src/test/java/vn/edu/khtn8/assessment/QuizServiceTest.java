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
        when(repository.list()).thenReturn(List.of(new Quiz(5,"Quiz",15,3,1)));
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
}
