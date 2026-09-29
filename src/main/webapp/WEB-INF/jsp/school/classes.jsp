<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<h1 class="h3">Lớp học được phân công</h1><p id="schoolNotice" role="status"></p>
<ul class="list-group my-3"><c:forEach var="classroom" items="${classes}"><li class="list-group-item"><a href="${pageContext.request.contextPath}/teacher/classes/${classroom.id}"><c:out value="${classroom.name}"/></a> · <c:out value="${classroom.academic_year}"/></li></c:forEach></ul>
<c:if test="${pageContext.request.isUserInRole('ADMIN')}">
<h2 class="h5">Tạo lớp khối 8</h2><form id="createClass" class="mb-4"><label for="yearId">Năm học</label><select id="yearId" class="form-select" required><c:forEach var="year" items="${years}"><option value="${year.id}"><c:out value="${year.name}"/></option></c:forEach></select><label for="className">Tên lớp</label><input id="className" class="form-control mb-2" required maxlength="80"><button class="btn btn-primary">Tạo lớp</button></form>
<h2 class="h5">Phân công / ghi danh</h2><form id="assignMember"><label for="classId">Lớp</label><select id="classId" class="form-select" required><c:forEach var="classroom" items="${classes}"><option value="${classroom.id}"><c:out value="${classroom.name}"/></option></c:forEach></select><label for="memberId">Thành viên (tối đa 500 tài khoản đang hoạt động)</label><select id="memberId" class="form-select mb-2" required><c:forEach var="member" items="${members}"><option value="${member.id}" data-role="${member.role}"><c:out value="${member.full_name}"/> — <c:out value="${member.username}"/> (<c:out value="${member.role}"/>)</option></c:forEach></select><button class="btn btn-primary">Lưu phân công</button></form>
</c:if>
<script src="${pageContext.request.contextPath}/js/school.js" defer></script>
