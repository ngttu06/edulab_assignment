package vn.edu.khtn8.files;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class R2Client {
    private final StorageEndpointPolicy policy;
    private static final java.util.concurrent.ScheduledExecutorService DEADLINES=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"r2-read-deadline");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public R2Client(StorageEndpointPolicy policy) { this.policy=policy; }
    public URI presign(StorageSettingsService.Profile p,String method,String key,Map<String,String> headers,int seconds) {
        policy.validate(p.endpoint);return S3Signer.sign(p,method,key,headers,seconds,Instant.now());
    }
    public record Metadata(long size,String contentType,String etag) {}
    public Metadata head(StorageSettingsService.Profile p,String key) {
        var response=request(p,"HEAD",key,Map.of());
        try(var body=response.body()) {
            if(response.statusCode()==404) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa tìm thấy file trên R2. Upload xong rồi thử xác nhận lại.");
            requireSuccess(response.statusCode());
            return new Metadata(response.headers().firstValueAsLong("Content-Length").orElse(-1),response.headers().firstValue("Content-Type").orElse(""),response.headers().firstValue("ETag").orElse(""));
        } catch(IOException ex) { throw unavailable(); }
    }
    public void copy(StorageSettingsService.Profile p,String source,String target) {
        // Validate/scan the destination, not the still browser-writable source.
        var response=request(p,"PUT",target,Map.of("x-amz-copy-source","/"+p.bucket+"/"+source));
        try(var body=response.body()) {
            requireSuccess(response.statusCode());
            // Copy responses can report errors in an HTTP 200 body.
            String xml=new String(body.readNBytes(16384),java.nio.charset.StandardCharsets.UTF_8);
            if(!xml.contains("CopyObjectResult") || xml.contains("<Error")) throw unavailable();
        } catch(IOException ex) { throw unavailable(); }
    }
    public byte[] read(StorageSettingsService.Profile p,String key,long maxBytes) {
        var response=request(p,"GET",key,Map.of());
        try(var body=response.body()) {
            requireSuccess(response.statusCode());
            if(maxBytes<1 || maxBytes>10*1024*1024 || response.headers().firstValueAsLong("Content-Length").orElse(0)>maxBytes) throw StorageSettingsService.bad("File vượt giới hạn đọc an toàn.");
            byte[] bytes=body.readNBytes((int)maxBytes+1);
            if(bytes.length>maxBytes) throw StorageSettingsService.bad("File vượt giới hạn đọc an toàn.");
            return bytes;
        } catch(IOException ex) { throw unavailable(); }
    }
    public void delete(StorageSettingsService.Profile p,String key) {
        var response=request(p,"DELETE",key,Map.of());
        try(var body=response.body()) { requireSuccess(response.statusCode()); }
        catch(IOException ex) { throw unavailable(); }
    }
    private HttpResponse<java.io.InputStream> request(StorageSettingsService.Profile p,String method,String key,Map<String,String> headers) {
        policy.requirePublicAddress(p.endpoint);
        var builder=HttpRequest.newBuilder(presign(p,method,key,headers,60)).timeout(Duration.ofSeconds(30)).method(method,HttpRequest.BodyPublishers.noBody());
        headers.forEach(builder::header);
        try {
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
            // JDK input-stream handlers finish at headers. Bound actual body reads too.
            DEADLINES.schedule(()->{try { response.body().close(); }catch(IOException ignored) {}},30,java.util.concurrent.TimeUnit.SECONDS);
            return response;
        }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw unavailable(); }
        catch(IOException ex) { throw unavailable(); }
    }
    private static void requireSuccess(int status) { if(status<200 || status>=300) throw unavailable(); }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không thể thao tác R2. Kiểm tra endpoint, bucket, quyền khóa hoặc thử lại sau."); }
}