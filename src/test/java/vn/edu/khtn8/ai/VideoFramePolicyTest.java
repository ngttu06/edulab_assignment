package vn.edu.khtn8.ai;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class VideoFramePolicyTest {
    final VideoFramePolicy policy=new VideoFramePolicy();
    static String jpeg(int width,int height) throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"jpeg",out);
        return "data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());
    }
    @Test void realJpegIsReencodedWithoutPersistingAndRedacted() throws Exception {
        var image=policy.image(jpeg(1280,720));
        assertEquals("image/jpeg",image.contentType());
        assertEquals("video-frame.jpg",image.filename());
        var decoded=ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(image.dataUrl().split(",")[1])));
        assertEquals(1280,decoded.getWidth());assertEquals(720,decoded.getHeight());
        assertFalse(image.toString().contains("base64"));
    }
    @Test void invalidFormatsUrlsAndForgedJpegsRejected() {
        for(String data:new String[]{"https://example.com/frame.jpg","data:image/png;base64,AAAA","data:image/jpeg;base64,!!!!",
                "data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(new byte[]{(byte)255,(byte)216,1,2}),
                "data:image/jpeg;base64,"+"A".repeat(700000)})
            assertEquals(400,assertThrows(ResponseStatusException.class,()->policy.image(data)).getStatusCode().value());
    }
    @Test void oversizedDimensionsRejectedBeforeDecode() throws Exception {
        String image=jpeg(1601,2);
        assertThrows(ResponseStatusException.class,()->policy.image(image));
    }
    @Test void trailingUntrustedBytesAreNotForwarded() throws Exception {
        byte[] clean=Base64.getDecoder().decode(jpeg(40,20).split(",")[1]);
        var out=new ByteArrayOutputStream();out.write(clean);out.write("UNTRUSTED_METADATA".getBytes());
        var accepted=policy.image("data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(out.toByteArray()));
        assertFalse(new String(Base64.getDecoder().decode(accepted.dataUrl().split(",")[1]),java.nio.charset.StandardCharsets.ISO_8859_1).contains("UNTRUSTED_METADATA"));
    }
}