<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<a href="${pageContext.request.contextPath}/quizzes">← Danh sách quiz</a>
<h1 class="h3 mt-3"><c:out value="${attempt.title()}"/></h1>
<p>Hạn nộp: <time id="deadline" datetime="${attempt.deadline()}"><c:out value="${attempt.deadline()}"/></time></p>
<c:if test="${attempt.status() == 'GRADED'}"><p class="alert alert-info">Điểm: <c:out value="${attempt.score()}"/>/10 <c:if test="${attempt.expired()}"> — Nộp sau khi hết giờ.</c:if></p></c:if>
<p id="quizNotice" role="alert"></p>
<form id="quizForm" data-id="${attempt.id()}" data-status="${attempt.status()}">
<c:forEach var="question" items="${attempt.questions()}" varStatus="index">
<fieldset class="card mb-3"><div class="card-body"><legend class="h5"><c:out value="${index.count}"/>. <c:out value="${question.text()}"/></legend>
<c:forEach var="option" items="${question.options()}">
<div class="form-check"><input type="radio" class="form-check-input" id="option-${option.id()}" name="${question.id()}" value="${option.id()}" ${attempt.status() == 'GRADED' ? 'disabled' : ''} ${question.selectedOption() == option.id() ? 'checked' : ''}>
<label class="form-check-label" for="option-${option.id()}"><c:out value="${option.text()}"/><c:if test="${option.correct() == true}"> — Đáp án đúng</c:if></label></div>
</c:forEach>
<c:if test="${attempt.status() == 'GRADED'}"><p class="mt-3"><c:out value="${question.explanation()}"/></p></c:if>
</div></fieldset></c:forEach>
<c:if test="${attempt.status() == 'IN_PROGRESS'}"><button type="submit" class="btn btn-success">Nộp bài</button></c:if>
</form>
<script src="${pageContext.request.contextPath}/js/quiz.js" defer></script>
