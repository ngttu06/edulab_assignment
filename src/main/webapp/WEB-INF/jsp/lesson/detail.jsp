<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<a href="${pageContext.request.contextPath}/lessons">← Tất cả bài học</a>
<article class="card mt-3"><div class="card-body p-4">
<nav class="breadcrumb-line" aria-label="Ngữ cảnh bài học"><a href="${pageContext.request.contextPath}/courses/${lesson.courseId()}"><c:out value="${lesson.courseTitle()}"/></a><span aria-hidden="true">/</span><span><c:out value="${lesson.topicName()}"/></span></nav>
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
<section class="card mt-3"><div class="card-body"><span class="eyebrow">TRỢ LÝ HỌC TẬP</span><h2 class="h5">Một câu hỏi. Một khám phá mới.</h2><p class="text-muted small">Câu hỏi và nội dung bài học được gửi tới dịch vụ AI bên thứ ba khi bạn bấm gửi. Không gửi dữ liệu cá nhân; câu trả lời cần được kiểm chứng.</p><form id="aiForm" data-id="${lesson.id()}"><label for="aiQuestion" class="form-label">Em muốn hiểu thêm điều gì?</label><textarea id="aiQuestion" class="form-control mb-3" required maxlength="2000" rows="3" placeholder="Ví dụ: Giải thích kiến thức này bằng một ví dụ trong cuộc sống..."></textarea><button class="btn btn-outline-primary">Hỏi trợ lý AI →</button></form><div id="aiAnswer" class="ai-response" role="status" aria-live="polite"></div></div></section>
<script src="${pageContext.request.contextPath}/js/lesson.js" defer></script>
