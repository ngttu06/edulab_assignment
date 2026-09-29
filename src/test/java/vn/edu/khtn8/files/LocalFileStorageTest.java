package vn.edu.khtn8.files;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.khtn8.common.config.AppProperties;
import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTest {
    @TempDir Path directory;
    private LocalFileStorage storage() throws IOException {
        var properties = new AppProperties();
        properties.getUpload().setRoot(directory.toString());
        return new LocalFileStorage(properties);
    }
    @Test void storesWithRandomKeyAndLoadsSameBytes() throws IOException {
        var storage = storage();
        byte[] bytes = {1,2,3};
        String first = storage.store(bytes);
        String second = storage.store(bytes);
        assertNotEquals(first,second);
        assertArrayEquals(bytes,storage.load(first).getContentAsByteArray());
        storage.delete(first);
        assertFalse(Files.exists(directory.resolve(first)));
    }
    @Test void blocksPathTraversal() throws IOException {
        var storage = storage();
        assertThrows(IOException.class, () -> storage.load("../../etc/passwd"));
        assertThrows(IOException.class, () -> storage.delete("../secret"));
    }
    @Test void rejectsSymlinkFile() throws IOException {
        var storage = storage();
        var target = Files.write(directory.resolve("target"),new byte[]{1});
        var key = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        Files.createSymbolicLink(directory.resolve(key),target);
        assertThrows(IOException.class, () -> storage.load(key));
    }
}
