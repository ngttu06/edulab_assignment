<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="class-nav.jspf" %>
<h1 class="h3">${empty item ? 'Giao quiz cho lớp' : 'Sửa hạn nộp'}</h1><p>Chỉ tính lượt nộp từ lúc giao bài đến hạn. Để đổi đề, hãy hủy bài giao cũ và giao mới.</p>
<p id="workspaceNotice" role="status" aria-live="polite"></p>
<form data-workspace-form="assignment" data-api="/api/v1/teacher/classes/${classId}/assignments${empty item ? '' : '/'}${item.id}" data-method="${empty item ? 'POST' : 'PUT'}" data-return="/teacher/classes/${classId}/assignments">
<c:choose><c:when test="${empty item}"><label class="form-label" for="assignedQuiz">Quiz đã xuất bản</label><select id="assignedQuiz" name="quizId" class="form-select mb-3" required><option value="">Chọn quiz</option><c:forEach var="quiz" items="${quizzes}"><option value="${quiz.id()}"><c:out value="${quiz.title()}"/></option></c:forEach></select></c:when><c:otherwise><p>Quiz: <c:out value="${item.title}"/></p></c:otherwise></c:choose>
<label class="form-label" for="assignmentDue">Hạn hoàn thành (giờ thiết bị)</label><input id="assignmentDue" name="dueAt" type="datetime-local" data-utc="<c:out value='${item.due_at}'/>" class="form-control mb-3" required>
<button class="btn btn-primary">Lưu bài giao</button><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/teacher/classes/${classId}/assignments">Hủy</a></form>
<script src="${pageContext.request.contextPath}/js/teacher-workspace.js" defer></script>