<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="discovery" value="${contentPage == '/WEB-INF/jsp/home.jsp' or contentPage == '/WEB-INF/jsp/catalog/list.jsp'}"/>
<c:set var="community" value="${contentPage == '/WEB-INF/jsp/forum/list.jsp'}"/>
<c:set var="social" value="${contentPage == '/WEB-INF/jsp/community/list.jsp'}"/>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="app-context" content="${pageContext.request.contextPath}">
    <title><c:out value="${pageTitle}" default="EduLab · Học chủ động"/></title>
    <link href="${pageContext.request.contextPath}/css/vendor/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/fonts.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/edulab.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/ai-chat.css" rel="stylesheet">
    <c:if test="${contentPage == '/WEB-INF/jsp/lesson/video.jsp' or contentPage == '/WEB-INF/jsp/lesson/video-manage.jsp'}"><link href="${pageContext.request.contextPath}/css/lesson-video.css" rel="stylesheet"></c:if>
    <c:if test="${contentPage == '/WEB-INF/jsp/home.jsp' or community}"><link href="${pageContext.request.contextPath}/css/discovery.css" rel="stylesheet"></c:if>
    <c:if test="${social}"><link href="${pageContext.request.contextPath}/css/social-community.css" rel="stylesheet"></c:if>
    <link href="${pageContext.request.contextPath}/css/motion.css" rel="stylesheet">
    <c:if test="${contentPage.startsWith('/WEB-INF/jsp/teacher/') or contentPage == '/WEB-INF/jsp/school/classes.jsp'}"><link href="${pageContext.request.contextPath}/css/teacher.css" rel="stylesheet"></c:if>
    <script src="${pageContext.request.contextPath}/js/app.js" defer></script>
    <script src="${pageContext.request.contextPath}/js/motion.js" defer></script>
    <script src="${pageContext.request.contextPath}/js/ai-chat.js" defer></script>
