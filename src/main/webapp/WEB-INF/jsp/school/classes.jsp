<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../teacher/nav.jspf" %>
<h1 class="h3">Lớp học</h1><p class="text-muted">Chọn lớp để quản lý học sinh, giao bài và xem kết quả.</p>
<c:if test="${pageContext.request.isUserInRole('ADMIN')}"><a class="btn btn-primary mb-3" href="${pageContext.request.contextPath}/teacher/classes/new">Tạo lớp khối 8</a></c:if>
<ul class="list-group my-3"><c:forEach var="classroom" items="${classes}"><li class="list-group-item"><a href="${pageContext.request.contextPath}/teacher/classes/${classroom.id}"><c:out value="${classroom.name}"/></a> · <c:out value="${classroom.academic_year}"/></li></c:forEach></ul>
<c:if test="${empty classes}"><div class="empty-state"><h2>Chưa có lớp được phân công</h2><p>Lớp học sẽ xuất hiện tại đây sau khi quản trị viên phân công.</p></div></c:if>
<c:if test="${not empty archivedClasses}"><h2 class="h5 mt-4">Lớp đã lưu trữ</h2><ul class="list-group"><c:forEach var="classroom" items="${archivedClasses}"><li class="list-group-item"><a href="${pageContext.request.contextPath}/teacher/classes/${classroom.id}"><c:out value="${classroom.name}"/></a> · <c:out value="${classroom.academic_year}"/></li></c:forEach></ul></c:if>
