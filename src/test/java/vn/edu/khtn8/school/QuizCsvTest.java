package vn.edu.khtn8.school;

import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class QuizCsvTest {
    private static final String HEADER="question,option_a,option_b,option_c,option_d,correct_answer,explanation,points\n";
    private final QuizCsv csv=new QuizCsv(Validation.buildDefaultValidatorFactory().getValidator());
    private byte[] bytes(String text) {return text.getBytes(StandardCharsets.UTF_8);}
    @Test void templateIsValidAndIncludesVietnamese() throws Exception {
        try(var in=getClass().getResourceAsStream("/templates/quiz-mau.csv")) {
            var questions=csv.parse(in.readAllBytes());assertEquals(3,questions.size());assertEquals(1,questions.get(1).correctIndex());
            assertTrue(questions.getFirst().content().contains("áp suất"));
        }
    }
    @Test void acceptsBomCrLfQuotedCommaNewlineAndEscapedQuote() {
        var q=csv.parse(bytes("\uFEFF"+HEADER.replace("\n","\r\n")+"\"Dòng 1,\nDòng \"\"2\"\"\",A,B,,,b,Giải thích,1.25\r\n")).getFirst();
        assertEquals("Dòng 1,\nDòng \"2\"",q.content());assertEquals(2,q.options().size());assertEquals(1,q.correctIndex());
    }
    @Test void rejectsMalformedRowsAndReportsLine() {
        for(String row:new String[]{"Q,A,B,,,D,,1","Q,A,,,,A,,1","Q,A,B,,,A,,0","Q,A,B,,,A,,1.234","Q,A,B,,,A,,abc","\"Q,A,B,,,A,,1","Q,A,B,,,A,extra,1,extra"}) {
            var error=assertThrows(ResponseStatusException.class,()->csv.parse(bytes(HEADER+row)));
            assertTrue(error.getReason().startsWith("Dòng 2:"),error.getReason());
        }
    }
    @Test void rejectsWrongEncodingHeaderEmptyAndOversizedFiles() {
        assertThrows(ResponseStatusException.class,()->csv.parse(new byte[]{(byte)0xff}));
        assertThrows(ResponseStatusException.class,()->csv.parse(bytes("wrong\n")));
        assertThrows(ResponseStatusException.class,()->csv.parse(bytes(HEADER)));
        assertThrows(ResponseStatusException.class,()->csv.parse(new byte[1024*1024+1]));
    }
    @Test void limitsQuestionCountAndTextLengths() {
        assertEquals(200,csv.parse(bytes(HEADER+"Q,A,B,,,A,,1\n".repeat(200))).size());
        assertThrows(ResponseStatusException.class,()->csv.parse(bytes(HEADER+"Q,A,B,,,A,,1\n".repeat(201))));
        assertThrows(ResponseStatusException.class,()->csv.parse(bytes(HEADER+"Q,"+"A".repeat(1001)+",B,,,A,,1")));
    }
}