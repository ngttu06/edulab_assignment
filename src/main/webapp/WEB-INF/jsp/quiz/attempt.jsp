<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:choose><c:when test="${teacherReview}">
<a href="${pageContext.request.contextPath}/teacher/quizzes/${student.quiz_id}/report">← Báo cáo quiz</a>
<p class="mt-3">Học sinh: <strong><c:out value="${student.full_name}"/></strong> · <c:out value="${student.username}"/></p>
<p>Bắt đầu (UTC): <c:out value="${student.started_at}"/> · Nộp (UTC): <c:out value="${student.submitted_at}" default="Chưa nộp"/></p>
</c:when><c:otherwise><a href="${pageContext.request.contextPath}/quizzes/history">← Lịch sử làm bài</a></c:otherwise></c:choose>
<h1 class="h3 mt-3"><c:out value="${attempt.title()}"/></h1>
<p><span class="badge text-bg-light">${attempt.mode() == 'TEST' ? 'Bài kiểm tra' : 'Luyện tập'}</span> · Hạn nộp: <time id="deadline" datetime="${attempt.deadline()}"><c:out value="${attempt.deadline()}"/></time></p>
<c:if test="${attempt.status() == 'IN_PROGRESS' and not teacherReview}">
<p>Thời gian còn lại: <strong id="quizTimer" aria-live="off"></strong>. Câu trả lời tự lưu khi chọn; đồng hồ server quyết định hạn nộp.</p>
<c:if test="${attempt.mode() == 'TEST'}"><div class="alert alert-warning">Khi chuyển tab, thu nhỏ cửa sổ hoặc rời trang, hệ thống ghi nhận và báo số lần cho giáo viên. Không tự trừ điểm. <strong id="tabWarning" role="status" aria-live="polite"></strong></div></c:if>
</c:if>
<c:if test="${teacherReview and attempt.mode() == 'TEST'}">
<details class="alert alert-warning"><summary>Số lần rời tab ghi nhận: ${events.size()}</summary><p class="small">Dữ liệu hỗ trợ đánh giá, không phải bằng chứng chắc chắn về gian lận. Có thể thiếu sự kiện khi mất mạng/đóng ứng dụng.</p><ol><c:forEach var="event" items="${events}"><li><c:out value="${event.received_at}"/> UTC</li></c:forEach></ol></details>
</c:if>
<c:if test="${attempt.status() == 'GRADED'}">
<section class="card mb-3"><div class="card-body"><h2 class="h5">Kết quả: <c:out value="${attempt.score()}"/>/10</h2>
<c:if test="${attempt.expired()}"><p>Hết giờ — chấm theo câu trả lời đã lưu trước hạn.</p></c:if>
<c:choose><c:when test="${attempt.answersVisible()}"><p><strong>${attempt.correctCount()}</strong> đúng · <strong>${attempt.wrongCount()}</strong> sai · <strong>${attempt.unansweredCount()}</strong> bỏ trống / ${attempt.questions().size()} câu</p></c:when><c:otherwise><p>Đáp án, câu sai và giải thích sẽ mở theo lịch công bố: <time datetime="${attempt.revealAfter()}"><c:out value="${attempt.revealAfter()}"/></time>.</p></c:otherwise></c:choose>
</div></section>
<c:if test="${not teacherReview}"><section class="card mb-3" id="aiReview" data-id="${attempt.id()}"><div class="card-body"><h2 class="h5">Nhận xét AI</h2><p class="small text-muted">AI chỉ hỗ trợ học tập, không thay đổi điểm. ${attempt.answersVisible() ? 'Nhận xét dựa trên kết quả và nội dung bài.' : 'Trước giờ công bố, chỉ nhận xét tổng quan theo điểm, không phân tích đáp án.'}</p><p id="aiReviewText" class="assessment-ai-text" role="status" aria-live="polite">Đang chuẩn bị nhận xét…</p><button type="button" id="retryAiReview" class="btn btn-outline-primary btn-sm">Tạo / tải lại nhận xét</button></div></section></c:if>
<c:if test="${attempt.answersVisible()}"><label for="questionFilter" class="form-label">Xem lại câu hỏi</label><select id="questionFilter" class="form-select mb-3"><option value="all">Tất cả câu hỏi</option><option value="WRONG">Câu sai</option><option value="UNANSWERED">Bỏ trống</option><option value="CORRECT">Câu đúng</option></select><p id="questionFilterEmpty" hidden role="status">Không có câu hỏi thuộc bộ lọc này.</p></c:if>
</c:if>
<p id="quizNotice" role="alert"></p>
<form id="quizForm" data-id="${attempt.id()}" data-status="${attempt.status()}" data-mode="${attempt.mode()}" data-teacher="${teacherReview ? 'true' : 'false'}">
<c:forEach var="question" items="${attempt.questions()}" varStatus="index">
<fieldset class="card mb-3 assessment-question" data-outcome="${question.outcome()}"><div class="card-body"><legend class="h5">${index.count}. <c:out value="${question.text()}"/></legend>
<c:if test="${attempt.answersVisible()}"><p class="fw-semibold">${question.outcome() == 'CORRECT' ? '✓ Trả lời đúng' : question.outcome() == 'WRONG' ? '✗ Trả lời sai' : '— Chưa trả lời'}</p></c:if>
<c:forEach var="option" items="${question.options()}">
<div class="form-check"><input type="radio" class="form-check-input" id="option-${option.id()}" name="${question.id()}" value="${option.id()}" ${attempt.status() != 'IN_PROGRESS' or teacherReview ? 'disabled' : ''} ${question.selectedOption() == option.id() ? 'checked' : ''}>
<label class="form-check-label" for="option-${option.id()}"><c:out value="${option.text()}"/><c:if test="${option.correct() == true}"> — ✓ Đáp án đúng</c:if><c:if test="${question.selectedOption() == option.id() and attempt.status() == 'GRADED'}"> — Đã chọn</c:if></label></div>
</c:forEach>
<c:if test="${attempt.answersVisible()}"><p class="mt-3 assessment-ai-text"><c:out value="${question.explanation()}"/></p><c:if test="${not teacherReview}"><button type="button" class="btn btn-outline-primary btn-sm askQuestionAi" data-question="${question.id()}" aria-controls="ai-question-${question.id()}">Hỏi AI giải thích câu này</button><p id="ai-question-${question.id()}" class="assessment-ai-text mt-2" role="status" aria-live="polite"></p></c:if></c:if>
</div></fieldset></c:forEach>
<c:if test="${attempt.status() == 'IN_PROGRESS' and not teacherReview}"><button type="submit" class="btn btn-success">Nộp bài</button></c:if>
</form>
<c:if test="${teacherReview and not empty reviews}"><section class="card mt-3"><div class="card-body"><h2 class="h5">Nhận xét AI đã tạo cho học sinh</h2><c:forEach var="review" items="${reviews}"><h3 class="h6">${review.review_type == 'QUESTION' ? 'Giải thích từng câu' : 'Nhận xét tổng quan'} ${review.sample ? '· AI mẫu' : ''}</h3><p class="assessment-ai-text"><c:out value="${review.content}"/></p></c:forEach></div></section></c:if>
<script src="${pageContext.request.contextPath}/js/quiz.js" defer></script>
