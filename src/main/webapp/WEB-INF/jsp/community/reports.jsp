<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<h1 class="h3 mb-3">Báo cáo bài viết cộng đồng</h1>
<p>Chỉ quản trị viên có thể xem báo cáo và xử lý nội dung. <a href="${pageContext.request.contextPath}/community">Về bảng tin</a></p>
<c:choose><c:when test="${empty reports}"><p class="card card-body">Chưa có báo cáo nào.</p></c:when><c:otherwise>
<div class="d-grid gap-3"><c:forEach var="report" items="${reports}"><article class="card"><div class="card-body">
    <h2 class="h5">Bài viết #${report.post_id}</h2><p><strong>Người báo cáo:</strong> <c:out value="${report.reporter}"/> · <c:out value="${report.created_at}"/></p>
    <p><strong>Lý do:</strong> <c:out value="${report.reason}"/></p><p><strong>Nội dung:</strong> <c:out value="${report.body}"/></p><p><strong>Trạng thái:</strong> <c:out value="${report.status}"/></p>
    <div class="d-flex flex-wrap gap-2"><c:if test="${report.status == 'PUBLISHED'}"><a href="${pageContext.request.contextPath}/community/posts/${report.post_id}">Xem bài</a><button type="button" class="btn btn-outline-danger btn-sm report-action" data-id="${report.post_id}" data-type="hide">Ẩn bài</button></c:if><c:if test="${report.status == 'HIDDEN'}"><button type="button" class="btn btn-outline-primary btn-sm report-action" data-id="${report.post_id}" data-type="restore">Hiển thị lại</button></c:if><button type="button" class="btn btn-outline-secondary btn-sm report-action" data-id="${report.post_id}" data-type="resolve">Đã xử lý</button></div>
</div></article></c:forEach></div>
</c:otherwise></c:choose><p id="reportStatus" role="status" aria-live="polite"></p>
<script src="${pageContext.request.contextPath}/js/community-reports.js" defer></script>