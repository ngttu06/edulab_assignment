package vn.edu.khtn8.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileLayoutTest {
    @Test void profileFormsHaveAccessibleFieldsAndDoNotExposePrivateData() throws IOException {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/profile.jsp"));
        var document = Jsoup.parseBodyFragment(source);
        assertEquals(1, document.select("h1").size());
        assertEquals(1, document.select("form#profileForm").size());
        assertEquals(1, document.select("form#passwordForm").size());
        for (String id : new String[]{"profileFullName", "profileEmail", "profileUsername", "currentPassword", "newPassword", "confirmPassword"}) {
            assertEquals(1, document.select("input#" + id).size());
            assertEquals(1, document.select("label[for=" + id + "]").size());
        }
        assertEquals(1, document.select("#profileUsername[readonly]").size());
        assertEquals(1, document.select("#profileEmail[type=email][autocomplete=email][required]").size());
        assertEquals(1, document.select("#currentPassword[type=password][autocomplete=current-password][required]").size());
        assertEquals(2, document.select("input[type=password][autocomplete=new-password][minlength=8]").size());
        assertEquals(2, document.select("[role=status][aria-live=polite]").size());
        assertEquals(1, document.select("script[src$='profile.js'][defer]").size());
        assertFalse(source.contains("passwordHash")); assertFalse(source.contains("authVersion"));
        assertTrue(source.contains("<c:out value='${profile.fullName()}'/>"));
        assertTrue(source.contains("<c:out value='${profile.email()}'/>"));
    }
}