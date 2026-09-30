<%@ tag body-content="empty" pageEncoding="UTF-8" %>
<%@ attribute name="course" required="true" type="java.util.Map" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<article class="course-card">
    <a class="course-cover theme-<c:out value='${course.theme}'/>" href="${pageContext.request.contextPath}/courses/${course.id}" tabindex="-1" aria-hidden="true"><span class="cover-brand">EDULAB</span><span class="cover-subject"><c:out value="${course.subject_name}"/></span><strong class="cover-grade">${course.grade_level}</strong><span class="cover-caption">NẮM VỮNG NỀN TẢNG</span><ui:icon name="book"/></a>
    <div class="course-info"><span class="course-type"><c:choose><c:when test="${course.kind == 'REVISION'}">Ôn tập</c:when><c:when test="${course.kind == 'TOPIC'}">Chuyên đề</c:when><c:otherwise>Nền tảng</c:otherwise></c:choose> · Lớp ${course.grade_level}</span><h3><a href="${pageContext.request.contextPath}/courses/${course.id}"><c:out value="${course.title}"/></a></h3>
    <c:if test="${not empty course.teacher_name}"><p class="course-teacher"><ui:icon name="user"/><c:out value="${course.teacher_name}"/></p></c:if>
    <p class="course-meta"><span><ui:icon name="book"/>${course.lesson_count} bài học</span><span><ui:icon name="clock"/>${course.duration_minutes} phút</span></p>
    <c:if test="${pageContext.request.isUserInRole('STUDENT')}"><label class="course-progress">Đã hoàn thành ${course.completed_lessons}/${course.lesson_count} bài<progress value="${course.completion_percent}" max="100">${course.completion_percent}%</progress></label></c:if>
    <a class="course-action" href="${pageContext.request.contextPath}/courses/${course.id}">Xem khóa học <span aria-hidden="true">→</span><span class="visually-hidden">: <c:out value="${course.title}"/></span></a></div>
</article>