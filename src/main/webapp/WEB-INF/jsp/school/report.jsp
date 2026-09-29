<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<h1 class="h3">Kết quả học tập: <c:out value="${report.student.full_name}"/></h1>
<p>Đã hoàn thành <c:out value="${report.summary.completed_lessons}"/>/<c:out value="${report.summary.total_lessons}"/> bài; tiến độ trung bình <c:out value="${report.summary.completion_percent}"/>%.</p>
<h2 class="h5">Tiến độ từng bài</h2><ul class="list-group mb-4"><c:forEach var="lesson" items="${report.lessons}"><li class="list-group-item"><c:out value="${lesson.title()}"/> — <c:out value="${lesson.completionPercent()}"/>%</li></c:forEach></ul>
<h2 class="h5">100 kết quả quiz gần nhất</h2><div class="table-responsive"><table class="table"><thead><tr><th>Quiz</th><th>Điểm /10</th><th>Nộp lúc</th><th>Ghi chú</th></tr></thead><tbody><c:forEach var="attempt" items="${report.attempts}"><tr><td><c:out value="${attempt.title}"/></td><td><c:out value="${attempt.score}"/></td><td><c:out value="${attempt.submitted_at}"/></td><td><c:if test="${attempt.expired}">Hết giờ</c:if></td></tr></c:forEach></tbody></table></div>
