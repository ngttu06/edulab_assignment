package vn.edu.khtn8.files;

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

@Component
public class StorageSecretCipher {
    private final byte[] key;
    public StorageSecretCipher(@Value("${EDULAB_STORAGE_ENCRYPTION_KEY:}") String encoded) {
        try { key = encoded.isBlank() ? null : Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid storage encryption key"); }
        if (key != null && key.length != 32) throw new IllegalStateException("Storage encryption key must contain 32 bytes");
    }
    public boolean available() { return key != null; }
    public String encrypt(String value) {
        requireKey();
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(cipher(Cipher.ENCRYPT_MODE,nonce).doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw unavailable(); }
    }
    public String decrypt(String value) {
        requireKey();
        try {
            String[] parts=value.split("\\.",2);
            return new String(cipher(Cipher.DECRYPT_MODE,Base64.getDecoder().decode(parts[0])).doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
        } catch (Exception ex) { throw unavailable(); }
    }
    private Cipher cipher(int mode,byte[] nonce) throws Exception {
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        cipher.updateAAD("EduLab.storage.v1".getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    private void requireKey() { if(!available()) throw unavailable(); }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không thể đọc/lưu khóa storage. Kiểm tra EDULAB_STORAGE_ENCRYPTION_KEY trên server.");
    }
}