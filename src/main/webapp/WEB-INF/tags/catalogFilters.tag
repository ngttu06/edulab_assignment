<%@ tag body-content="empty" pageEncoding="UTF-8" %>
<%@ attribute name="action" required="true" type="java.lang.String" %>
<%@ attribute name="filter" required="false" type="vn.edu.khtn8.content.dto.CatalogFilter" %>
<%@ attribute name="subjects" required="true" type="java.util.List" %>
<%@ attribute name="grades" required="true" type="java.util.List" %>
<%@ attribute name="courses" required="false" type="java.util.List" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<form class="catalog-filters" method="get" action="${pageContext.request.contextPath}${action}" aria-label="Bộ lọc nội dung">
    <div class="filter-search"><label for="filterQuery">Từ khóa</label><input class="form-control" type="search" id="filterQuery" name="q" maxlength="100" value="<c:out value='${filter.q()}'/>" placeholder="Bạn muốn học gì?"></div>
    <div><label for="filterGrade">Khối lớp</label><select class="form-select" id="filterGrade" name="grade"><option value="">Tất cả khối</option><c:forEach var="grade" items="${grades}"><option value="${grade}" ${filter.grade() == grade ? 'selected' : ''}>Lớp ${grade}</option></c:forEach></select></div>
    <div><label for="filterSubject">Môn học</label><select class="form-select" id="filterSubject" name="subjectId"><option value="">Tất cả môn</option><c:forEach var="subject" items="${subjects}"><option value="${subject.id}" ${filter.subjectId() == subject.id ? 'selected' : ''}><c:out value="${subject.name}"/> · ${subject.grade_level}</option></c:forEach></select></div>
    <c:if test="${not empty courses}"><div class="filter-course"><label for="filterCourse">Khóa học</label><select class="form-select" id="filterCourse" name="courseId"><option value="">Tất cả khóa học</option><c:forEach var="course" items="${courses}"><option value="${course.id}" ${filter.courseId() == course.id ? 'selected' : ''}><c:out value="${course.title}"/></option></c:forEach></select></div></c:if>
    <div class="filter-actions"><button type="submit" class="btn btn-primary">Lọc kết quả</button><a href="${pageContext.request.contextPath}${action}" class="btn btn-outline-secondary">Xóa lọc</a></div>
</form>