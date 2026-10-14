<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="nav.jspf" %>
<a href="${pageContext.request.contextPath}/teacher/${kind}">← Danh sách</a>
<h1 class="h3 mt-3"><c:out value="${item.title}"/></h1>
<p id="workspaceNotice" role="status" aria-live="polite"></p>
<c:choose><c:when test="${not empty item.deleted_at}"><p class="alert alert-warning">Đã xóa khỏi nội dung học. Tiến độ và lượt làm vẫn được lưu.</p></c:when><c:otherwise>
<div class="teacher-actions"><a class="btn btn-primary" href="${pageContext.request.contextPath}/teacher/${kind}/${item.id}/edit">Chỉnh sửa</a><button class="btn btn-outline-primary" type="button" data-duplicate="/api/v1/teacher/${kind}/${item.id}/duplicate" data-duplicate-return="/teacher/${kind}/">Nhân bản nháp</button><button class="btn btn-outline-danger" data-delete="/api/v1/teacher/${kind}/${item.id}" data-return="/teacher/${kind}" data-confirm="Xóa nội dung này? Học sinh không thể bắt đầu học/làm mới; lịch sử được giữ. Quiz bị xóa cũng hủy các bài giao liên quan.">Xóa</button></div>
</c:otherwise></c:choose>
<p>Thời lượng: ${item.duration_minutes} phút</p>
<c:choose><c:when test="${kind == 'lessons'}">
<p>Trạng thái: ${item.status == 'PUBLISHED' ? 'Đã xuất bản' : item.status == 'ARCHIVED' ? 'Đã lưu trữ' : 'Bản nháp'}</p><p><c:out value="${item.summary}"/></p>
<h2 class="h5">Mục tiêu</h2><p class="text-break"><c:out value="${item.objectives}"/></p>
<h2 class="h5">Nội dung (HTML nguồn đã lọc)</h2><pre class="text-wrap text-break"><c:out value="${item.content_html}"/></pre>
</c:when><c:otherwise>
<p>${item.published ? 'Đã xuất bản' : 'Chưa xuất bản'} · <c:out value="${item.mode}"/> · Tối đa ${item.max_attempts} lượt</p>
<p><a class="btn btn-primary" href="${pageContext.request.contextPath}/teacher/quizzes/${item.id}/report">Báo cáo, phổ điểm &amp; bài làm học sinh</a></p>
<p>Công bố đáp án (UTC): <c:out value="${item.reveal_after}" default="Sau khi nộp (luyện tập)"/></p>
<c:forEach var="q" items="${item.questions}" varStatus="n"><section class="card mb-3"><div class="card-body"><h2 class="h5">Câu ${n.count}: <c:out value="${q.content()}"/></h2><ol type="A"><c:forEach var="option" items="${q.options()}" varStatus="o"><li><c:out value="${option}"/> ${o.index == q.correctIndex() ? '✓ Đáp án đúng' : ''}</li></c:forEach></ol><p><c:out value="${q.explanation()}"/></p><small>Trọng số: ${q.points()}</small></div></section></c:forEach>
</c:otherwise></c:choose>
<script src="${pageContext.request.contextPath}/js/teacher-workspace.js" defer></script>