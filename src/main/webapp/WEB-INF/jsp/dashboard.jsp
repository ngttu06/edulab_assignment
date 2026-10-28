<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<section class="learner-hero"><div class="hero-content"><span class="eyebrow">GÓC HỌC TẬP CỦA BẠN</span>
<h1>Chào <c:out value="${report.student.full_name}"/></h1>
<p>Chọn một bài học và tiếp tục tiến bộ hôm nay. Các số liệu bên dưới lấy từ tiến độ bạn đã lưu, không phải điểm đánh giá của giáo viên.</p>
<nav class="hero-actions" aria-label="Hoạt động học tập"><a class="btn btn-primary" href="${pageContext.request.contextPath}/roadmap">Lộ trình bài học</a><a class="btn btn-outline-light" href="${pageContext.request.contextPath}/quizzes">Luyện quiz</a><a class="btn btn-outline-light" href="${pageContext.request.contextPath}/family">Liên kết phụ huynh</a></nav>
</div><div class="hero-stats"><div class="stat-chip"><span class="stat-label">Bài học hoàn thành</span><strong class="stat-value"><c:out value="${report.summary.completed_lessons}"/> / <c:out value="${report.summary.total_lessons}"/></strong></div><div class="stat-chip"><span class="stat-label">Tiến độ trung bình</span><strong class="stat-value"><c:out value="${report.summary.completion_percent}"/>%</strong></div></div></section>
<jsp:include page="/WEB-INF/jsp/school/assignments.jsp"/>
<section class="my-4"><div class="section-heading"><h2 class="section-title">Khóa học của bạn</h2><a href="${pageContext.request.contextPath}/courses">Xem tất cả</a></div><div class="course-grid"><c:forEach var="course" items="${courses}" end="3"><ui:courseCard course="${course}"/></c:forEach></div></section>
<section class="card"><div class="card-body"><h2 class="h5">Kết quả quiz gần nhất</h2>
<div class="table-responsive"><table class="table"><thead><tr><th>Quiz</th><th>Điểm /10</th><th>Thời gian nộp</th></tr></thead><tbody><c:forEach var="attempt" items="${report.attempts}"><tr><td><a href="${pageContext.request.contextPath}/quizzes/attempts/${attempt.id}"><c:out value="${attempt.title}"/></a></td><td><c:out value="${attempt.score}"/></td><td><c:out value="${attempt.submitted_at}"/></td></tr></c:forEach></tbody></table></div>
<c:if test="${empty report.attempts}"><p class="text-muted mb-0">Bạn chưa nộp bài quiz nào.</p></c:if></div></section>
