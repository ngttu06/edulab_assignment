package vn.edu.khtn8.ai;

import jakarta.servlet.http.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Principal;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual admin JSP, without Spring, a database, credentials or provider requests. */
class AiSettingsViewTest {
    @TempDir Path work;
    @Test void renderAiSettingsForOfflineBrowserRegression() throws Exception {
        var tomcat=new Tomcat();tomcat.setBaseDir(work.toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addWebapp("/edulab",Path.of("src/main/webapp").toAbsolutePath().toString());
        context.setParentClassLoader(getClass().getClassLoader());
        context.setContainerSciFilter("org.springframework.web.SpringServletContainerInitializer");
        ((StandardJarScanner)context.getJarScanner()).setScanManifest(false);
        Tomcat.addServlet(context,"fixture",new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)
                    throws java.io.IOException,jakarta.servlet.ServletException {
                var wrapped=new HttpServletRequestWrapper(request) {
                    @Override public Principal getUserPrincipal() {return ()->"admin-fixture";}
                    @Override public boolean isUserInRole(String role) {return "ADMIN".equals(role);}
                };
                request.setAttribute("pageTitle","Cài đặt AI · EduLab");
                request.setAttribute("adminTitle","Cài đặt AI");request.setAttribute("adminActive","ai");
                request.setAttribute("adminNavigation",vn.edu.khtn8.administration.AdminResources.all());
                request.setAttribute("settingsKind","ai");
                request.setAttribute("contentPage","/WEB-INF/jsp/admin/settings.jsp");
                request.getRequestDispatcher("/WEB-INF/jsp/layout/admin.jsp").forward(wrapped,response);
            }
        });
        context.addServletMappingDecoded("/admin/settings/ai","fixture");
        try {
            tomcat.start();
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/edulab/admin/settings/ai")).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200,response.statusCode(),response.body());
            var doc=Jsoup.parse(response.body());
            for(String id:new String[]{"aiSystemPrompt","aiSupportSystemPrompt"}) {
                assertEquals("8000",doc.getElementById(id).attr("maxlength"));assertEquals("textarea",doc.getElementById(id).tagName());
            }
            assertEquals("button",doc.getElementById("aiFetchModels").attr("type"));
            assertTrue(doc.getElementById("aiModelList").hasAttr("disabled"));
            for(var element:doc.select("[aria-describedby]"))
                for(String id:element.attr("aria-describedby").split("\\s+")) assertNotNull(doc.getElementById(id));
            var output=Path.of("target/ai-ui-fixtures");Files.createDirectories(output);
            Files.writeString(output.resolve("settings.html"),response.body(),StandardCharsets.UTF_8);
        } finally {tomcat.stop();tomcat.destroy();}
    }
}