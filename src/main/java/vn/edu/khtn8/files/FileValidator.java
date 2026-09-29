package vn.edu.khtn8.files;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class FileValidator {
    private FileValidator() { }
    public static String validate(String name, String declaredMime, byte[] bytes, long imageLimit, long pdfLimit) {
        if (name == null || name.length() > 255 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) throw invalid();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mime = switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> throw invalid();
        };
        if (!mime.equals(declaredMime) || bytes.length == 0 || bytes.length > (mime.equals("application/pdf") ? pdfLimit : imageLimit)) throw invalid();
        boolean valid = switch (mime) {
            case "image/jpeg" -> bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            case "image/png" -> bytes.length >= 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            case "image/webp" -> bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            case "application/pdf" -> bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            default -> false;
        };
        if (!valid) throw invalid();
        return mime;
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ nhận ảnh JPG/PNG/WebP tối đa 5 MB hoặc PDF tối đa 10 MB đúng định dạng.");
    }
}
