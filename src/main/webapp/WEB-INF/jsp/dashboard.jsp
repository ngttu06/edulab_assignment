<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<section class="card dashboard-welcome mb-4"><div class="card-body p-4"><span class="eyebrow">GÓC HỌC TẬP CỦA BẠN</span>
<h1 class="h3">Chào <c:out value="${report.student.full_name}"/></h1>
<p>Chọn một bài học và tiếp tục tiến bộ hôm nay. Các số liệu bên dưới lấy từ tiến độ bạn đã lưu, không phải điểm đánh giá của giáo viên.</p>
<dl class="dashboard-stats"><div><dt>Bài học hoàn thành</dt><dd><c:out value="${report.summary.completed_lessons}"/> / <c:out value="${report.summary.total_lessons}"/></dd></div><div><dt>Tiến độ trung bình các bài đã xuất bản</dt><dd><c:out value="${report.summary.completion_percent}"/>%</dd></div></dl>
</div></section>
<nav aria-label="Hoạt động học tập" class="d-flex flex-wrap gap-2 mb-4"><a class="btn btn-primary" href="${pageContext.request.contextPath}/roadmap">Lộ trình bài học</a><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/quizzes">Luyện quiz</a><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/family">Liên kết phụ huynh</a><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/forum">Hỏi đáp</a></nav>
<jsp:include page="/WEB-INF/jsp/school/assignments.jsp"/>
<section class="my-4"><div class="section-heading"><h2 class="section-title">Khóa học của bạn</h2><a href="${pageContext.request.contextPath}/courses">Xem tất cả</a></div><div class="course-grid"><c:forEach var="course" items="${courses}" end="3"><ui:courseCard course="${course}"/></c:forEach></div></section>
<h2 class="h5">Kết quả quiz gần nhất (tối đa 100)</h2>
<div class="table-responsive"><table class="table"><thead><tr><th>Quiz</th><th>Điểm /10</th><th>Thời gian nộp</th></tr></thead><tbody><c:forEach var="attempt" items="${report.attempts}"><tr><td><a href="${pageContext.request.contextPath}/quizzes/attempts/${attempt.id}"><c:out value="${attempt.title}"/></a></td><td><c:out value="${attempt.score}"/></td><td><c:out value="${attempt.submitted_at}"/></td></tr></c:forEach></tbody></table></div>
<c:if test="${empty report.attempts}"><p>Bạn chưa nộp bài quiz nào.</p></c:if>
