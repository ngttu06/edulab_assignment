<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<a href="${pageContext.request.contextPath}/teacher/classes">← Các lớp</a><h1 class="h3 mt-3">Học sinh trong lớp</h1>
<ul class="list-group"><c:forEach var="student" items="${students}"><li class="list-group-item"><a href="${pageContext.request.contextPath}/school/students/${student.id}"><c:out value="${student.full_name}"/></a></li></c:forEach></ul>
<c:if test="${empty students}"><p>Chưa có học sinh đang theo học.</p></c:if>
