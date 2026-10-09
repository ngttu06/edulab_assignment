package vn.edu.khtn8.community;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Renders real JSPs without Spring, credentials, a datasource or external services. */
class SocialCommunityViewTest {
    @TempDir Path work;

    @Test void renderCommunityFixturesForOfflineBrowserRegression() throws Exception {
        var tomcat=new Tomcat();
        tomcat.setBaseDir(work.toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addWebapp("/edulab",Path.of("src/main/webapp").toAbsolutePath().toString());
        context.setParentClassLoader(getClass().getClassLoader());
        // Keep Jasper/JSTL, but never bootstrap the production Spring application.
        context.setContainerSciFilter("org.springframework.web.SpringServletContainerInitializer");
        ((StandardJarScanner)context.getJarScanner()).setScanManifest(false);
        Tomcat.addServlet(context,"fixture",new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)
                    throws java.io.IOException,jakarta.servlet.ServletException {
                String variant=request.getParameter("variant");
                boolean detail="detail".equals(variant),empty="empty".equals(variant);
                var wrapped=new HttpServletRequestWrapper(request) {
                    @Override public Principal getUserPrincipal() {return ()->"ui-fixture";}
                    @Override public boolean isUserInRole(String role) {
                        return "STUDENT".equals(role) || ("admin".equals(variant) && "ADMIN".equals(role));
                    }
                };
                request.setAttribute("pageTitle","Cộng đồng · EduLab");
                request.setAttribute("contentPage","/WEB-INF/jsp/community/list.jsp");
                request.setAttribute("_csrf",Map.of("parameterName","_csrf","token","fixture-only"));
                request.setAttribute("mine",false);
                request.setAttribute("detail",detail);
                request.setAttribute("nextCursor",empty || detail ? null : 199L);
                request.setAttribute("hasMore",!empty && !detail);
                request.setAttribute("grades",List.of(8,10));
                request.setAttribute("subjects",List.of(Map.of("id",1,"name","Khoa học tự nhiên","grade_level",8),
                        Map.of("id",2,"name","Toán","grade_level",10)));
                var first=post(200L,"Minh Anh","Một buổi chiều thật đẹp. Bạn đã có khoảnh khắc nào vui hôm nay?",true,List.of());
                var second=post(199L,"Bảo An","Góc học tập mới của mình. <script>window.untrustedPost=true</script>",false,
                        List.of(Map.of("id",501L,"scan_status","PENDING_SCAN","image_ready",true)));
                second.put("reaction","LOVE");second.put("reaction_label","Yêu thích");second.put("liked",true);
                request.setAttribute("posts",empty?List.of():detail?List.of(first):List.of(first,second));
                wrapped.getRequestDispatcher("/WEB-INF/jsp/layout/default.jsp").forward(wrapped,response);
            }
        });
        context.addServletMappingDecoded("/community","fixture");
        try {
            tomcat.start();
            var client=HttpClient.newHttpClient();
            Path output=Path.of("target/community-ui-fixtures");
            Files.createDirectories(output);
            for(String variant:List.of("feed","detail","empty","admin")) {
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+
                        tomcat.getConnector().getLocalPort()+"/edulab/community?variant="+variant)).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                assertEquals(200,response.statusCode(),response.body());
                var document=Jsoup.parse(response.body());
                assertEquals(0,document.select(".social-hero").size());
                assertEquals(1,document.select("h1").size());
                assertEquals("/edulab/community",document.selectFirst(".discovery-tabs [aria-current=page]").attr("href"));
                assertEquals(1,document.select(".utility-column").size());
                assertEquals(3,document.select("#utilityGrade option").size());
                assertEquals(3,document.select("#utilitySubject option").size());
                assertEquals("detail".equals(variant)?0:1,document.select("#socialForm").size());
                assertEquals(0,document.select("#socialFileHint").size());
                assertEquals(1,document.select("dialog#socialPostViewer[aria-labelledby=socialViewerTitle]").size());
                assertEquals(1,document.select("#socialViewerDetails[aria-label='Nội dung bài viết và bình luận']").size());
                assertEquals(1,document.select("#socialViewerClose[aria-label='Đóng bài viết']").size());
                assertEquals(1,document.select("#socialViewerPrevious[aria-label='Ảnh trước']").size());
                assertEquals(1,document.select("#socialViewerNext[aria-label='Ảnh tiếp theo']").size());
                assertFalse(document.text().contains("Tự động nén"));
                assertFalse(document.text().contains("Không quét antivirus"));
                for(var described:document.select("[aria-describedby]"))
                    for(String id:described.attr("aria-describedby").split("\\s+")) assertNotNull(document.getElementById(id),"Description exists: "+id);
                assertEquals("empty".equals(variant)?1:0,document.select("#socialEmpty").size());
                assertEquals("admin".equals(variant)?2:0,document.select("[data-action=hide]").size());
                assertEquals(List.of("empty","detail").contains(variant)?0:1,document.select(".social-gallery img").size(),"Validated community image renders without CLEAN status");
                for(var card:document.select(".social-post")) {
                    assertEquals(1,card.select("details.social-post-menu summary").size());
                    assertEquals(3,card.select(".social-actions button").size()); // Picker progressively enhanced by JS.
                    assertEquals(1,card.select(".social-comment-form textarea[maxlength=1000]").size());
                    assertEquals(2,card.select(".social-reaction-stack use").size());
                    assertEquals(card.selectFirst(".social-comments").id(),card.selectFirst("[data-action=comments]").attr("aria-controls"));
                }
                assertFalse(document.select(".social-post-text").select("script").iterator().hasNext());
                if(!"empty".equals(variant) && !"detail".equals(variant)) {
                    var selected=document.selectFirst("[data-id=199] [data-action=like]");
                    assertEquals("true",selected.attr("aria-pressed"));assertEquals("Yêu thích",selected.text());
                    assertTrue(selected.selectFirst("use").attr("href").endsWith("#reaction-love"));
                }
                Files.writeString(output.resolve(variant+".html"),response.body(),StandardCharsets.UTF_8);
            }
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    private static Map<String,Object> post(long id,String author,String body,boolean mine,List<Map<String,Object>> images) {
        var post=new java.util.HashMap<String,Object>(Map.of("id",id,"author",author,"body",body,"mine",mine,"images",images,
                "created_at","08/10/2026 · 14:30","like_count",3,"comment_count",1,"liked",false));
        post.put("reaction","");post.put("reaction_label","Thích");post.put("reaction_counts",Map.of("LIKE",2L,"LOVE",1L));
        return post;
    }
}