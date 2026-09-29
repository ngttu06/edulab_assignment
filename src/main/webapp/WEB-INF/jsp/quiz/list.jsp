<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<h1 class="h3">Quiz luyện tập</h1>
<p>Điểm được chấm trên thang 10. Hết giờ mà chưa nộp sẽ được 0 điểm. Đáp án chỉ hiện sau khi nộp.</p>
<p id="quizNotice" role="alert"></p>
<div class="row g-3"><c:forEach var="quiz" items="${quizzes}"><div class="col-md-6"><article class="card"><div class="card-body">
<h2 class="h5"><c:out value="${quiz.title()}"/></h2>
<p><c:out value="${quiz.questionCount()}"/> câu · <c:out value="${quiz.durationMinutes()}"/> phút · Tối đa <c:out value="${quiz.maxAttempts()}"/> lượt</p>
<c:if test="${pageContext.request.isUserInRole('STUDENT')}"><button class="btn btn-primary startQuiz" data-id="${quiz.id()}">Bắt đầu / tiếp tục</button></c:if>
</div></article></div></c:forEach></div>
<c:if test="${empty quizzes}"><p>Chưa có quiz được xuất bản.</p></c:if>
<script src="${pageContext.request.contextPath}/js/quiz.js" defer></script>
