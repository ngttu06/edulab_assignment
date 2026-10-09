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
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService files;
    private final DirectUploadService uploads;
    public FileController(FileService files,DirectUploadService uploads) { this.files = files;this.uploads=uploads; }
    @PostMapping
    public Map<String, Object> upload(@AuthenticationPrincipal AuthenticatedUser user) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Upload qua server đã ngừng. Dùng presigned URL để upload trực tiếp R2.");
    }
    @PostMapping("/presign")
    public DirectUploadService.Ticket presign(@AuthenticationPrincipal AuthenticatedUser user,@jakarta.validation.Valid @RequestBody DirectUploadService.Input input,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return uploads.presign(user,input);
    }
    @PostMapping("/uploads/{id}/complete")
    public DirectUploadService.Status complete(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id) { return uploads.complete(user,id); }
    @PostMapping("/community/presign")
    public DirectUploadService.Ticket presignCommunity(@AuthenticationPrincipal AuthenticatedUser user,@jakarta.validation.Valid @RequestBody DirectUploadService.Input input,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return uploads.presignCommunity(user,input);
    }
    @GetMapping("/uploads/{id}/status")
    public DirectUploadService.Status status(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return uploads.status(user,id);
    }
    @DeleteMapping("/uploads/{id}")
    public DirectUploadService.Status cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id) { return uploads.cancel(user,id); }
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        var download = files.download(user, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(download.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff").body(download.resource());
    }
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        var image = files.image(user, id);
        var download = image.download();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).contentLength(download.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff").body(download.resource());
    }
}
