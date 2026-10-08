package vn.edu.khtn8.community;

import java.io.IOException;
import java.sql.Statement;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.files.FileStorage;
import vn.edu.khtn8.files.R2Client;
import vn.edu.khtn8.files.S3Signer;
import vn.edu.khtn8.files.StorageSettingsService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Service
public class SocialCommunityService {
    private final JdbcTemplate jdbc;
    private final FileStorage local;
    private final StorageSettingsService settings;
    private final R2Client r2;
    public SocialCommunityService(JdbcTemplate jdbc, FileStorage local, StorageSettingsService settings, R2Client r2) {
        this.jdbc=jdbc; this.local=local; this.settings=settings; this.r2=r2;
    }
    public record Page(List<Map<String,Object>> items, Long nextCursor, boolean hasMore) { }
    public record Like(boolean liked, long count) { }
    public enum ReactionType {
        LIKE("Thích"), LOVE("Yêu thích"), CARE("Thương thương"), HAHA("Haha"), WOW("Wow"), SAD("Buồn"), ANGRY("Phẫn nộ");
        private final String label;
        ReactionType(String label) {this.label=label;}
        public String getLabel() {return label;}
    }
    public record Reaction(ReactionType reaction, long count, Map<String,Long> counts) { }
    private static boolean communityName(Object filename) {
        if(!(filename instanceof String name) || !name.startsWith("community-") || !name.endsWith(".jpg")) return false;
        String id=name.substring(10,name.length()-4);
        if(!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) return false;
        try {UUID.fromString(id);return true;}
        catch(IllegalArgumentException ex) {return false;}
    }

