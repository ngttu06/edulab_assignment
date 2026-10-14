package vn.edu.khtn8.assessment;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@RestController
public class QuizImportController {
    private final QuizImportService imports;
    public QuizImportController(QuizImportService imports) {this.imports=imports;}
    @PostMapping(value="/api/v1/teacher/quizzes/document-import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> extract(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam MultipartFile file) throws java.io.IOException {return imports.extract(user,file);}
    @PostMapping("/api/v1/teacher/quizzes/document-import/{id}/generate-answers")
    public Map<String,Object> generate(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id) {return imports.generate(user,id);}
}