<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<span class="eyebrow">MỖI NGÀY MỘT KHÁM PHÁ</span><h1 class="h3 mb-3">Thư viện bài học</h1>
<p class="text-muted">Chọn môn học hoặc khóa học để bắt đầu. Tiến độ được lưu riêng cho từng học sinh.</p>
<ui:catalogFilters action="/lessons" filter="${filter}" subjects="${subjects}" grades="${grades}" courses="${courseOptions}"/>
<div class="row g-3">
<c:forEach var="lesson" items="${lessons}">
<div class="col-md-6 col-lg-4"><article class="card h-100"><div class="card-body">
<p class="small text-primary"><c:out value="${lesson.subjectName()}"/> · Lớp ${lesson.gradeLevel()}<br><c:out value="${lesson.topicName()}"/></p>
<h2 class="h5"><c:out value="${lesson.title()}"/></h2>
<p><c:out value="${lesson.summary()}"/></p>
<p class="small text-muted"><c:out value="${lesson.durationMinutes()}"/> phút · Tiến độ: <c:out value="${lesson.completionPercent()}"/>%</p><progress class="mb-3" aria-label="Tiến độ bài học" value="${lesson.completionPercent()}" max="100">${lesson.completionPercent()}%</progress>
<a class="btn btn-primary" href="${pageContext.request.contextPath}/lessons/${lesson.id()}">Mở bài học</a>
</div></article></div>
</c:forEach>
<c:if test="${empty lessons}"><div class="empty-state"><ui:icon name="book"/><h2>Chưa có bài học phù hợp</h2><p>Thử thay đổi bộ lọc hoặc quay lại khi nội dung được xuất bản.</p></div></c:if>
</div>
