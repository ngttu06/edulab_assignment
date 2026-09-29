package vn.edu.khtn8.community;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Service
public class ForumService {
    private final JdbcTemplate jdbc;
    public ForumService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String, Object>> categories(AuthenticatedUser user) {
        return jdbc.queryForList("""
                SELECT c.id,c.name,c.description FROM forum_categories c WHERE c.active=TRUE AND
                (c.classroom_id IS NULL OR ? OR
                  EXISTS(SELECT 1 FROM class_teachers t WHERE t.classroom_id=c.classroom_id AND t.teacher_id=?) OR
                  EXISTS(SELECT 1 FROM class_enrollments e WHERE e.classroom_id=c.classroom_id AND e.student_id=? AND e.active=TRUE)) ORDER BY c.id
                """, SchoolService.hasRole(user, "ADMIN"), user.id(), user.id());
    }
    public void categoryAccess(AuthenticatedUser user, long id) {
        if (categories(user).stream().noneMatch(c -> ((Number)c.get("id")).longValue() == id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chuyên mục.");
    }
    public List<Map<String, Object>> threads(AuthenticatedUser user, long categoryId, int page) {
        categoryAccess(user, categoryId);
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang không hợp lệ.");
        return jdbc.queryForList("""
                SELECT t.id,t.title,t.status,t.created_at,u.full_name author FROM forum_threads t JOIN users u ON u.id=t.author_id
                WHERE t.category_id=? AND t.status IN ('PUBLISHED','LOCKED') ORDER BY t.id DESC LIMIT 20 OFFSET ?
                """, categoryId, page * 20);
    }
    public Map<String, Object> thread(AuthenticatedUser user, long id) {
        var rows = jdbc.queryForList("SELECT t.*,u.full_name author FROM forum_threads t JOIN users u ON u.id=t.author_id WHERE t.id=? AND t.status IN ('PUBLISHED','LOCKED')", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thảo luận.");
        var thread = rows.getFirst();
        categoryAccess(user, ((Number)thread.get("category_id")).longValue());
        return thread;
    }
    public List<Map<String, Object>> posts(AuthenticatedUser user, long threadId, int page) {
        thread(user, threadId);
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang không hợp lệ.");
        var posts = jdbc.queryForList("SELECT p.id,p.body,p.author_id,p.created_at,u.full_name author FROM forum_posts p JOIN users u ON u.id=p.author_id WHERE p.thread_id=? AND p.status='PUBLISHED' ORDER BY p.id LIMIT 20 OFFSET ?", threadId, page * 20);
        for (var post : posts) post.put("files", jdbc.queryForList("SELECT f.id,f.original_filename,f.scan_status FROM forum_post_attachments a JOIN stored_files f ON f.id=a.stored_file_id WHERE a.forum_post_id=?", post.get("id")));
        return posts;
    }
    @Transactional
    public long create(AuthenticatedUser user, long categoryId, String title, String body) {
        categoryAccess(user, categoryId);
        return insert("INSERT INTO forum_threads(category_id,author_id,title,body) VALUES(?,?,?,?)", categoryId, user.id(), title.trim(), body.trim());
    }
    @Transactional
    public long reply(AuthenticatedUser user, long threadId, String body, List<Long> fileIds) {
        jdbc.queryForList("SELECT id FROM forum_threads WHERE id=? FOR UPDATE", threadId);
        var thread = thread(user, threadId);
        if (!thread.get("status").equals("PUBLISHED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Thảo luận đã khóa.");
        long postId = insert("INSERT INTO forum_posts(thread_id,author_id,body) VALUES(?,?,?)", threadId, user.id(), body.trim());
        for (long fileId : fileIds.stream().distinct().toList()) {
            var files = jdbc.queryForList("SELECT id FROM stored_files WHERE id=? AND uploaded_by=? AND scan_status IN ('CLEAN','PENDING_SCAN') FOR UPDATE", fileId, user.id());
            if (files.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp đính kèm không hợp lệ.");
            jdbc.update("INSERT INTO forum_post_attachments(forum_post_id,stored_file_id) VALUES(?,?)", postId, fileId);
        }
        return postId;
    }
    @Transactional
    public void moderate(AuthenticatedUser user, long threadId, String status) {
        SchoolService.require(user, "ADMIN", "TEACHER");
        if (!List.of("PUBLISHED", "LOCKED", "HIDDEN").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
        var rows = jdbc.queryForList("SELECT c.id,c.classroom_id FROM forum_threads t JOIN forum_categories c ON c.id=t.category_id WHERE t.id=?", threadId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thảo luận.");
        var category = rows.getFirst();
        categoryAccess(user, ((Number)category.get("id")).longValue());
        if (!SchoolService.hasRole(user, "ADMIN") && category.get("classroom_id") != null &&
                !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM class_teachers WHERE classroom_id=? AND teacher_id=?)", Boolean.class, category.get("classroom_id"), user.id())))
            throw new AccessDeniedException("Class not assigned");
        jdbc.update("UPDATE forum_threads SET status=? WHERE id=?", status, threadId);
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,'MODERATE_THREAD','forum_thread',?)", user.id(), String.valueOf(threadId));
    }
    public boolean canDownload(AuthenticatedUser user, long fileId) {
        var threads = jdbc.queryForList("""
                SELECT t.id FROM forum_post_attachments a JOIN forum_posts p ON p.id=a.forum_post_id
                JOIN forum_threads t ON t.id=p.thread_id WHERE a.stored_file_id=? AND p.status='PUBLISHED' AND t.status IN ('PUBLISHED','LOCKED')
                """, Long.class, fileId);
        for (long id : threads) {
            try { thread(user, id); return true; }
            catch (ResponseStatusException ignored) { }
        }
        return false;
    }
    private long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }
}