</head>
<body>
<a class="skip-link" href="#mainContent">Đến nội dung chính</a>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp"/>
<div class="app-shell ${discovery or community or social ? 'discovery-shell' : 'workspace-shell'} ${community or social ? 'community-shell' : ''}">
    <aside id="sideNav" class="sidebar" aria-label="Điều hướng chính">
        <div class="sidebar-mobile-title"><span class="brand">EduLab</span><button id="menuClose" type="button" class="icon-button" aria-label="Đóng menu"><ui:icon name="close"/></button></div>
        <a class="sidebar-account" data-nav href="${pageContext.request.contextPath}${empty pageContext.request.userPrincipal ? '/login' : (pageContext.request.isUserInRole('TEACHER') ? '/teacher' : '/dashboard')}"><span class="nav-icon"><ui:icon name="user"/></span>${empty pageContext.request.userPrincipal ? 'Đăng nhập để bắt đầu' : (pageContext.request.isUserInRole('TEACHER') ? 'Không gian giáo viên' : 'Góc học tập của tôi')}</a>
        <nav class="side-links" aria-label="Học tập">
            <a data-nav href="${pageContext.request.contextPath}/"><span class="nav-icon blue"><ui:icon name="home"/></span>Trang chủ</a>
            <a data-nav href="${pageContext.request.contextPath}/courses"><span class="nav-icon coral"><ui:icon name="cap"/></span>Khóa học</a>
            <a data-nav href="${pageContext.request.contextPath}/lessons"><span class="nav-icon blue"><ui:icon name="book"/></span>Thư viện bài học</a>
            <a data-nav href="${pageContext.request.contextPath}/quizzes"><span class="nav-icon purple"><ui:icon name="quiz"/></span>Luyện tập &amp; kiểm tra</a>
            <a data-nav href="${pageContext.request.contextPath}/roadmap"><span class="nav-icon orange"><ui:icon name="route"/></span>Lộ trình học</a>
            <a data-nav href="${pageContext.request.contextPath}/community"><span class="nav-icon green"><ui:icon name="users"/></span>Cộng đồng</a>
            <a data-nav href="${pageContext.request.contextPath}/forum"><span class="nav-icon blue"><ui:icon name="chat"/></span>Hỏi đáp học tập</a>
        </nav>
        <div class="sidebar-section"><h2>Không gian của bạn</h2><nav class="side-links" aria-label="Không gian cá nhân">
            <c:choose><c:when test="${pageContext.request.isUserInRole('TEACHER')}"><a data-nav data-nav-exact href="${pageContext.request.contextPath}/teacher"><span class="nav-icon"><ui:icon name="chart"/></span>Tổng quan giảng dạy</a></c:when><c:otherwise><a data-nav href="${pageContext.request.contextPath}/dashboard"><span class="nav-icon"><ui:icon name="chart"/></span>Tổng quan học tập</a></c:otherwise></c:choose>
            <c:if test="${not empty pageContext.request.userPrincipal}"><a data-nav href="${pageContext.request.contextPath}/profile"><span class="nav-icon"><ui:icon name="user"/></span>Thông tin cá nhân</a></c:if>
            <c:if test="${pageContext.request.isUserInRole('TEACHER') or pageContext.request.isUserInRole('ADMIN')}"><a data-nav href="${pageContext.request.contextPath}/teacher/classes"><span class="nav-icon"><ui:icon name="users"/></span>Lớp học</a></c:if>
            <c:if test="${pageContext.request.isUserInRole('TEACHER') or pageContext.request.isUserInRole('ADMIN')}"><a data-nav href="${pageContext.request.contextPath}/teacher/lessons"><span class="nav-icon"><ui:icon name="book"/></span>Bài học của tôi</a><a data-nav href="${pageContext.request.contextPath}/teacher/quizzes"><span class="nav-icon"><ui:icon name="book"/></span>Quiz của tôi</a></c:if>
            <c:if test="${pageContext.request.isUserInRole('TEACHER') or pageContext.request.isUserInRole('ADMIN')}"><a data-nav href="${pageContext.request.contextPath}/teacher/videos"><span class="nav-icon"><ui:icon name="book"/></span>Video bài học</a></c:if>
            <c:if test="${pageContext.request.isUserInRole('TEACHER') or pageContext.request.isUserInRole('ADMIN')}"><a data-nav href="${pageContext.request.contextPath}/teacher/assignments"><span class="nav-icon"><ui:icon name="quiz"/></span>Bài đã giao</a></c:if>
            <c:if test="${pageContext.request.isUserInRole('STUDENT') or pageContext.request.isUserInRole('PARENT')}"><a data-nav href="${pageContext.request.contextPath}/family"><span class="nav-icon"><ui:icon name="users"/></span>Kết nối gia đình</a></c:if>
            <c:if test="${pageContext.request.isUserInRole('ADMIN')}"><a data-nav href="${pageContext.request.contextPath}/admin"><span class="nav-icon"><ui:icon name="settings"/></span>Quản trị hệ thống</a><a data-nav href="${pageContext.request.contextPath}/admin/community/reports"><span class="nav-icon"><ui:icon name="chat"/></span>Báo cáo cộng đồng</a></c:if>
        </nav></div>
        <div class="sidebar-note"><ui:icon name="spark"/><strong>Mỗi ngày một bước tiến</strong><p>Chọn một bài học nhỏ.<br>Khám phá một điều mới.</p><a href="${pageContext.request.contextPath}/courses">Khám phá ngay <span aria-hidden="true">→</span></a></div>
        <p class="sidebar-signature">EduLab · Học chủ động</p>
    </aside>
    <button id="menuBackdrop" class="menu-backdrop" type="button" aria-label="Đóng menu" hidden></button>
    <main id="mainContent" class="main-content" tabindex="-1"><div id="globalNotice" class="alert alert-danger d-none" role="alert"></div><div class="${discovery or community or social ? 'discovery-content' : 'workspace-content'}"><jsp:include page="${contentPage}"/></div></main>
    <c:if test="${discovery or social}"><jsp:include page="/WEB-INF/jsp/layout/utilities.jsp"/></c:if>
    <c:if test="${community}"><jsp:include page="/WEB-INF/jsp/forum/rail.jsp"/></c:if>
</div>
<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
<c:if test="${contentPage != '/WEB-INF/jsp/lesson/video.jsp'}"><jsp:include page="/WEB-INF/jsp/layout/ai-chat.jsp"/></c:if>
</body>
</html>
