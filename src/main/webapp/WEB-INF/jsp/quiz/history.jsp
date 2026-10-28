<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<a class="quiz-back" href="${pageContext.request.contextPath}/quizzes">← Quiz &amp; kiểm tra</a>
<header class="quiz-hub-head">
    <div class="quiz-hub-copy">
        <span class="eyebrow">BÀI ĐÃ LÀM</span>
        <h1 class="h3">Lịch sử làm bài</h1>
        <p class="text-muted">200 lượt gần nhất. Xem lại bài đã nộp hoặc tiếp tục lượt đang làm. Bài kiểm tra mở đáp án theo lịch giáo viên.</p>
    </div>
</header>
<div class="table-responsive">
    <table class="table quiz-history-table">
        <caption class="visually-hidden">Danh sách lượt làm bài gần nhất</caption>
        <thead><tr><th scope="col">Bài</th><th scope="col">Lượt</th><th scope="col">Trạng thái</th><th scope="col">Điểm /10</th><th scope="col">Nộp lúc (UTC)</th><th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
        <tbody>
        <c:forEach var="a" items="${attempts}">
            <tr>
                <td><span class="quiz-history-title"><c:out value="${a.title}"/></span><span class="quiz-badge ${a.mode == 'TEST' ? 'is-test' : 'is-practice'}">${a.mode == 'TEST' ? 'Kiểm tra' : 'Luyện tập'}</span></td>
                <td>${a.attempt_number}</td>
                <td><span class="quiz-status ${a.status == 'GRADED' ? 'is-graded' : 'is-progress'}">${a.status == 'GRADED' ? 'Đã chấm' : 'Đang làm'}</span></td>
                <td><c:out value="${a.score}" default="—"/></td>
                <td><c:out value="${a.submitted_at}" default="—"/></td>
                <td><a class="quiz-history-action" href="${pageContext.request.contextPath}/quizzes/attempts/${a.id}">${a.status == 'GRADED' ? 'Xem lại bài' : 'Tiếp tục'}<span aria-hidden="true">→</span></a></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>
<c:if test="${empty attempts}"><p class="quiz-empty">Bạn chưa có lượt làm bài nào. <a href="${pageContext.request.contextPath}/quizzes">Chọn một quiz để bắt đầu</a>.</p></c:if>
