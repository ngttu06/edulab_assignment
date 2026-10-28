<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<header class="quiz-hub-head">
    <div class="quiz-hub-copy">
        <span class="eyebrow">HIỂU THÊM SAU MỖI CÂU HỎI</span>
        <h1 class="h3">Luyện tập &amp; kiểm tra</h1>
        <p class="text-muted">Điểm được chấm trên server theo thang 10. Hết giờ chỉ chấm các câu trả lời đã lưu trước hạn. Đáp án bài luyện hiện ngay sau khi nộp; bài kiểm tra mở theo lịch công bố của giáo viên.</p>
    </div>
    <c:if test="${pageContext.request.isUserInRole('STUDENT')}">
        <a class="btn btn-outline-primary quiz-hub-link" href="${pageContext.request.contextPath}/quizzes/history"><ui:icon name="chart"/>Lịch sử &amp; xem lại bài làm</a>
    </c:if>
</header>
<p id="quizNotice" role="alert"></p>
<ui:catalogFilters action="/quizzes" filter="${filter}" subjects="${subjects}" grades="${grades}" courses="${courseOptions}"/>
<div class="quiz-list-grid">
    <c:forEach var="quiz" items="${quizzes}">
        <article class="quiz-card">
            <div class="quiz-card-top">
                <span class="quiz-badge ${quiz.mode() == 'TEST' ? 'is-test' : 'is-practice'}">${quiz.mode() == 'TEST' ? 'Bài kiểm tra' : 'Luyện tập'}</span>
                <span class="quiz-card-clock"><ui:icon name="clock"/>${quiz.durationMinutes()} phút</span>
            </div>
            <h2 class="quiz-card-title"><c:out value="${quiz.title()}"/></h2>
            <dl class="quiz-card-meta">
                <div><dt>Số câu</dt><dd>${quiz.questionCount()}</dd></div>
                <div><dt>Lượt tối đa</dt><dd>${quiz.maxAttempts()}</dd></div>
                <div><dt>Thang điểm</dt><dd>10</dd></div>
            </dl>
            <c:if test="${pageContext.request.isUserInRole('STUDENT')}">
                <button class="btn btn-primary startQuiz quiz-card-cta" data-id="${quiz.id()}" data-mode="${quiz.mode()}">Bắt đầu / tiếp tục<span aria-hidden="true">→</span></button>
            </c:if>
        </article>
    </c:forEach>
</div>
<c:if test="${empty quizzes}"><p class="quiz-empty">Chưa có quiz được xuất bản.</p></c:if>
<script src="${pageContext.request.contextPath}/js/quiz.js" defer></script>
