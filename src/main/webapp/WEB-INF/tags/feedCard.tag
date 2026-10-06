<%@ tag body-content="empty" pageEncoding="UTF-8" %>
<%@ attribute name="thread" required="false" type="java.util.Map" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<article class="panel feed-card" data-id="${thread.id}">
    <header class="feed-card-header"><span class="feed-avatar" aria-hidden="true"><c:out value="${fn:substring(thread.author,0,1)}"/></span><div class="feed-author"><strong><c:out value="${thread.author}"/></strong><span><span class="feed-category"><c:out value="${thread.category_name}"/></span> · <span class="feed-time"><c:out value="${thread.created_at}"/></span></span></div><span class="feed-locked" ${thread.status != 'LOCKED' ? 'hidden' : ''}>Đã khóa</span></header>
    <div class="feed-card-body"><h3><a class="feed-title" href="${pageContext.request.contextPath}/forum/threads/${thread.id}"><c:out value="${thread.title}"/></a></h3><p class="forum-body feed-body"><c:out value="${thread.body}"/></p><ui:attachments files="${thread.files}"/></div>
    <div class="feed-counts"><span><span class="like-dot" aria-hidden="true">♥</span> <span class="feed-like-count">${empty thread ? 0 : thread.like_count}</span> lượt thích</span><a class="feed-comment-link" href="${pageContext.request.contextPath}/forum/threads/${thread.id}"><span class="feed-comment-count">${empty thread ? 0 : thread.comment_count}</span> bình luận</a></div>
    <div class="feed-actions"><button type="button" class="feed-like" aria-pressed="${thread.liked == true}"><span aria-hidden="true">♡</span> Thích</button><button type="button" class="feed-comments-toggle" aria-expanded="false" aria-controls="comments-${thread.id}"><ui:icon name="chat"/> Bình luận</button><button type="button" class="feed-share"><span aria-hidden="true">↗</span> Sao chép liên kết</button></div>
    <p class="feed-card-notice" role="status" aria-live="polite"></p>
    <section id="comments-${thread.id}" class="feed-comments" aria-label="Bình luận bài viết" hidden>
        <div class="feed-comment-list"></div><button type="button" class="comments-more" hidden>Xem thêm bình luận</button>
        <p class="comments-locked text-muted" ${thread.status != 'LOCKED' ? 'hidden' : ''}>Bài viết đã khóa trả lời. Bạn vẫn có thể xem bình luận.</p>
        <form class="feed-reply-form" ${thread.status == 'LOCKED' ? 'hidden' : ''}>
            <label for="reply-${thread.id}" class="visually-hidden">Nội dung bình luận</label><textarea id="reply-${thread.id}" class="form-control" required maxlength="10000" rows="2" placeholder="Chia sẻ cách hiểu của bạn..."></textarea>
            <details><summary>Đính kèm ảnh / PDF</summary><label for="reply-files-${thread.id}" class="small">Tối đa 3 ảnh (5 MB/tệp) hoặc PDF (10 MB/tệp)</label><input id="reply-files-${thread.id}" type="file" class="form-control" multiple accept="image/jpeg,image/png,image/webp,application/pdf"><p id="reply-status-${thread.id}" class="small" role="status" aria-live="polite"></p><button id="reply-cancel-${thread.id}" type="button" class="btn btn-outline-secondary" hidden>Hủy upload</button></details>
            <div class="feed-reply-footer"><a class="feed-detail-link" href="${pageContext.request.contextPath}/forum/threads/${thread.id}">Chi tiết &amp; gợi ý AI →</a><button type="submit" class="btn btn-primary">Gửi bình luận</button></div>
        </form>
    </section>
</article>