<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<nav class="breadcrumb-line" aria-label="Đường dẫn"><a href="${pageContext.request.contextPath}/">Trang chủ</a><span aria-hidden="true">/</span><span aria-current="page">Khóa học</span></nav>
<section class="panel catalog-panel"><div class="page-heading"><span class="eyebrow">KHÁM PHÁ KIẾN THỨC</span><h1>Khóa học EduLab</h1><p>Chọn môn học, xây nền tảng và tiến bộ theo nhịp của bạn.</p></div><ui:catalogFilters action="/courses" filter="${filter}" subjects="${subjects}" grades="${grades}"/>
    <div class="section-heading"><h2 class="section-title">Khóa học phù hợp</h2><span class="text-muted small">${courses.size()} kết quả · tối đa 60</span></div>
    <div class="course-grid"><c:forEach var="course" items="${courses}"><ui:courseCard course="${course}"/></c:forEach></div>
    <c:if test="${empty courses}"><div class="empty-state"><ui:icon name="search"/><h2>Chưa tìm thấy khóa học</h2><p>Thử một từ khóa khác hoặc bỏ bớt bộ lọc môn và khối lớp.</p><a class="btn btn-primary" href="${pageContext.request.contextPath}/courses">Xem tất cả khóa học</a></div></c:if>
</section>