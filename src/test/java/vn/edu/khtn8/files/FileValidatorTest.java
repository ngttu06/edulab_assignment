package vn.edu.khtn8.files;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class FileValidatorTest {
    private static final long IMAGE_LIMIT = 5 * 1024 * 1024;
    private static final long PDF_LIMIT = 10 * 1024 * 1024;
    private byte[] pdf() { return "%PDF-1.7\nexample".getBytes(StandardCharsets.US_ASCII); }
    @Test void acceptsMatchingPdfSignature() { assertEquals("application/pdf", FileValidator.validate("exercise.pdf","application/pdf",pdf(),IMAGE_LIMIT,PDF_LIMIT)); }
    @Test void rejectsFakeImage() { assertThrows(ResponseStatusException.class, () -> FileValidator.validate("a.jpg","image/jpeg",pdf(),IMAGE_LIMIT,PDF_LIMIT)); }
    @Test void rejectsSvg() { assertThrows(ResponseStatusException.class, () -> FileValidator.validate("a.svg","image/svg+xml",pdf(),IMAGE_LIMIT,PDF_LIMIT)); }
    @Test void rejectsMimeMismatch() { assertThrows(ResponseStatusException.class, () -> FileValidator.validate("a.pdf","text/html",pdf(),IMAGE_LIMIT,PDF_LIMIT)); }
    @Test void rejectsTraversal() { assertThrows(ResponseStatusException.class, () -> FileValidator.validate("../a.pdf","application/pdf",pdf(),IMAGE_LIMIT,PDF_LIMIT)); }
    @Test void rejectsOversize() { assertThrows(ResponseStatusException.class, () -> FileValidator.validate("a.pdf","application/pdf",pdf(),1,1)); }
    @Test void rejectsEmpty() { assertThrows(ResponseStatusException.class, () -> FileValidator.validate("a.pdf","application/pdf",new byte[0],IMAGE_LIMIT,PDF_LIMIT)); }
}
