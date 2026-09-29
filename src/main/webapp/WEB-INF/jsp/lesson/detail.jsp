<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<a href="${pageContext.request.contextPath}/lessons">← Tất cả bài học</a>
<article class="card mt-3"><div class="card-body p-4">
<p class="text-primary"><c:out value="${lesson.topicName()}"/></p>
<h1 class="h2"><c:out value="${lesson.title()}"/></h1>
<p class="text-muted"><c:out value="${lesson.summary()}"/> · <c:out value="${lesson.durationMinutes()}"/> phút</p>
<p><strong>Mục tiêu:</strong> <c:out value="${lesson.objectives()}"/></p>
<div class="lesson-content">${lesson.contentHtml()}</div>
<hr><p>Tiến độ: <strong id="progressValue"><c:out value="${lesson.completionPercent()}"/>%</strong></p>
<c:if test="${pageContext.request.isUserInRole('STUDENT')}">
<button id="complete" type="button" class="btn btn-success" data-id="${lesson.id()}">Đánh dấu hoàn thành</button>
</c:if>
<p id="lessonNotice" class="mt-3" role="status"></p>
</div></article>
<c:if test="${not pageContext.request.isUserInRole('ADMIN')}">
<section class="card mt-3"><div class="card-body"><h2 class="h5">Trợ giúp AI mẫu</h2><p>Chưa kết nối AI thật. Không gửi thông tin cá nhân; kết quả cần giáo viên kiểm tra.</p><form id="aiForm" data-id="${lesson.id()}"><label for="aiQuestion">Câu hỏi về bài học</label><textarea id="aiQuestion" class="form-control mb-3" required maxlength="2000" rows="3"></textarea><button class="btn btn-outline-primary">Nhận hướng dẫn mẫu</button></form><p id="aiAnswer" class="mt-3" role="status"></p></div></section>
</c:if>
<script src="${pageContext.request.contextPath}/js/lesson.js" defer></script>
