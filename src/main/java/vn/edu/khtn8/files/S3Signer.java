package vn.edu.khtn8.files;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** AWS SigV4 query signing. Only individual object operations; never expose account credentials. */
public final class S3Signer {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private S3Signer() {}
    public static URI sign(StorageSettingsService.Profile p,String method,String key,Map<String,String> headers,int seconds,Instant now) {
        if(seconds<1 || seconds>604800) throw new IllegalArgumentException("Invalid signature expiry");
        if(!key.matches("[A-Za-z0-9_/-]{1,255}") || key.contains("..")) throw new IllegalArgumentException("Invalid object key");
        String day=DATE.format(now),timestamp=TIME.format(now),scope=day+"/"+p.region+"/s3/aws4_request";
        URI endpoint=URI.create(p.endpoint);
        var signedHeaders=new TreeMap<String,String>();
        signedHeaders.put("host",endpoint.getHost());
        headers.forEach((name,value)->signedHeaders.put(name.toLowerCase(java.util.Locale.ROOT),value.trim().replaceAll("\\s+"," ")));
        String names=String.join(";",signedHeaders.keySet());
        StringBuilder canonicalHeaders=new StringBuilder();signedHeaders.forEach((name,value)->canonicalHeaders.append(name).append(':').append(value).append('\n'));
        var query=new TreeMap<String,String>();
        query.put("X-Amz-Algorithm","AWS4-HMAC-SHA256");query.put("X-Amz-Credential",p.accessKey+"/"+scope);
        query.put("X-Amz-Date",timestamp);query.put("X-Amz-Expires",String.valueOf(seconds));query.put("X-Amz-SignedHeaders",names);
        String path="/"+encode(p.bucket)+"/"+java.util.Arrays.stream(key.split("/")).map(S3Signer::encode).collect(java.util.stream.Collectors.joining("/"));
        String canonicalQuery=query.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
        String canonical=method+"\n"+path+"\n"+canonicalQuery+"\n"+canonicalHeaders+"\n"+names+"\nUNSIGNED-PAYLOAD";
        byte[] signing=hmac(hmac(hmac(hmac(("AWS4"+p.secretKey).getBytes(StandardCharsets.UTF_8),day),p.region),"s3"),"aws4_request");
        String signature=HexFormat.of().formatHex(hmac(signing,"AWS4-HMAC-SHA256\n"+timestamp+"\n"+scope+"\n"+sha256(canonical.getBytes(StandardCharsets.UTF_8))));
        return URI.create(p.endpoint+path+"?"+canonicalQuery+"&X-Amz-Signature="+signature);
    }
    public static String encode(String value) {
        StringBuilder out=new StringBuilder();
        for(byte b:value.getBytes(StandardCharsets.UTF_8)) {
            int c=Byte.toUnsignedInt(b);
            if((c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='-'||c=='_'||c=='.'||c=='~') out.append((char)c);
            else out.append('%').append("0123456789ABCDEF".charAt(c>>4)).append("0123456789ABCDEF".charAt(c&15));
        }
        return out.toString();
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception ex) { throw new IllegalStateException("SHA256 unavailable"); }
    }
    private static byte[] hmac(byte[] key,String text) {
        try { var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(text.getBytes(StandardCharsets.UTF_8)); }
        catch(Exception ex) { throw new IllegalStateException("HMAC unavailable"); }
    }
}