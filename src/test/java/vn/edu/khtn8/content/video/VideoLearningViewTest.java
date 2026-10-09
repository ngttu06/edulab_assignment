package vn.edu.khtn8.content.video;

import jakarta.servlet.http.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Principal;
import java.util.Map;
import java.util.List;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.khtn8.content.dto.LessonDetail;
import static org.junit.jupiter.api.Assertions.*;

/** Real JSP rendering without Spring bootstrapping, database, cloud credentials or AI calls. */
class VideoLearningViewTest {
    @TempDir Path work;
    @Test void renderVideoForOfflineBrowserRegression() throws Exception {
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
                    @Override public Principal getUserPrincipal() { return ()->"video-fixture"; }
                    @Override public boolean isUserInRole(String role) { return "STUDENT".equals(role); }
                };
                request.setAttribute("pageTitle","Video bài học · EduLab");
                boolean manage=request.getServletPath().equals("/teacher/videos");
                request.setAttribute("contentPage",manage ? "/WEB-INF/jsp/lesson/video-manage.jsp" : "/WEB-INF/jsp/lesson/video.jsp");
                request.setAttribute("_csrf",Map.of("parameterName","_csrf","token","fixture-only"));
                request.setAttribute("lesson",new LessonDetail(1L,"Chất và sự biến đổi","Phản ứng hóa học","",10,"","<p>Nội dung bài học</p>","NOT_STARTED",0,1L,"Khoa học tự nhiên 8","Khoa học tự nhiên",8,true));
                boolean hls="hls".equals(request.getParameter("source"));
                boolean mp4="mp4".equals(request.getParameter("source"));
                request.setAttribute("video",hls
                        ? new VideoModels.Video(2,1,1,"PLAYERJS","https://media.test/fixture.m3u8",null,600,95)
                        : mp4 ? new VideoModels.Video(2,1,1,"PLAYERJS","https://unconfigured.example.org/fixture.MP4?token=test%2B123",null,6,95)
                        : new VideoModels.Video(2,1,1,"YOUTUBE","https://www.youtube.com/watch?v=M7lc1UVf-VE","M7lc1UVf-VE",600,95));
                request.setAttribute("videoAllHosts",true);
                request.setAttribute("videoHosts",List.of());
                request.setAttribute("lessons",List.of(Map.of("id",1,"subject_name","Khoa học tự nhiên","grade_level",8,
                        "title","Video offline","course_title","Khoa học tự nhiên 8","status","PUBLISHED")));
                request.setAttribute("playerAvailable",hls || mp4 || "true".equals(request.getParameter("installed")));
                request.setAttribute("playerPath","/js/vendor/playerjs.js");
                request.getRequestDispatcher("/WEB-INF/jsp/layout/default.jsp").forward(wrapped,response);
            }
        });
        context.addServletMappingDecoded("/lessons/1/video","fixture");
        context.addServletMappingDecoded("/teacher/videos","fixture");
        try {
            tomcat.start();
            var output=Path.of("target/video-ui-fixtures");Files.createDirectories(output);
            for(String source:new String[]{"youtube","hls","mp4"}) {
                var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/edulab/lessons/1/video?source="+source)).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                assertEquals(200,response.statusCode(),response.body());
                var doc=Jsoup.parse(response.body());
                assertEquals(1,doc.select("#videoAiForm").size());assertEquals(0,doc.select("#aiChatToggle").size());
                assertEquals("2",doc.selectFirst("#videoLearning").attr("data-video"));
                assertEquals(source.equals("youtube") ? "YOUTUBE" : "PLAYERJS",doc.selectFirst("#videoLearning").attr("data-provider"));
                if (source.equals("mp4")) {
                    assertEquals("true",doc.selectFirst("#videoLearning").attr("data-player-available"));
                    assertTrue(doc.selectFirst("#videoLearning").attr("data-source").contains(".MP4?token="));
                }
                assertEquals("false",doc.selectFirst("#videoFullscreen").attr("aria-pressed"));
                assertEquals("Phát video",doc.selectFirst("#videoPlay").attr("aria-label"));
                assertEquals("true",doc.selectFirst("#videoAiToggle").attr("aria-expanded"));
                assertEquals("videoAi",doc.selectFirst("#videoAiToggle").attr("aria-controls"));
                assertEquals(0,doc.select("#videoAi #videoAiToggle").size());
                assertEquals(0,doc.select("#videoAi[hidden]").size());
                assertEquals(0,doc.select(".video-controls .timeline-legend").size());
                assertEquals(1,doc.select(".video-progress-card .timeline-legend").size());
                assertEquals(1,doc.select("#videoAiPreview[hidden]").size());
                for(var element:doc.select("[aria-describedby], [aria-controls], [aria-labelledby]"))
                    for(String attribute:new String[]{"aria-describedby","aria-controls","aria-labelledby"})
                        for(String id:element.attr(attribute).split("\\s+")) if(!id.isEmpty()) assertNotNull(doc.getElementById(id));
                Files.writeString(output.resolve(source.equals("youtube") ? "watch.html" : "watch-"+source+".html"),response.body(),StandardCharsets.UTF_8);
            }
            var manage=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/edulab/teacher/videos")).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200,manage.statusCode(),manage.body());
            var doc=Jsoup.parse(manage.body());
            assertTrue(doc.selectFirst("#videoSourcePolicy").text().contains("tất cả domain HTTPS"));
            assertTrue(doc.selectFirst("#videoProvider option[value=PLAYERJS]").text().contains("MP4"));
            assertEquals("false",doc.selectFirst("#videoManageForm").attr("data-player-available"));
            assertFalse(doc.text().contains("chưa cấu hình (EDULAB_VIDEO_ALLOWED_HOSTS)"));
            Files.writeString(output.resolve("manage.html"),manage.body(),StandardCharsets.UTF_8);
            var installed=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/edulab/teacher/videos?installed=true")).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200,installed.statusCode(),installed.body());
            var installedDoc=Jsoup.parse(installed.body());
            assertEquals("true",installedDoc.selectFirst("#videoManageForm").attr("data-player-available"));
            assertFalse(installedDoc.text().contains("PlayerJS chưa được cài"));
            Files.writeString(output.resolve("manage-installed.html"),installed.body(),StandardCharsets.UTF_8);
        } finally { tomcat.stop();tomcat.destroy(); }
    }
}