<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<span class="eyebrow">HIỂU THÊM SAU MỖI CÂU HỎI</span><h1 class="h3">Luyện tập &amp; kiểm tra</h1>
<p class="text-muted">Điểm được chấm trên server theo thang 10. Hết giờ chỉ chấm các câu trả lời đã lưu trước hạn. Đáp án bài luyện hiện sau khi nộp; bài kiểm tra theo lịch công bố.</p>
<p id="quizNotice" role="alert"></p>
<c:if test="${pageContext.request.isUserInRole('STUDENT')}"><p><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/quizzes/history">Lịch sử &amp; xem lại bài làm</a></p></c:if>
<ui:catalogFilters action="/quizzes" filter="${filter}" subjects="${subjects}" grades="${grades}" courses="${courseOptions}"/>
<div class="row g-3"><c:forEach var="quiz" items="${quizzes}"><div class="col-md-6"><article class="card"><div class="card-body">
<h2 class="h5"><c:out value="${quiz.title()}"/></h2>
<span class="badge text-bg-light mb-3">${quiz.mode()=='TEST'?'Bài kiểm tra':'Luyện tập'}</span>
<p><c:out value="${quiz.questionCount()}"/> câu · <c:out value="${quiz.durationMinutes()}"/> phút · Tối đa <c:out value="${quiz.maxAttempts()}"/> lượt</p>
<c:if test="${pageContext.request.isUserInRole('STUDENT')}"><button class="btn btn-primary startQuiz" data-id="${quiz.id()}" data-mode="${quiz.mode()}">Bắt đầu / tiếp tục</button></c:if>
</div></article></div></c:forEach></div>
<c:if test="${empty quizzes}"><p>Chưa có quiz được xuất bản.</p></c:if>
<script src="${pageContext.request.contextPath}/js/quiz.js" defer></script>
