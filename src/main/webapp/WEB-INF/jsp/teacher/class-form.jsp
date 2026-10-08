<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="nav.jspf" %>
<a href="${pageContext.request.contextPath}/teacher/classes">← Các lớp</a><h1 class="h3 mt-3">${empty classId ? 'Tạo lớp khối 8' : 'Chỉnh sửa lớp'}</h1>
<p id="workspaceNotice" role="status" aria-live="polite"></p>
<form data-workspace-form="class" data-api="/api/v1/admin/classes${empty classId ? '' : '/'}${classId}" data-method="${empty classId ? 'POST' : 'PUT'}" data-return="/teacher/classes">
<label for="yearId" class="form-label">Năm học</label><select id="yearId" name="yearId" class="form-select mb-3" required><c:forEach var="year" items="${years}"><option value="${year.id}" ${year.id == classroom.academic_year_id ? 'selected' : ''}><c:out value="${year.name}"/></option></c:forEach></select>
<label for="className" class="form-label">Tên lớp</label><input id="className" name="name" class="form-control mb-3" maxlength="80" required value="<c:out value='${classroom.name}'/>">
<button class="btn btn-primary">Lưu lớp</button><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/teacher/classes">Hủy</a></form>
<script src="${pageContext.request.contextPath}/js/teacher-workspace.js" defer></script>