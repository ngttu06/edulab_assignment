package vn.edu.khtn8.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.ai.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static vn.edu.khtn8.assessment.QuizModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentReviewTest {
    final List<Question> questions=List.of(new Question(1,"Câu hỏi","Giải thích bí mật",BigDecimal.ONE,List.of(new Option(11,"Đúng",true),new Option(12,"Sai",false))));
    AuthenticatedUser student() {return new AuthenticatedUser(2L,"student","Student","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));}
    Attempt attempt(String mode,Instant reveal) {return new Attempt(9,2,"Quiz","GRADED",Instant.now(),questions,Map.of(1L,12L),BigDecimal.ZERO,BigDecimal.TEN,false,mode,reveal);}
    @Test void hidesCorrectnessExplanationAndQuestionOutcomeBeforeReveal() {
        var a=QuizService.view(attempt("TEST",Instant.now().plusSeconds(1000)));
        assertFalse(a.answersVisible());assertEquals("HIDDEN",a.questions().getFirst().outcome());
        assertNull(a.questions().getFirst().explanation());assertNull(a.questions().getFirst().options().getFirst().correct());
        assertTrue(QuizService.view(attempt("TEST",Instant.now().minusSeconds(1))).answersVisible());
        assertTrue(QuizService.view(attempt("TEST",Instant.now().plusSeconds(1000)),true).answersVisible());
        assertEquals("WRONG",QuizService.view(attempt("PRACTICE",null)).questions().getFirst().outcome());
    }
    @Test void distinguishesUnansweredAndCorrect() {
        var options=List.of(new OptionView(1,"A",true),new OptionView(2,"B",false));
        assertEquals("UNANSWERED",new QuestionView(1,"Q","",BigDecimal.ONE,options,null).outcome());
        assertEquals("CORRECT",new QuestionView(1,"Q","",BigDecimal.ONE,options,1L).outcome());
    }
    @Test void explanationIsRejectedBeforeAnyCacheOrProviderCall() {
        var reviews=mock(AssessmentReviewService.class);var jdbc=mock(JdbcTemplate.class);var provider=mock(AiProvider.class);var quota=mock(AiQuotaService.class);var settings=mock(AiSettingsService.class);
        when(reviews.student(any(),eq(9L))).thenReturn(QuizService.view(attempt("TEST",Instant.now().plusSeconds(1000))));
        var ai=new QuizAiReviewService(reviews,jdbc,provider,quota,settings);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->ai.review(student(),9,1)).getStatusCode().value());
        verifyNoInteractions(jdbc,provider,quota,settings);
    }
    @Test void resumingActiveTestDoesNotApplyNewStartCutoff() {
        var repo=mock(QuizRepository.class);var service=new QuizService(repo);
        when(repo.list(2)).thenReturn(List.of(new Quiz(5,"Test",15,1,1,"TEST",Instant.now().plusSeconds(300))));
        when(repo.active(2,5)).thenReturn(Optional.of(new Attempt(9,2,"Test","IN_PROGRESS",Instant.now().plusSeconds(200),questions,Map.of(),null,BigDecimal.TEN,false,"TEST",Instant.now().plusSeconds(300))));
        assertEquals(9,service.start(2,5).id());verify(repo,never()).create(anyLong(),any(),anyInt(),anyList(),any());
    }
    @Test void tabEventsCannotTouchAnotherStudentsAttempt() {
        var jdbc=mock(JdbcTemplate.class);var repo=mock(QuizRepository.class);
        var service=new AssessmentReviewService(jdbc,repo,mock(QuizService.class));
        when(repo.find(9,2,true)).thenReturn(Optional.empty());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.tabEvent(student(),9,UUID.randomUUID().toString())).getStatusCode().value());
        verifyNoInteractions(jdbc);
    }
}