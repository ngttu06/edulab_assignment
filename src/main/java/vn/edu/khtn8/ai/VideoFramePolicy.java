package vn.edu.khtn8.ai;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Ephemeral, decoded and re-encoded JPEGs only; never a general file/storage upload. */
@Component
public class VideoFramePolicy {
    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_DATA_URL_LENGTH = 700000;
    private static final String PREFIX = "data:image/jpeg;base64,";

    public AiProvider.Attachment image(String dataUrl) {
        if (dataUrl == null || dataUrl.length() > MAX_DATA_URL_LENGTH || !dataUrl.startsWith(PREFIX)) throw invalid();
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(dataUrl.substring(PREFIX.length())); }
        catch (IllegalArgumentException ex) { throw invalid(); }
        if (bytes.length < 4 || bytes.length > MAX_BYTES || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216) throw invalid();
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!reader.getFormatName().equalsIgnoreCase("JPEG") || width < 1 || height < 1 || width > 1600 || height > 1600) throw invalid();
                var decoded = reader.read(0);
                var clean = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                var graphics = clean.createGraphics();
                try { graphics.drawImage(decoded, 0, 0, null); }
                finally { graphics.dispose(); decoded.flush(); }
                var output = new ByteArrayOutputStream();
                try {
                    if (!ImageIO.write(clean, "jpeg", output) || output.size() > MAX_BYTES) throw invalid();
                } finally { clean.flush(); }
                return new AiProvider.Attachment("video-frame.jpg", "image/jpeg", PREFIX + Base64.getEncoder().encodeToString(output.toByteArray()));
            } finally { reader.dispose(); }
        } catch (java.io.IOException | IllegalArgumentException ex) { throw invalid(); }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khung hình phải là ảnh JPEG hợp lệ, tối đa 512 KiB và 1600 × 1600 px.");
    }
}