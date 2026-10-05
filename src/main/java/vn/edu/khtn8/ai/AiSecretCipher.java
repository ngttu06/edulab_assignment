package vn.edu.khtn8.ai;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Encryption key must be backed up separately from the database. Never derive it from the JWT secret. */
@Component
public class AiSecretCipher {
    private final byte[] key;
    public AiSecretCipher(@Value("${EDULAB_AI_ENCRYPTION_KEY:}") String encoded) {
        try { key = encoded.isBlank() ? null : Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid AI encryption key encoding"); }
        if (key != null && key.length != 32) throw new IllegalStateException("AI encryption key must contain 32 bytes");
    }
    public boolean available() { return key != null; }
    public String encrypt(String text) {
        requireKey();
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) { throw unavailable(); }
    }
    public String decrypt(String encrypted) {
        requireKey();
        try {
            String[] parts = encrypted.split("\\.", 2);
            return new String(cipher(Cipher.DECRYPT_MODE, Base64.getDecoder().decode(parts[0]))
                    .doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw unavailable(); }
    }
    private Cipher cipher(int mode, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD("EduLab.ai-settings.v1".getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    private void requireKey() {
        if (!available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Server chưa cấu hình EDULAB_AI_ENCRYPTION_KEY để bảo vệ khóa AI.");
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể xử lý khóa AI. Kiểm tra khóa mã hóa trên server.");
    }
}