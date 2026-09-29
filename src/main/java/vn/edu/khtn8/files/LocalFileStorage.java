package vn.edu.khtn8.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import vn.edu.khtn8.common.config.AppProperties;

@Component
public class LocalFileStorage implements FileStorage {
    private final Path root;
    public LocalFileStorage(AppProperties properties) throws IOException {
        root = Path.of(properties.getUpload().getRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Storage root must not be a symlink");
    }
    public String store(byte[] bytes) throws IOException {
        String key = UUID.randomUUID().toString();
        Files.write(path(key), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return key;
    }
    public Resource load(String key) throws IOException {
        Path file = path(key);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("File not available");
        return new FileSystemResource(file);
    }
    public void delete(String key) throws IOException { Files.deleteIfExists(path(key)); }
    private Path path(String key) throws IOException {
        if (key == null || !key.matches("[a-f0-9-]{36}")) throw new IOException("Invalid storage key");
        return root.resolve(key);
    }
}
