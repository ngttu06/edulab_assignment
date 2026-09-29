package vn.edu.khtn8.files;

import java.io.IOException;
import org.springframework.core.io.Resource;

public interface FileStorage {
    String store(byte[] bytes) throws IOException;
    Resource load(String key) throws IOException;
    void delete(String key) throws IOException;
}
