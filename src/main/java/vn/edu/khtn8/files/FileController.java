package vn.edu.khtn8.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService files;
    public FileController(FileService files) { this.files = files; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam MultipartFile file) throws IOException {
        return files.upload(user, file);
    }
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        var download = files.download(user, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(download.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff").body(download.resource());
    }
}
