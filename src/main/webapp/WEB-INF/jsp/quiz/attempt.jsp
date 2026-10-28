<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:choose>
    <c:when test="${teacherReview}"><a class="quiz-back" href="${pageContext.request.contextPath}/teacher/quizzes/${student.quiz_id}/report">← Báo cáo quiz</a></c:when>
    <c:otherwise><a class="quiz-back" href="${pageContext.request.contextPath}/quizzes/history">← Lịch sử làm bài</a></c:otherwise>
</c:choose>
<header class="assessment-head">
    <div class="assessment-head-copy">
        <span class="quiz-badge ${attempt.mode() == 'TEST' ? 'is-test' : 'is-practice'}">${attempt.mode() == 'TEST' ? 'Bài kiểm tra' : 'Luyện tập'}</span>
        <h1 class="h3 assessment-title"><c:out value="${attempt.title()}"/></h1>
        <p class="assessment-head-meta">
            <c:choose>
                <c:when test="${teacherReview}">Học sinh <strong><c:out value="${student.full_name}"/></strong> · <c:out value="${student.username}"/> · Bắt đầu (UTC): <c:out value="${student.started_at}"/> · Nộp (UTC): <c:out value="${student.submitted_at}" default="Chưa nộp"/></c:when>
                <c:otherwise>Hạn nộp: <time id="deadline" datetime="${attempt.deadline()}" data-deadline><c:out value="${attempt.deadline()}"/></time> · Bài làm đã khóa, chỉ xem lại.</c:otherwise>
            </c:choose>
        </p>
    </div>
</header>
<c:if test="${attempt.status() == 'GRADED'}">
<section class="assessment-score-card" aria-labelledby="resultTitle">
    <div class="assessment-score-main">
        <p class="assessment-score-label" id="resultTitle">Kết quả</p>
        <p class="assessment-score-value"><strong><c:out value="${attempt.score()}"/></strong><span>/10</span></p>
        <c:if test="${attempt.expired()}"><p class="assessment-score-note">Hết giờ — chấm theo câu trả lời đã lưu trước hạn.</p></c:if>
    </div>
    <c:choose>
        <c:when test="${attempt.answersVisible()}">
            <dl class="assessment-stats">
                <div class="is-correct"><dt>Trả lời đúng</dt><dd>${attempt.correctCount()}</dd></div>
                <div class="is-wrong"><dt>Trả lời sai</dt><dd>${attempt.wrongCount()}</dd></div>
                <div class="is-empty"><dt>Bỏ trống</dt><dd>${attempt.unansweredCount()}</dd></div>
                <div><dt>Tổng số câu</dt><dd>${attempt.questions().size()}</dd></div>
            </dl>
        </c:when>
        <c:otherwise>
            <p class="assessment-reveal-note">Đáp án, câu sai và giải thích sẽ mở theo lịch công bố:
                <c:choose>
                    <c:when test="${empty attempt.revealAfter()}">theo lịch của giáo viên.</c:when>
                    <c:otherwise><time datetime="${attempt.revealAfter()}" data-deadline><c:out value="${attempt.revealAfter()}"/></time>.</c:otherwise>
                </c:choose>
            </p>
        </c:otherwise>
    </c:choose>
</section>
</c:if>
<c:if test="${not teacherReview and attempt.status() == 'GRADED'}">
<section class="assessment-ai-card" id="aiReview" data-id="${attempt.id()}">
    <div class="assessment-ai-head">
        <h2 class="h5"><ui:icon name="spark"/>Nhận xét AI</h2>
        <button type="button" id="retryAiReview" class="btn btn-outline-primary btn-sm">Tạo / tải lại nhận xét</button>
    </div>
    <p class="small text-muted">AI chỉ hỗ trợ học tập, không thay đổi điểm. ${attempt.answersVisible() ? 'Nhận xét dựa trên kết quả và nội dung bài.' : 'Trước giờ công bố, chỉ nhận xét tổng quan theo điểm, không phân tích đáp án.'}</p>
    <p id="aiReviewText" class="assessment-ai-text" role="status" aria-live="polite">Đang chuẩn bị nhận xét…</p>
</section>
</c:if>
<c:if test="${attempt.answersVisible()}">
<div class="assessment-toolbar">
    <div class="assessment-toolbar-field">
        <label for="questionFilter" class="form-label">Xem lại câu hỏi</label>
        <select id="questionFilter" class="form-select">
            <option value="all">Tất cả câu hỏi</option>
            <option value="WRONG">Câu sai</option>
            <option value="UNANSWERED">Bỏ trống</option>
            <option value="CORRECT">Câu đúng</option>
        </select>
    </div>
    <p class="assessment-toolbar-note">${attempt.correctCount()} đúng · ${attempt.wrongCount()} sai · ${attempt.unansweredCount()} bỏ trống / ${attempt.questions().size()} câu</p>
