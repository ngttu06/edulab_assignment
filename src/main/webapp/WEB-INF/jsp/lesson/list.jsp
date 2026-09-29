<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<h1 class="h3 mb-3">Lộ trình bài học KHTN 8</h1>
<p class="text-muted">Học theo thứ tự chủ đề bên dưới. Tiến độ được lưu riêng cho từng học sinh.</p>
<div class="row g-3">
<c:forEach var="lesson" items="${lessons}">
<div class="col-md-6 col-lg-4"><article class="card h-100"><div class="card-body">
<p class="small text-primary"><c:out value="${lesson.topicName()}"/></p>
<h2 class="h5"><c:out value="${lesson.title()}"/></h2>
<p><c:out value="${lesson.summary()}"/></p>
<p class="small text-muted"><c:out value="${lesson.durationMinutes()}"/> phút · Tiến độ: <c:out value="${lesson.completionPercent()}"/>%</p>
<a class="btn btn-primary" href="${pageContext.request.contextPath}/lessons/${lesson.id()}">Mở bài học</a>
</div></article></div>
</c:forEach>
<c:if test="${empty lessons}"><p>Chưa có bài học được xuất bản.</p></c:if>
</div>