    public Page feed(AuthenticatedUser user, boolean mine, Long before, int size) {
        if (size<1 || size>20 || (before!=null && before<=0)) throw bad("Phân trang không hợp lệ.");
        var args=new ArrayList<Object>();
        String where="p.status='PUBLISHED'";
        if(mine) { where+=" AND p.author_id=?"; args.add(user.id()); }
        if(before!=null) { where+=" AND p.id<?"; args.add(before); }
        args.add(size+1);
        var rows=jdbc.queryForList("SELECT p.id,p.author_id,p.body,p.created_at,p.updated_at,u.full_name author " +
                "FROM community_posts p JOIN users u ON u.id=p.author_id WHERE "+where+" ORDER BY p.id DESC LIMIT ?",args.toArray());
        boolean more=rows.size()>size;
        var items=new ArrayList<>(rows.subList(0,Math.min(size,rows.size())));
        enrich(user,items);
        return new Page(items,more?number(items.getLast().get("id")):null,more);
    }
    public Map<String,Object> post(AuthenticatedUser user,long id) {
        var rows=jdbc.queryForList("SELECT p.id,p.author_id,p.body,p.created_at,p.updated_at,u.full_name author " +
                "FROM community_posts p JOIN users u ON u.id=p.author_id WHERE p.id=? AND p.status='PUBLISHED'",id);
        if(rows.isEmpty()) throw missing();
        enrich(user,rows);
        return rows.getFirst();
    }
    private void enrich(AuthenticatedUser user,List<Map<String,Object>> items) {
        if(items.isEmpty()) return;
        String marks=String.join(",",java.util.Collections.nCopies(items.size(),"?"));
        Object[] ids=items.stream().map(p->p.get("id")).toArray();
        var images=jdbc.queryForList("SELECT i.post_id,f.id,f.original_filename,f.scan_status FROM community_images i " +
                "JOIN stored_files f ON f.id=i.file_id WHERE i.post_id IN ("+marks+") ORDER BY i.post_id,i.position",ids);
        var comments=jdbc.queryForList("SELECT post_id,COUNT(*) total FROM community_comments WHERE status='PUBLISHED' AND post_id IN ("+marks+") GROUP BY post_id",ids);
        var params=new ArrayList<Object>(); params.add(user.id()); java.util.Collections.addAll(params,ids);
        var likes=jdbc.queryForList("SELECT post_id,reaction_type,COUNT(*) total,MAX(user_id=?) liked FROM community_likes WHERE post_id IN ("+marks+") GROUP BY post_id,reaction_type",params.toArray());
        var byPost=new HashMap<Long,List<Map<String,Object>>>();
        for(var image:images) byPost.computeIfAbsent(number(image.get("post_id")),unused->new ArrayList<>()).add(image);
        var commentCounts=new HashMap<Long,Long>();for(var row:comments) commentCounts.put(number(row.get("post_id")),number(row.get("total")));
        var reactionCounts=new HashMap<Long,Map<String,Long>>();
        var selected=new HashMap<Long,String>();
        for(var row:likes) {
            long id=number(row.get("post_id"));String type=(String)row.get("reaction_type");
            reactionCounts.computeIfAbsent(id,unused->new java.util.TreeMap<>()).put(type,number(row.get("total")));
            if(number(row.get("liked"))>0) selected.put(id,type);
        }
        for(var item:items) {
            long id=number(item.get("id"));var counts=reactionCounts.getOrDefault(id,Map.of());
            long total=counts.values().stream().mapToLong(Long::longValue).sum();
            String reaction=selected.getOrDefault(id,"");
            item.put("images",byPost.getOrDefault(id,List.of()));
            item.put("comment_count",commentCounts.getOrDefault(id,0L));
            item.put("like_count",total); // Legacy clients count all reactions.
            item.put("liked",!reaction.isEmpty());
            item.put("reaction",reaction);
            item.put("reaction_label",reaction.isEmpty()?"Thích":ReactionType.valueOf(reaction).getLabel());
            item.put("reaction_count",total);
            item.put("reaction_counts",sortedCounts(counts));
            item.put("mine",number(item.get("author_id"))==user.id());
        }
    }
    @Transactional
    public long create(AuthenticatedUser user,String body,List<Long> images) {
        String text=body==null?"":body.trim();
        if(text.length()>3000 || (text.isEmpty() && (images==null || images.isEmpty()))) throw bad("Nhập nội dung hoặc chọn ít nhất một ảnh.");
        if(images==null || images.size()>5 || images.stream().anyMatch(id->id==null || id<=0) || images.stream().distinct().count()!=images.size()) throw bad("Tối đa 5 ảnh khác nhau.");
        for(long fileId:images) validateImage(user,fileId);
        long id=insert("INSERT INTO community_posts(author_id,body) VALUES(?,?)",user.id(),text);
        for(int i=0;i<images.size();i++) jdbc.update("INSERT INTO community_images(post_id,file_id,position) VALUES(?,?,?)",id,images.get(i),i);
        return id;
    }
    private void validateImage(AuthenticatedUser user,long fileId) {
        // Lock the file and ensure it has never been attached to a different post.
        var files=jdbc.queryForList("SELECT * FROM stored_files WHERE id=? AND uploaded_by=? AND scan_status='CLEAN' AND content_type='image/jpeg' AND size_bytes<=512000 FOR UPDATE",fileId,user.id());
        if(files.isEmpty() || !communityName(files.getFirst().get("original_filename")) ||
                jdbc.queryForList("SELECT id FROM file_uploads WHERE stored_file_id=? AND status='COMPLETED' AND user_id=?",fileId,user.id()).isEmpty() ||
                !jdbc.queryForList("SELECT file_id FROM community_images WHERE file_id=?",fileId).isEmpty() ||
                !jdbc.queryForList("SELECT stored_file_id FROM forum_thread_attachments WHERE stored_file_id=?",fileId).isEmpty() ||
                !jdbc.queryForList("SELECT stored_file_id FROM forum_post_attachments WHERE stored_file_id=?",fileId).isEmpty())
            throw bad("Ảnh không hợp lệ, chưa quét xong hoặc đã thuộc bài khác.");
        var file=files.getFirst();
        // Reject untrusted full-size originals: browser canvas must create a small, metadata-free JPEG.
        byte[] bytes;
        try {
            long size=number(file.get("size_bytes"));
            if(file.get("storage_profile_id")==null) {
                try(var input=local.load((String)file.get("storage_key")).getInputStream()) { bytes=input.readNBytes((int)size+1); }
            } else bytes=r2.read(settings.profile(number(file.get("storage_profile_id"))),(String)file.get("storage_key"),size);
        } catch(IOException ex) { throw bad("Không đọc được ảnh."); }
        if(bytes.length!=number(file.get("size_bytes")) || !S3Signer.sha256(bytes).equals(file.get("sha256")) || hasExif(bytes)) throw bad("Ảnh không đúng định dạng đã tối ưu.");
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw bad("Ảnh không đọc được.");
            var reader=readers.next();
            try { reader.setInput(stream); int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1 || h<1 || Math.max(w,h)>1600 || (long)w*h>2_560_000 || reader.read(0)==null) throw bad("Ảnh vượt kích thước cho phép.");
            } finally { reader.dispose(); }
        } catch(IOException | IllegalArgumentException ex) { throw bad("Ảnh không đọc được."); }
    }
    static boolean hasExif(byte[] bytes) {
        if(bytes.length<4 || (bytes[0]&255)!=255 || (bytes[1]&255)!=216) return true;
        int pos=2;
        while(pos+3<bytes.length && (bytes[pos]&255)==255) {
            int marker=bytes[pos+1]&255;
            if(marker==0xda || marker==0xd9) return false;
            int length=((bytes[pos+2]&255)<<8)|(bytes[pos+3]&255);
            if(length<2 || pos+2+length>bytes.length) return true;
            if(marker==0xe1 || marker==0xe2 || marker==0xed || marker==0xfe || marker==0xe0 && !isJfif(bytes,pos+4,length-2) || marker==0xee) return true;
            pos+=length+2;
        }
        return true;
    }
    private static boolean isJfif(byte[] bytes,int offset,int size) {
        return size>=5 && bytes[offset]=='J' && bytes[offset+1]=='F' && bytes[offset+2]=='I' && bytes[offset+3]=='F' && bytes[offset+4]==0;
    }
    @Transactional
    public void edit(AuthenticatedUser user,long id,String body) {
        var current=locked(id); owner(user,current);
        if(body==null || body.trim().length()>3000 || (body.isBlank() && jdbc.queryForObject("SELECT COUNT(*) FROM community_images WHERE post_id=?",Long.class,id)==0)) throw bad("Bài cần nội dung hoặc ảnh.");
        jdbc.update("UPDATE community_posts SET body=? WHERE id=?",body.trim(),id);
    }
    @Transactional
    public void remove(AuthenticatedUser user,long id) {
        owner(user,locked(id)); jdbc.update("UPDATE community_posts SET status='REMOVED' WHERE id=?",id);
    }
    @Transactional
    public Like like(AuthenticatedUser user,long id,boolean liked) {
        var result=react(user,id,liked?ReactionType.LIKE:null);
        return new Like(result.reaction()!=null,result.count());
    }
    private static Map<String,Long> sortedCounts(Map<String,Long> counts) {
        var sorted=new java.util.LinkedHashMap<String,Long>();
        counts.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry->sorted.put(entry.getKey(),entry.getValue()));
        return sorted;
    }
    @Transactional
    public Reaction react(AuthenticatedUser user,long id,ReactionType type) {
        locked(id);
        if(type!=null) jdbc.update("INSERT INTO community_likes(post_id,user_id,reaction_type) VALUES(?,?,?) ON DUPLICATE KEY UPDATE reaction_type=VALUES(reaction_type)",id,user.id(),type.name());
        else jdbc.update("DELETE FROM community_likes WHERE post_id=? AND user_id=?",id,user.id());
        var counts=new java.util.TreeMap<String,Long>();
        for(var row:jdbc.queryForList("SELECT reaction_type,COUNT(*) total FROM community_likes WHERE post_id=? GROUP BY reaction_type",id))
            counts.put((String)row.get("reaction_type"),number(row.get("total")));
        return new Reaction(type,counts.values().stream().mapToLong(Long::longValue).sum(),sortedCounts(counts));
    }
    public Page comments(AuthenticatedUser user,long id,Long after,int size) {
        if(size<1 || size>20 || (after!=null && after<=0)) throw bad("Phân trang không hợp lệ.");
        post(user,id);
        var rows=jdbc.queryForList("SELECT c.id,c.post_id,c.author_id,c.body,c.created_at,u.full_name author " +
                "FROM community_comments c JOIN users u ON u.id=c.author_id WHERE c.post_id=? AND c.status='PUBLISHED' AND c.id>? ORDER BY c.id LIMIT ?",id,after==null?0:after,size+1);
        boolean more=rows.size()>size;
        var items=new ArrayList<>(rows.subList(0,Math.min(size,rows.size())));
        for(var item:items) item.put("mine",number(item.get("author_id"))==user.id());
        return new Page(items,more?number(items.getLast().get("id")):null,more);
    }
    @Transactional
    public long comment(AuthenticatedUser user,long id,String body) {
        locked(id);
        if(body==null || body.isBlank() || body.trim().length()>1000) throw bad("Bình luận cần từ 1 đến 1000 ký tự.");
        return insert("INSERT INTO community_comments(post_id,author_id,body) VALUES(?,?,?)",id,user.id(),body.trim());
    }
    @Transactional
    public void deleteComment(AuthenticatedUser user,long postId,long commentId) {
        locked(postId);
        var rows=jdbc.queryForList("SELECT author_id FROM community_comments WHERE id=? AND post_id=? AND status='PUBLISHED'",commentId,postId);
        if(rows.isEmpty()) throw missing();
        if(number(rows.getFirst().get("author_id"))!=user.id() && !SchoolService.hasRole(user,"ADMIN")) throw new AccessDeniedException("Không phải tác giả.");
        jdbc.update("UPDATE community_comments SET status='REMOVED' WHERE id=?",commentId);
    }
    public Map<String,Object> commentView(AuthenticatedUser user,long postId,long commentId) {
        post(user,postId);
        var rows=jdbc.queryForList("SELECT c.id,c.post_id,c.author_id,c.body,c.created_at,u.full_name author FROM community_comments c " +
                "JOIN users u ON u.id=c.author_id WHERE c.post_id=? AND c.id=? AND c.status='PUBLISHED'",postId,commentId);
        if(rows.isEmpty()) throw missing();
        var row=rows.getFirst();row.put("mine",number(row.get("author_id"))==user.id());return row;
    }
    @Transactional
    public void editComment(AuthenticatedUser user,long postId,long commentId,String body) {
        locked(postId);
        var rows=jdbc.queryForList("SELECT author_id FROM community_comments WHERE post_id=? AND id=? AND status='PUBLISHED'",postId,commentId);
        if(rows.isEmpty()) throw missing();
        owner(user,rows.getFirst());
        if(body==null || body.isBlank() || body.trim().length()>1000) throw bad("Bình luận từ 1–1000 ký tự.");
        jdbc.update("UPDATE community_comments SET body=? WHERE id=?",body.trim(),commentId);
    }
    @Transactional
    public void report(AuthenticatedUser user,long id,String reason) {
        var current=locked(id);
        if(number(current.get("author_id"))==user.id() || reason==null || reason.isBlank() || reason.trim().length()>500) throw bad("Lý do báo cáo không hợp lệ.");
        jdbc.update("INSERT INTO community_reports(post_id,reporter_id,reason) VALUES(?,?,?) ON DUPLICATE KEY UPDATE reason=VALUES(reason)",id,user.id(),reason.trim());
    }
    @Transactional
    public void moderate(AuthenticatedUser user,long id,boolean hide) {
        SchoolService.require(user,"ADMIN");
        var current=jdbc.queryForList("SELECT id FROM community_posts WHERE id=? AND status IN ('PUBLISHED','HIDDEN') FOR UPDATE",id);
        if(current.isEmpty()) throw missing();
        jdbc.update("UPDATE community_posts SET status=? WHERE id=?",hide?"HIDDEN":"PUBLISHED",id);
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,?,?,?)",user.id(),hide?"HIDE_COMMUNITY_POST":"RESTORE_COMMUNITY_POST","community_post",String.valueOf(id));
    }
    public List<Map<String,Object>> reports(AuthenticatedUser user) {
        SchoolService.require(user,"ADMIN");
        return jdbc.queryForList("SELECT r.post_id,r.reason,r.created_at,u.full_name reporter,p.body,p.status " +
                "FROM community_reports r JOIN users u ON u.id=r.reporter_id JOIN community_posts p ON p.id=r.post_id " +
                "ORDER BY r.created_at DESC LIMIT 100");
    }
    @Transactional
    public void resolveReports(AuthenticatedUser user,long id) {
        SchoolService.require(user,"ADMIN");
        if(jdbc.update("DELETE FROM community_reports WHERE post_id=?",id)==0) throw missing();
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,'RESOLVE_COMMUNITY_REPORT','community_post',?)",user.id(),String.valueOf(id));
    }
    public boolean canDownload(AuthenticatedUser user,long fileId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM community_images i JOIN community_posts p ON p.id=i.post_id WHERE i.file_id=? AND p.status='PUBLISHED')",Boolean.class,fileId));
    }
    private Map<String,Object> locked(long id) {
        var rows=jdbc.queryForList("SELECT id,author_id FROM community_posts WHERE id=? AND status='PUBLISHED' FOR UPDATE",id);
        if(rows.isEmpty()) throw missing();return rows.getFirst();
    }
    private void owner(AuthenticatedUser user,Map<String,Object> row) {
        if(number(row.get("author_id"))!=user.id()) throw new AccessDeniedException("Không phải tác giả.");
    }
    private long insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder();jdbc.update(connection->{var stmt=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) stmt.setObject(i+1,args[i]);return stmt;},key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }
    private static long number(Object value) {return ((Number)value).longValue();}
    private static ResponseStatusException bad(String reason) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
    private static ResponseStatusException missing() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài viết.");}
}