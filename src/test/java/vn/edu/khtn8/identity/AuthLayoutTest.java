package vn.edu.khtn8.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthLayoutTest {
    private String jsp(String name) throws IOException {
        return Files.readString(Path.of("src/main/webapp/WEB-INF/jsp", name + ".jsp"));
    }

    @Test void bothLayoutsIncludeSharedHeaderAndFooterExactlyOnce() throws IOException {
        for (String name : new String[]{"layout/default", "layout/auth"}) {
            String source = jsp(name);
            for (String fragment : new String[]{"header", "footer"}) {
                String include = "page=\"/WEB-INF/jsp/layout/" + fragment + ".jsp\"";
                assertEquals(1, source.split(java.util.regex.Pattern.quote(include), -1).length - 1);
            }
            assertFalse(source.contains("<header"), "Header belongs in the shared fragment");
            assertFalse(source.contains("<footer"), "Footer belongs in the shared fragment");
        }
    }

    @Test void authenticationShellOwnsDocumentAssetsAndAccessibleLandmarks() throws IOException {
        String source = jsp("layout/auth");
        var document = Jsoup.parse(source);
        assertEquals("vi", document.selectFirst("html").attr("lang"));
        assertEquals(1, document.select("main#mainContent[tabindex=-1]").size());
        assertEquals(1, document.select("a.skip-link[href=#mainContent]").size());
        assertEquals(1, document.select("#globalNotice[role=alert]").size());
        assertEquals(1, document.select("meta[name=app-context]").size());
        for (String asset : new String[]{"bootstrap.min.css", "fonts.css", "edulab.css"}) {
            assertEquals(1, document.select("link[href$='" + asset + "']").size());
        }
        for (String asset : new String[]{"app.js", "auth.js"}) {
            assertEquals(1, document.select("script[src$='" + asset + "'][defer]").size());
        }
        assertTrue(source.contains("<jsp:param name=\"showMenu\" value=\"false\"/>"));
        assertTrue(source.contains("<jsp:include page=\"${contentPage}\"/>"));
    }

    @Test void authenticationContentRemainsFragmentsWithExistingFormContracts() throws IOException {
        for (String name : new String[]{"login", "register"}) {
            String source = jsp("auth/" + name);
            assertFalse(java.util.regex.Pattern.compile("(?i)<!doctype|<(?:html|head|body|header|footer|main|script|link)\\b")
                    .matcher(source).find(), "Authentication content must not duplicate the shell");
            var fragment = Jsoup.parseBodyFragment(source);
            assertEquals(1, fragment.select(".auth-story").size());
            assertEquals(1, fragment.select(".auth-card h1").size());
            assertEquals(1, fragment.select("form#" + name + "Form").size());
            assertEquals(1, fragment.select("label[for=username]").size());
            assertEquals(1, fragment.select("input#username[required][autocomplete=username]").size());
            assertEquals(1, fragment.select("input#password[type=password][required]").size());
            assertEquals(1, fragment.select("form button[type=submit]").size());
            String otherPage = "login".equals(name) ? "register" : "login";
            assertEquals(1, fragment.select(".auth-switch a[href$='/" + otherPage + "']").size());
        }
        var login = Jsoup.parseBodyFragment(jsp("auth/login"));
        assertEquals(1, login.select("#error[role=alert]").size());
        assertEquals("current-password", login.selectFirst("#password").attr("autocomplete"));
        assertEquals("80", login.selectFirst("#username").attr("maxlength"));
        var register = Jsoup.parseBodyFragment(jsp("auth/register"));
        assertEquals(1, register.select("#notice[role=alert]").size());
        assertEquals(1, register.select("input#fullName[required][maxlength=160][autocomplete=name]").size());
        assertEquals(1, register.select("input#email[type=email][required][maxlength=160][autocomplete=email]").size());
        assertEquals("[A-Za-z0-9._-]{3,80}", register.selectFirst("#username").attr("pattern"));
        assertEquals("new-password", register.selectFirst("#password").attr("autocomplete"));
        assertEquals("8", register.selectFirst("#password").attr("minlength"));
        assertEquals("72", register.selectFirst("#password").attr("maxlength"));
        assertEquals(java.util.List.of("STUDENT", "PARENT"), register.select("#role option").eachAttr("value"));
    }
}