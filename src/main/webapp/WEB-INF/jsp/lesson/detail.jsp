<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<a href="${pageContext.request.contextPath}/lessons">← Tất cả bài học</a>
<div class="lesson-layout mt-3"><article class="lesson-reader card"><div class="card-body">
<nav class="breadcrumb-line" aria-label="Ngữ cảnh bài học"><a href="${pageContext.request.contextPath}/courses/${lesson.courseId()}"><c:out value="${lesson.courseTitle()}"/></a><span aria-hidden="true">/</span><span><c:out value="${lesson.topicName()}"/></span></nav>
<h1 class="h2"><c:out value="${lesson.title()}"/></h1>
<p class="text-muted"><c:out value="${lesson.summary()}"/> · <c:out value="${lesson.durationMinutes()}"/> phút</p>
<p><strong>Mục tiêu:</strong> <c:out value="${lesson.objectives()}"/></p>
<div class="lesson-content">${lesson.contentHtml()}</div>
<hr><p>Tiến độ: <strong id="progressValue"><c:out value="${lesson.completionPercent()}"/>%</strong></p>
<c:if test="${lesson.hasVideo()}"><p class="text-muted">Tiến độ bài này được tính từ các đoạn video đã xem.</p><a class="btn btn-primary" href="${pageContext.request.contextPath}/lessons/${lesson.id()}/video">Học qua video →</a></c:if>
<c:if test="${pageContext.request.isUserInRole('STUDENT') and not lesson.hasVideo()}">
<button id="complete" type="button" class="btn btn-success" data-id="${lesson.id()}">Đánh dấu hoàn thành</button>
</c:if>
<p id="lessonNotice" class="mt-3" role="status"></p>
</div></article>
<aside class="ai-companion card"><div class="card-body"><span class="eyebrow">TRỢ LÝ HỌC TẬP</span><h2 class="h5">Một câu hỏi. Một khám phá mới.</h2><p class="text-muted small">Câu hỏi, nội dung bài học và file đính kèm được gửi tới dịch vụ AI bên thứ ba khi bạn bấm gửi. Không gửi dữ liệu cá nhân; câu trả lời cần được kiểm chứng.</p><form id="aiForm" data-id="${lesson.id()}"><label for="aiQuestion" class="form-label">Em muốn hiểu thêm điều gì?</label><textarea id="aiQuestion" class="form-control mb-3" required maxlength="2000" rows="3" placeholder="Ví dụ: Giải thích từng bước bài trong ảnh đính kèm..."></textarea>
<label for="aiFiles" class="form-label">Ảnh/PDF bài cần giải (tối đa 3 file)</label><input id="aiFiles" type="file" multiple accept="image/jpeg,image/png,image/webp,application/pdf" class="form-control" aria-describedby="aiFileHint"><p id="aiFileHint" class="small text-muted mt-1">Ảnh tối đa 5 MB, PDF 10 MB; admin có thể đặt giới hạn thấp hơn. Upload trực tiếp R2; chỉ file quét đạt yêu cầu và model hỗ trợ mới gửi AI.</p><p id="aiUploadStatus" role="status" aria-live="polite" class="small text-break"></p><button id="aiUploadCancel" type="button" class="btn btn-outline-secondary mb-3" hidden>Hủy upload</button>
<button type="submit" class="btn btn-outline-primary">Hỏi trợ lý AI →</button></form><div id="aiAnswer" class="ai-response" role="status" aria-live="polite"></div></div></aside></div>
<script src="${pageContext.request.contextPath}/js/file-upload.js" defer></script>
<script src="${pageContext.request.contextPath}/js/lesson.js" defer></script>
