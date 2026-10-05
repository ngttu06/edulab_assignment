package vn.edu.khtn8.files;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.khtn8.common.config.AppProperties;

/** ClamAV INSTREAM; no user-controlled host, no shell execution. Fail closed. */
@Component
public class FileScanner {
    private final String host;
    private final int port;
    private final AppProperties properties;
    public FileScanner(@Value("${EDULAB_CLAMAV_HOST:}") String host,@Value("${EDULAB_CLAMAV_PORT:3310}") int port,AppProperties properties) {
        this.host=host;this.port=port;this.properties=properties;
    }
    public String scan(byte[] bytes) {
        if(properties.getUpload().isDevScanBypass()) return "CLEAN";
        if(host.isBlank()) return "PENDING_SCAN";
        try(var socket=new Socket()) {
            socket.connect(new InetSocketAddress(host,port),3000);socket.setSoTimeout(15000);
            var out=new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for(int start=0;start<bytes.length;start+=8192) {
                int size=Math.min(8192,bytes.length-start);out.writeInt(size);out.write(bytes,start,size);
            }
            out.writeInt(0);out.flush();
            var reply=new ByteArrayOutputStream();int b;
            while(reply.size()<4096 && (b=socket.getInputStream().read())!=-1 && b!=0) reply.write(b);
            String result=reply.toString(StandardCharsets.UTF_8).trim();
            if(result.equals("stream: OK")) return "CLEAN";
            if(result.startsWith("stream:") && result.endsWith(" FOUND")) return "INFECTED";
            return "PENDING_SCAN";
        } catch(IOException | IllegalArgumentException ex) { return "PENDING_SCAN"; }
    }
    public boolean ready() { return properties.getUpload().isDevScanBypass() || !host.isBlank(); }
}