<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<div class="community-page">
    <nav class="discovery-tabs" aria-label="Khám phá EduLab"><a href="${pageContext.request.contextPath}/"><ui:icon name="home"/>Trang chủ</a><a class="is-active" href="${pageContext.request.contextPath}/forum" aria-current="page"><ui:icon name="users"/>Cộng đồng</a></nav>
    <header class="community-heading"><div><span class="eyebrow">CÙNG NHAU HIỂU BÀI</span><h1>Cộng đồng EduLab</h1><p>Chia sẻ một câu hỏi. Khám phá một góc nhìn mới.</p></div><a class="btn btn-outline-primary" href="#composer">Đặt câu hỏi</a></header>
    <section id="composer" class="panel feed-composer" aria-labelledby="composerTitle">
        <div class="composer-heading"><span class="feed-avatar" aria-hidden="true"><ui:icon name="user"/></span><div><h2 id="composerTitle">Bạn đang muốn tìm hiểu điều gì?</h2><p>Trao đổi cùng cộng đồng và giáo viên.</p></div></div>
        <p id="forumNotice" role="alert"></p>
        <c:choose><c:when test="${not empty categories}">
        <form id="threadForm">
            <label for="threadCategory">Chuyên mục</label><select id="threadCategory" class="form-select mb-3" required><c:forEach var="category" items="${categories}"><option value="${category.id}" ${category.id == categoryId ? 'selected' : ''}><c:out value="${category.name}"/></option></c:forEach></select>
            <label for="threadTitle" class="visually-hidden">Tiêu đề câu hỏi</label><input id="threadTitle" class="form-control mb-2" placeholder="Tiêu đề câu hỏi của bạn..." required maxlength="240">
            <label for="threadBody" class="visually-hidden">Nội dung câu hỏi</label><textarea id="threadBody" class="form-control mb-3" placeholder="Mô tả điều bạn chưa hiểu, hoặc chia sẻ cách bạn đã thử..." required maxlength="10000" rows="3"></textarea>
            <details class="composer-options"><summary><ui:icon name="book"/> Thêm ảnh / PDF hoặc nhờ AI gợi ý</summary><div class="composer-options-body">
                <label for="threadFiles">Ảnh/PDF bài cần giải (tối đa 3 tệp)</label><input id="threadFiles" type="file" multiple accept="image/jpeg,image/png,image/webp,application/pdf" class="form-control mb-2"><p class="small text-muted">Ảnh tối đa 5 MB, PDF 10 MB hoặc thấp hơn theo cấu hình admin. Upload trực tiếp R2; tệp chờ quét chưa gửi AI.</p>
                <p id="threadUploadStatus" role="status" aria-live="polite" class="small text-break"></p><button id="threadUploadCancel" type="button" class="btn btn-outline-secondary mb-3" hidden>Hủy upload</button>
                <label for="threadLesson">Bài học tham khảo cho AI</label><select id="threadLesson" class="form-select mb-3"><option value="">Không chọn</option><c:forEach var="lesson" items="${lessons}"><option value="${lesson.id()}"><c:out value="${lesson.title()}"/></option></c:forEach></select>
                <div class="form-check"><input id="threadAi" type="checkbox" class="form-check-input"><label for="threadAi" class="form-check-label">Nhờ AI gợi ý sau khi đăng (gửi câu hỏi và tệp tới dịch vụ bên thứ ba)</label></div>
            </div></details>
            <div class="composer-footer"><p>Không chia sẻ số điện thoại, địa chỉ hoặc thông tin riêng tư.</p><button type="submit" class="btn btn-primary">Đăng câu hỏi →</button></div>
        </form>
        </c:when><c:otherwise><p class="text-muted">Bạn chưa có chuyên mục khả dụng để đăng câu hỏi.</p></c:otherwise></c:choose>
    </section>
    <div class="feed-heading"><h2>Bảng tin mới nhất</h2><span>Thảo luận bạn có quyền xem</span></div>
    <nav class="feed-filters" aria-label="Lọc bảng tin"><a href="${pageContext.request.contextPath}/forum" class="${empty categoryId ? 'is-active' : ''}" ${empty categoryId ? 'aria-current="page"' : ''}>Tất cả</a><c:forEach var="category" items="${categories}"><a href="${pageContext.request.contextPath}/forum?category=${category.id}" class="${category.id == categoryId ? 'is-active' : ''}" ${category.id == categoryId ? 'aria-current="page"' : ''}><c:out value="${category.name}"/></a></c:forEach></nav>
    <div id="communityFeed" class="community-feed" data-category="${categoryId}" data-cursor="${nextCursor}">
        <c:forEach var="thread" items="${threads}"><ui:feedCard thread="${thread}"/></c:forEach>
    </div>
    <c:if test="${empty threads}"><section class="panel empty-state"><ui:icon name="chat"/><h2>Chưa có câu hỏi nào ở đây</h2><p>Hãy bắt đầu bằng một điều bạn đang học.</p><a href="#composer">Chia sẻ câu hỏi đầu tiên →</a></section></c:if>
    <p id="feedNotice" role="status" aria-live="polite" class="feed-notice"></p>
    <div id="feedSentinel" class="feed-pagination"><button id="feedLoadMore" class="btn btn-outline-primary" type="button" ${not hasMore ? 'hidden' : ''}>Xem thêm bài viết</button><c:if test="${hasMore}"><c:url var="nextPage" value="/forum"><c:param name="before" value="${nextCursor}"/><c:if test="${not empty categoryId}"><c:param name="category" value="${categoryId}"/></c:if></c:url><noscript><a href="<c:out value='${nextPage}'/>">Trang tiếp theo →</a></noscript></c:if><c:if test="${not hasMore and not empty threads}"><span class="text-muted">Bạn đã xem hết các bài viết hiện tại.</span></c:if></div>
    <template id="feedCardTemplate"><ui:feedCard/></template>
</div>
<script src="${pageContext.request.contextPath}/js/file-upload.js" defer></script>
<script src="${pageContext.request.contextPath}/js/forum.js" defer></script>
<script src="${pageContext.request.contextPath}/js/community.js" defer></script>