</div>
<p id="questionFilterEmpty" class="assessment-empty" hidden role="status">Không có câu hỏi thuộc bộ lọc này.</p>
</c:if>
<p id="quizNotice" role="alert"></p>
<form id="quizForm" data-id="${attempt.id()}" data-status="${attempt.status()}" data-mode="${attempt.mode()}" data-teacher="${teacherReview ? 'true' : 'false'}">
    <c:forEach var="question" items="${attempt.questions()}" varStatus="index">
        <fieldset class="card mb-3 assessment-question" data-outcome="${question.outcome()}">
            <legend class="assessment-question-legend"><span class="assessment-question-number">${index.count}.</span> <c:out value="${question.text()}"/></legend>
            <div class="card-body">
                <c:if test="${attempt.answersVisible()}">
                    <p class="assessment-outcome ${question.outcome() == 'CORRECT' ? 'is-correct' : question.outcome() == 'WRONG' ? 'is-wrong' : 'is-empty'}">
                        <c:choose>
                            <c:when test="${question.outcome() == 'CORRECT'}">✓ Trả lời đúng</c:when>
                            <c:when test="${question.outcome() == 'WRONG'}">✗ Trả lời sai</c:when>
                            <c:otherwise>— Chưa trả lời</c:otherwise>
                        </c:choose>
                    </p>
                </c:if>
                <div class="assessment-options">
                    <c:forEach var="option" items="${question.options()}">
                        <c:set var="correctOption" value="${option.correct() == true}"/>
                        <c:set var="selectedOption" value="${question.selectedOption() == option.id()}"/>
                        <div class="assessment-option form-check ${correctOption ? 'is-correct' : ''} ${selectedOption ? 'is-selected' : ''} ${selectedOption and not correctOption and attempt.answersVisible() ? 'is-wrong' : ''}">
                            <input class="form-check-input" type="radio" id="option-${option.id()}" name="${question.id()}" value="${option.id()}" disabled ${selectedOption ? 'checked' : ''}>
                            <label class="form-check-label" for="option-${option.id()}">
                                <span class="assessment-option-text"><c:out value="${option.text()}"/></span>
                                <c:if test="${correctOption}"><span class="assessment-option-tag is-correct">Đáp án đúng</span></c:if>
                                <c:if test="${selectedOption}"><span class="assessment-option-tag is-selected">Bạn chọn</span></c:if>
                            </label>
                        </div>
                    </c:forEach>
                </div>
                <c:if test="${attempt.answersVisible()}">
                    <div class="assessment-explanation">
                        <p class="assessment-explanation-text"><c:out value="${question.explanation()}"/></p>
                        <c:if test="${not teacherReview}">
                            <button type="button" class="btn btn-outline-primary btn-sm askQuestionAi" data-question="${question.id()}" aria-controls="ai-question-${question.id()}">Hỏi AI giải thích câu này</button>
                            <p id="ai-question-${question.id()}" class="assessment-ai-text mt-2" role="status" aria-live="polite"></p>
                        </c:if>
                    </div>
                </c:if>
            </div>
        </fieldset>
    </c:forEach>
</form>
<c:if test="${teacherReview and attempt.mode() == 'TEST'}">
<details class="assessment-events">
    <summary>Số lần rời tab ghi nhận: ${events.size()}</summary>
    <p class="small">Dữ liệu hỗ trợ đánh giá, không phải bằng chứng chắc chắn về gian lận. Có thể thiếu sự kiện khi mất mạng/đóng ứng dụng.</p>
    <ol><c:forEach var="event" items="${events}"><li><c:out value="${event.received_at}"/> UTC</li></c:forEach></ol>
</details>
</c:if>
<c:if test="${teacherReview and not empty reviews}">
<section class="card mt-3"><div class="card-body">
    <h2 class="h5">Nhận xét AI đã tạo cho học sinh</h2>
    <c:forEach var="review" items="${reviews}"><h3 class="h6">${review.review_type == 'QUESTION' ? 'Giải thích từng câu' : 'Nhận xét tổng quan'} ${review.sample ? '· AI mẫu' : ''}</h3><p class="assessment-ai-text"><c:out value="${review.content}"/></p></c:forEach>
</div></section>
</c:if>
<script src="${pageContext.request.contextPath}/js/quiz.js" defer></script>
