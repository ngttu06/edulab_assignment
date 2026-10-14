package vn.edu.khtn8.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.ai.*;
import vn.edu.khtn8.files.FileScanner;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.*;
import static vn.edu.khtn8.school.AuthoringModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
@Transactional
@WithMockUser(roles="STUDENT")
class AssessmentMysqlTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));p.add("spring.datasource.username",()->"edulab_test");p.add("spring.datasource.password",()->"local-test-only-password");
        p.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TeacherAuthoringService authoring;
    @Autowired QuizService quizzes;
    @Autowired AssessmentReviewService reviews;
    @Autowired QuizAiReviewService ai;
    @Autowired QuizImportService imports;
    @MockBean AiProvider provider;
    @MockBean AiQuotaService quota;
    @MockBean FileScanner scanner;
    AuthenticatedUser user(String name,String role) {long id=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);return new AuthenticatedUser(id,name,name,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    AuthenticatedUser student(){return user("hocsinh","STUDENT");}
    AuthenticatedUser teacher(){return user("giaovien","TEACHER");}
    long classroom(){return jdbc.queryForObject("SELECT classroom_id FROM class_teachers WHERE teacher_id=? LIMIT 1",Long.class,teacher().id());}
    long quiz(String mode) {return authoring.saveQuiz(teacher(),null,new QuizInput("Đề kiểm thử",15,3,mode,mode.equals("TEST")?Instant.now().plusSeconds(7200):null,true,List.of(new QuestionInput("Câu hỏi gốc","GIẢI THÍCH BÍ MẬT",List.of("Đáp án đúng","Đáp án sai"),0,BigDecimal.ONE)),List.of(classroom())));}
    @BeforeEach void setup(){when(provider.providerName()).thenReturn("gatecheap");when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer("Nhận xét kiểm thử",new String[0],true));when(quota.reserve(anyLong(),anyString(),anyString())).thenReturn(1L);jdbc.update("UPDATE ai_settings SET enabled=TRUE WHERE id=1");}
    @Test void tabEventsAreIdempotentAndStopAfterSubmission() {
        long quiz=quiz("TEST");var a=quizzes.start(student().id(),quiz);String event=UUID.randomUUID().toString();
        assertEquals(1,reviews.tabEvent(student(),a.id(),event));assertEquals(1,reviews.tabEvent(student(),a.id(),event));
        assertEquals(2,reviews.tabEvent(student(),a.id(),UUID.randomUUID().toString()));
        quizzes.submit(student().id(),a.id(),Map.of());assertEquals(2,reviews.tabEvent(student(),a.id(),UUID.randomUUID().toString()));
        var detail=reviews.teacherDetail(teacher(),a.id());assertEquals(2,((List<?>)detail.get("events")).size());
        assertTrue(((QuizModels.AttemptView)detail.get("attempt")).answersVisible());assertFalse(quizzes.get(student().id(),a.id()).answersVisible());
    }
    @Test void reportsUseUniqueStudentsLatestBestAndAllWithoutCountingNotStartedAsZero() {
        long quiz=quiz("PRACTICE");var first=quizzes.start(student().id(),quiz);var q=first.questions().getFirst();
        long correct=q.options().getFirst().id();
        quizzes.submit(student().id(),first.id(),Map.of(q.id(),correct));
        var second=quizzes.start(student().id(),quiz);quizzes.submit(student().id(),second.id(),Map.of());
        assertEquals(BigDecimal.ZERO.setScale(2),stats(reviews.report(teacher(),quiz,0,"latest")).get("average"));
        assertEquals(BigDecimal.TEN.setScale(2),stats(reviews.report(teacher(),quiz,0,"best")).get("average"));
        assertEquals(new BigDecimal("5.00"),stats(reviews.report(teacher(),quiz,0,"all")).get("average"));
        assertEquals(1,stats(reviews.report(teacher(),quiz,0,"latest")).get("sampleCount"));
        assertEquals(2,stats(reviews.report(teacher(),quiz,0,"all")).get("sampleCount"));
        jdbc.update("DELETE FROM class_teachers WHERE teacher_id=?",teacher().id());
        assertThrows(ResponseStatusException.class,()->reviews.teacher(teacher(),first.id()));
        assertThrows(ResponseStatusException.class,()->reviews.report(teacher(),quiz,0,"all"));
    }
    @Test void aiCachesPerQuestionAndSummaryAndNeverSeesTestAnswersBeforeRelease() {
        long quiz=quiz("TEST");var a=quizzes.start(student().id(),quiz);quizzes.submit(student().id(),a.id(),Map.of());
        ai.review(student(),a.id(),0);ai.review(student(),a.id(),0);
        var captor=org.mockito.ArgumentCaptor.forClass(AiProvider.AiQuestion.class);verify(provider,times(1)).answer(captor.capture());
        assertFalse(captor.getValue().lessonContext().contains("BÍ MẬT"));assertFalse(captor.getValue().lessonContext().contains("Câu hỏi gốc"));
        assertThrows(ResponseStatusException.class,()->ai.review(student(),a.id(),a.questions().getFirst().id()));
        jdbc.update("UPDATE quiz_attempts SET reveal_after_snapshot=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id=?",a.id());
        ai.review(student(),a.id(),0);ai.review(student(),a.id(),a.questions().getFirst().id());ai.review(student(),a.id(),a.questions().getFirst().id());
        verify(provider,times(3)).answer(any());assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_ai_reviews WHERE attempt_id=?",Integer.class,a.id()));
    }
    @Test void documentMissingAnswersRequireExplicitGenerationAndTeacherReview() throws Exception {
        jdbc.update("UPDATE storage_settings SET ai_pdf_enabled=TRUE WHERE id=1");when(scanner.scan(any())).thenReturn("CLEAN");
        String extracted="{\"complete\":true,\"warnings\":[],\"questions\":[{\"content\":\"Câu nhập\",\"options\":[\"A\",\"B\"],\"correctIndex\":null,\"explanation\":\"\",\"points\":1}]}";
        when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer(extracted,new String[0],true));
        var preview=imports.extract(teacher(),new MockMultipartFile("file","quiz.pdf","application/pdf","%PDF-1.4".getBytes()));
        assertEquals(1,preview.get("missingCount"));verify(provider,times(1)).answer(any());
        when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer("{\"answers\":[{\"index\":0,\"correctIndex\":1,\"explanation\":\"Vì B\"}]}",new String[0],true));
        var generated=imports.generate(teacher(),preview.get("id").toString());assertEquals(0,generated.get("missingCount"));
        var qs=(com.fasterxml.jackson.databind.JsonNode)generated.get("questions");assertTrue(qs.get(0).get("aiGenerated").asBoolean());
        var input=new QuizInput("Đề AI",15,1,"PRACTICE",null,false,List.of(new QuestionInput("Câu nhập","Vì B",List.of("A","B"),1,BigDecimal.ONE,true,false)),List.of(classroom()));
        assertThrows(ResponseStatusException.class,()->authoring.saveQuiz(teacher(),null,input));
        var approved=new QuizInput("Đề AI đã duyệt",15,1,"PRACTICE",null,false,List.of(new QuestionInput("Câu nhập","Vì B",List.of("A","B"),1,BigDecimal.ONE,true,true)),List.of(classroom()));
        assertTrue(authoring.saveQuiz(teacher(),null,approved)>0);
        var missing=new QuizInput("Thiếu đáp án",15,1,"PRACTICE",null,false,List.of(new QuestionInput("Câu nhập","",List.of("A","B"),null,BigDecimal.ONE)),List.of(classroom()));
        assertThrows(ResponseStatusException.class,()->authoring.saveQuiz(teacher(),null,missing));
    }
    @Test void cachedAiIsBlockedDuringAnotherActiveTestAndFailureDoesNotAlterGrade() {
        long practice=quiz("PRACTICE");var a=quizzes.start(student().id(),practice);quizzes.submit(student().id(),a.id(),Map.of());
        ai.review(student(),a.id(),0);
        long test=quiz("TEST");var active=quizzes.start(student().id(),test);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->ai.review(student(),a.id(),0)).getStatusCode().value());
        quizzes.submit(student().id(),active.id(),Map.of());
        when(provider.answer(any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"AI offline"));
        assertThrows(ResponseStatusException.class,()->ai.review(student(),a.id(),a.questions().getFirst().id()));
        assertEquals("GRADED",quizzes.get(student().id(),a.id()).status());
        assertEquals(new BigDecimal("0.00"),quizzes.get(student().id(),a.id()).score());
    }
    @Test void sampleReviewRemainsLabeledAfterProviderChanges() {
        long quiz=quiz("PRACTICE");var a=quizzes.start(student().id(),quiz);quizzes.submit(student().id(),a.id(),Map.of());
        when(provider.providerName()).thenReturn("stub-local");assertEquals(true,ai.review(student(),a.id(),0).get("sample"));
        when(provider.providerName()).thenReturn("gatecheap");assertEquals(true,ai.review(student(),a.id(),0).get("sample"));
        verify(provider,times(1)).answer(any());
    }
    @Test void docxImportReadsActualTextAndGeneratesOnlyMissingAnswer() throws Exception {
        when(scanner.scan(any())).thenReturn("CLEAN");
        String first="{\"content\":\"Câu có đáp án\",\"options\":[\"A\",\"B\"],\"correctIndex\":0,\"explanation\":\"Giữ nguyên\",\"points\":1}";
        String second="{\"content\":\"Câu thiếu\",\"options\":[\"C\",\"D\"],\"correctIndex\":null,\"explanation\":\"\",\"points\":1,\"reviewed\":true}";
        when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer("{\"complete\":true,\"warnings\":[],\"questions\":["+first+","+second+"]}",new String[0],true));
        byte[] bytes=new QuizDocumentReaderTest().docx("<w:document xmlns:w='urn:word'><w:p><w:r><w:t>Câu có đáp án A. A B. B</w:t></w:r></w:p></w:document>");
        var preview=imports.extract(teacher(),new MockMultipartFile("file","de.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",bytes));
        var captor=org.mockito.ArgumentCaptor.forClass(AiProvider.AiQuestion.class);verify(provider).answer(captor.capture());
        assertTrue(captor.getValue().lessonContext().contains("Câu có đáp án"));assertTrue(captor.getValue().attachments().isEmpty());
        when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer("{\"answers\":[{\"index\":1,\"correctIndex\":1,\"explanation\":\"Chọn D\"}]}",new String[0],true));
        var result=imports.generate(teacher(),preview.get("id").toString());var qs=(com.fasterxml.jackson.databind.JsonNode)result.get("questions");
        assertEquals("Giữ nguyên",qs.get(0).get("explanation").asText());assertEquals(0,qs.get(0).get("correctIndex").asInt());
        assertFalse(qs.get(1).get("reviewed").asBoolean());assertTrue(qs.get(1).get("aiGenerated").asBoolean());
        assertThrows(ResponseStatusException.class,()->imports.generate(user("admin","ADMIN"),preview.get("id").toString()));
    }
    @SuppressWarnings("unchecked") Map<String,Object> stats(Map<String,Object> report){return (Map<String,Object>)report.get("stats");}
}