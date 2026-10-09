package vn.edu.khtn8.community;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
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
    public record FeedPage(List<Map<String,Object>> items, Long nextCursor, boolean hasMore) { }
    public record LikeState(boolean liked, long count) { }

    public FeedPage feed(AuthenticatedUser user, Long categoryId, Long before, int size) {
        validateCursor(before, size);
        var allowed = categories(user);
        if (categoryId != null && allowed.stream().noneMatch(c -> number(c.get("id")) == categoryId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chuyên mục.");
        if (allowed.isEmpty()) return new FeedPage(List.of(), null, false);
        var ids = categoryId == null ? allowed.stream().map(c -> number(c.get("id"))).toList() : List.of(categoryId);
        var args = new ArrayList<Object>(ids);
        String cursor = before == null ? "" : " AND t.id<?";
        if (before != null) args.add(before);
        args.add(size + 1);
        var rows = jdbc.queryForList("""
                SELECT t.id,t.title,t.body,t.status,t.author_id,t.category_id,u.full_name author,c.name category_name,
                    DATE_FORMAT(t.created_at,'%Y-%m-%d %H:%i UTC') created_at
                FROM forum_threads t JOIN users u ON u.id=t.author_id JOIN forum_categories c ON c.id=t.category_id
                WHERE t.status IN ('PUBLISHED','LOCKED') AND t.category_id IN (
                """ + placeholders(ids.size()) + ")" + cursor + " ORDER BY t.id DESC LIMIT ?", args.toArray());
        boolean more = rows.size() > size;
        var items = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
        enrichThreads(user, items);
        return new FeedPage(items, more ? number(items.getLast().get("id")) : null, more);
    }
    public FeedPage comments(AuthenticatedUser user, long threadId, Long after, int size) {
        validateCursor(after, size);
        thread(user, threadId);
        var rows = jdbc.queryForList("""
                SELECT p.id,p.body,p.author_id,u.full_name author,DATE_FORMAT(p.created_at,'%Y-%m-%d %H:%i UTC') created_at
                FROM forum_posts p JOIN users u ON u.id=p.author_id
                WHERE p.thread_id=? AND p.status='PUBLISHED' AND p.id>? ORDER BY p.id LIMIT ?
                """, threadId, after == null ? 0 : after, size + 1);
        boolean more = rows.size() > size;
        var items = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
        attachFiles(items, false);
        return new FeedPage(items, more ? number(items.getLast().get("id")) : null, more);
    }
    @Transactional
    public LikeState like(AuthenticatedUser user, long threadId, boolean liked) {
        // Serializes duplicate/concurrent requests with replies and moderation of this thread.
        jdbc.queryForList("SELECT id FROM forum_threads WHERE id=? FOR UPDATE", threadId);
        thread(user, threadId);
        if (liked) jdbc.update("INSERT INTO forum_thread_likes(thread_id,user_id) VALUES(?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", threadId, user.id());
        else jdbc.update("DELETE FROM forum_thread_likes WHERE thread_id=? AND user_id=?", threadId, user.id());
        return new LikeState(liked, jdbc.queryForObject("SELECT COUNT(*) FROM forum_thread_likes WHERE thread_id=?", Long.class, threadId));
    }
    private void enrichThreads(AuthenticatedUser user, List<Map<String,Object>> items) {
        if (items.isEmpty()) return;
        var ids = items.stream().map(t -> t.get("id")).toArray();
        String in = placeholders(ids.length);
        var comments = counts(jdbc.queryForList("SELECT thread_id,COUNT(*) total FROM forum_posts WHERE status='PUBLISHED' AND thread_id IN (" + in + ") GROUP BY thread_id", ids));
        var args = new ArrayList<Object>(); args.add(user.id()); Collections.addAll(args, ids);
        var likes = jdbc.queryForList("SELECT thread_id,COUNT(*) total,MAX(user_id=?) liked FROM forum_thread_likes WHERE thread_id IN (" + in + ") GROUP BY thread_id", args.toArray());
        var states = new HashMap<Long,Map<String,Object>>();
        for (var like : likes) states.put(number(like.get("thread_id")), like);
        for (var item : items) {
            long id = number(item.get("id")); var state = states.get(id);
            item.put("comment_count", comments.getOrDefault(id, 0L));
            item.put("like_count", state == null ? 0L : number(state.get("total")));
            item.put("liked", state != null && number(state.get("liked")) > 0);
        }
        attachFiles(items, true);
    }
    private Map<Long,Long> counts(List<Map<String,Object>> rows) {
        var counts = new HashMap<Long,Long>();
        for (var row : rows) counts.put(number(row.get("thread_id")), number(row.get("total")));
        return counts;
    }
    private void attachFiles(List<Map<String,Object>> items, boolean threads) {
        if (items.isEmpty()) return;
        String table = threads ? "forum_thread_attachments" : "forum_post_attachments";
        String key = threads ? "thread_id" : "forum_post_id";
        var files = jdbc.queryForList("SELECT a." + key + " parent_id,f.id,f.original_filename,f.scan_status,f.content_type FROM " + table +
                " a JOIN stored_files f ON f.id=a.stored_file_id WHERE a." + key + " IN (" + placeholders(items.size()) + ") ORDER BY f.id", items.stream().map(t -> t.get("id")).toArray());
        var byParent = new HashMap<Long,List<Map<String,Object>>>();
        for (var file : files) byParent.computeIfAbsent(number(file.get("parent_id")), ignored -> new ArrayList<>()).add(file);
        for (var item : items) item.put("files", byParent.getOrDefault(number(item.get("id")), List.of()));
    }
    private static long number(Object value) { return ((Number)value).longValue(); }
    private static String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
    private void validateCursor(Long cursor, int size) {
        if ((cursor != null && cursor <= 0) || size < 1 || size > 20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mốc phân trang hoặc số bài không hợp lệ.");
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
        thread.put("files",jdbc.queryForList("SELECT f.id,f.original_filename,f.scan_status,f.content_type FROM forum_thread_attachments a JOIN stored_files f ON f.id=a.stored_file_id WHERE a.thread_id=?",id));
        return thread;
    }
    public List<Map<String, Object>> posts(AuthenticatedUser user, long threadId, int page) {
        thread(user, threadId);
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang không hợp lệ.");
        var posts = jdbc.queryForList("SELECT p.id,p.body,p.author_id,p.created_at,u.full_name author FROM forum_posts p JOIN users u ON u.id=p.author_id WHERE p.thread_id=? AND p.status='PUBLISHED' ORDER BY p.id LIMIT 20 OFFSET ?", threadId, page * 20);
        attachFiles(posts, false);
        return posts;
    }
    @Transactional
    public long create(AuthenticatedUser user, long categoryId, String title, String body) {
        categoryAccess(user, categoryId);
        return insert("INSERT INTO forum_threads(category_id,author_id,title,body) VALUES(?,?,?,?)", categoryId, user.id(), title.trim(), body.trim());
    }
    @Transactional
    public long create(AuthenticatedUser user,long categoryId,String title,String body,Long lessonId,List<Long> fileIds) {
        long id=create(user,categoryId,title,body);
        jdbc.update("UPDATE forum_threads SET ai_lesson_id=? WHERE id=?",lessonId,id);
        for(long fileId:fileIds.stream().distinct().toList()) {
            requireOwnAttachment(user,fileId);
            jdbc.update("INSERT INTO forum_thread_attachments(thread_id,stored_file_id) VALUES(?,?)",id,fileId);
        }
        return id;
    }
    private void requireOwnAttachment(AuthenticatedUser user,long fileId) {
        if(jdbc.queryForList("SELECT id FROM stored_files WHERE id=? AND uploaded_by=? AND scan_status IN ('CLEAN','PENDING_SCAN') AND storage_key NOT LIKE '%/community/files/%' FOR UPDATE",fileId,user.id()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tệp đính kèm không hợp lệ.");
    }
    @Transactional
    public long reply(AuthenticatedUser user, long threadId, String body, List<Long> fileIds) {
        jdbc.queryForList("SELECT id FROM forum_threads WHERE id=? FOR UPDATE", threadId);
        var thread = thread(user, threadId);
        if (!thread.get("status").equals("PUBLISHED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Thảo luận đã khóa.");
        long postId = insert("INSERT INTO forum_posts(thread_id,author_id,body) VALUES(?,?,?)", threadId, user.id(), body.trim());
        for (long fileId : fileIds.stream().distinct().toList()) {
            var files = jdbc.queryForList("SELECT id FROM stored_files WHERE id=? AND uploaded_by=? AND scan_status IN ('CLEAN','PENDING_SCAN') AND storage_key NOT LIKE '%/community/files/%' FOR UPDATE", fileId, user.id());
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
                UNION SELECT t.id FROM forum_thread_attachments a JOIN forum_threads t ON t.id=a.thread_id
                WHERE a.stored_file_id=? AND t.status IN ('PUBLISHED','LOCKED')
                """, Long.class, fileId,fileId);
        for (long id : threads) {
            try { thread(user, id); return true; }
            catch (ResponseStatusException ignored) { }
        }
        return false;
    }

    public List<Map<String,Object>> aiAnswers(AuthenticatedUser user,long threadId) {
        thread(user,threadId);
        return jdbc.queryForList("SELECT answer,source_label,created_at FROM forum_ai_answers WHERE thread_id=? ORDER BY id DESC LIMIT 5",threadId);
    }
    public void saveAi(AuthenticatedUser user,long threadId,String answer,String source) {
        var thread=thread(user,threadId);
        if(((Number)thread.get("author_id")).longValue()!=user.id()) throw new AccessDeniedException("Only author can request AI");
        jdbc.update("INSERT INTO forum_ai_answers(thread_id,requested_by,answer,source_label) VALUES(?,?,?,?)",threadId,user.id(),answer,source);
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
