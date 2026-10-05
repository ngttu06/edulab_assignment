package vn.edu.khtn8.files;

import java.net.ServerSocket;
import java.io.DataInputStream;
import org.junit.jupiter.api.Test;
import vn.edu.khtn8.common.config.AppProperties;
import static org.junit.jupiter.api.Assertions.*;

class FileScannerTest {
    @Test void missingOrUnavailableScannerNeverMarksClean() {
        var p=new AppProperties();
        assertEquals("PENDING_SCAN",new FileScanner("",3310,p).scan(new byte[]{1}));
        assertEquals("PENDING_SCAN",new FileScanner("127.0.0.1",1,p).scan(new byte[]{1}));
        p.getUpload().setDevScanBypass(true);
        assertEquals("CLEAN",new FileScanner("",3310,p).scan(new byte[]{1}));
    }
    @Test void instreamSendsBytesAndHonorsCleanInfectedAndErrorResults() throws Exception {
        for(String reply:java.util.List.of("stream: OK","stream: Test FOUND","stream: size limit exceeded ERROR")) {
            try(var server=new ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {
                var task=java.util.concurrent.CompletableFuture.runAsync(()->{
                    try(var socket=server.accept()) {
                        var input=new DataInputStream(socket.getInputStream());
                        assertEquals("zINSTREAM\0",new String(input.readNBytes(10),java.nio.charset.StandardCharsets.US_ASCII));
                        int length=input.readInt();assertArrayEquals(new byte[]{1,2,3},input.readNBytes(length));assertEquals(0,input.readInt());
                        socket.getOutputStream().write((reply+"\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    } catch(java.io.IOException ex) { throw new RuntimeException(ex); }
                });
                String result=new FileScanner("127.0.0.1",server.getLocalPort(),new AppProperties()).scan(new byte[]{1,2,3});
                assertEquals(reply.endsWith(" OK")?"CLEAN":reply.endsWith(" FOUND")?"INFECTED":"PENDING_SCAN",result);task.get(3,java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }
}