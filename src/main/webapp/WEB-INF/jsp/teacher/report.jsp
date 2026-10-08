<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="class-nav.jspf" %>
<h1 class="h3 mt-3"><c:out value="${report.student.full_name}"/></h1>
<p class="text-muted">Tiến độ và điểm của bài học/quiz thuộc lớp đang xem. Điểm quiz được tính theo thang 10; dấu — nghĩa là chưa có kết quả.</p>
<c:if test="${section == 'progress'}">
<h2 class="h5">Tiến độ bài học · ${report.completed} bài hoàn thành</h2>
<div class="table-responsive mb-4"><table class="table"><thead><tr><th>Bài học</th><th>Tiến độ</th><th>Trạng thái</th></tr></thead><tbody><c:forEach var="lesson" items="${report.lessons}"><tr><td><c:out value="${lesson.title}"/></td><td>${lesson.completion_percent}%</td><td>${lesson.status == 'COMPLETED' ? 'Hoàn thành' : lesson.status == 'IN_PROGRESS' ? 'Đang học' : 'Chưa học'}</td></tr></c:forEach></tbody></table><c:if test="${empty report.lessons}"><p>Chưa có bài học riêng của lớp.</p></c:if></div>
</c:if>
<c:if test="${section == 'results'}">
<h2 class="h5">Điểm theo từng quiz</h2>
<div class="table-responsive mb-4"><table class="table"><thead><tr><th>Quiz</th><th>Trạng thái</th><th>Lượt làm / đã chấm</th><th>Cao nhất /10</th><th>Gần nhất /10</th></tr></thead><tbody><c:forEach var="quiz" items="${report.quizzes}"><tr><td><c:out value="${quiz.title}"/></td><td>${quiz.status == 'NOT_STARTED' ? 'Chưa làm' : quiz.status == 'IN_PROGRESS' ? 'Đang làm' : 'Đã chấm'}</td><td>${quiz.attempts} / ${quiz.graded_count}</td><td><c:out value="${quiz.best_score}" default="—"/></td><td><c:out value="${quiz.latest_score}" default="—"/></td></tr></c:forEach></tbody></table><c:if test="${empty report.quizzes}"><p>Chưa có quiz thuộc lớp.</p></c:if></div>
<h2 class="h5">200 lượt làm gần nhất trong lớp</h2>
<div class="table-responsive mb-4"><table class="table"><thead><tr><th>Quiz</th><th>Lượt</th><th>Điểm /10</th><th>Nộp lúc (UTC)</th><th>Trạng thái</th></tr></thead><tbody><c:forEach var="attempt" items="${report.attempts}"><tr><td><c:out value="${attempt.title}"/></td><td>${attempt.attempt_number}</td><td><c:out value="${attempt.score}" default="—"/></td><td><c:out value="${attempt.submitted_at}" default="—"/></td><td>${attempt.status == 'IN_PROGRESS' ? 'Đang làm' : 'Đã chấm'}<c:if test="${attempt.expired}"> · Hết giờ</c:if></td></tr></c:forEach></tbody></table></div>
</c:if>
