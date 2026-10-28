<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<header class="app-header">
    <div class="header-brand">
        <c:if test="${param.showMenu != 'false'}"><button id="menuToggle" class="icon-button menu-toggle" type="button" aria-label="Mở menu" aria-controls="sideNav" aria-expanded="false"><ui:icon name="menu"/></button></c:if>
        <a class="brand" href="${pageContext.request.contextPath}/"><span class="logo-symbol" aria-hidden="true">E<span>·</span></span><span>EduLab</span></a>
    </div>
    <form class="header-search" action="${pageContext.request.contextPath}/courses" method="get" role="search">
        <label class="visually-hidden" for="headerQuery">Tìm khóa học</label>
        <input id="headerQuery" name="q" type="search" maxlength="100" placeholder="Tìm khóa học, môn học...">
        <button type="submit" class="icon-button" aria-label="Tìm kiếm"><ui:icon name="search"/></button>
    </form>
    <nav class="header-shortcuts" aria-label="Truy cập nhanh">
        <a data-nav href="${pageContext.request.contextPath}/" aria-label="Trang chủ" title="Trang chủ"><ui:icon name="home"/><span class="shortcut-label">Trang chủ</span></a>
        <a data-nav href="${pageContext.request.contextPath}/courses" aria-label="Khóa học" title="Khóa học"><ui:icon name="cap"/><span class="shortcut-label">Khóa học</span></a>
        <a data-nav href="${pageContext.request.contextPath}/lessons" aria-label="Bài học" title="Bài học"><ui:icon name="book"/><span class="shortcut-label">Bài học</span></a>
        <a data-nav href="${pageContext.request.contextPath}/quizzes" aria-label="Luyện tập" title="Luyện tập"><ui:icon name="quiz"/><span class="shortcut-label">Luyện tập</span></a>
        <a data-nav href="${pageContext.request.contextPath}/community" aria-label="Cộng đồng" title="Cộng đồng"><ui:icon name="users"/><span class="shortcut-label">Cộng đồng</span></a>
    </nav>
    <div class="header-account">
        <c:choose>
            <c:when test="${not empty pageContext.request.userPrincipal}">
                <c:choose>
                    <c:when test="${pageContext.request.isUserInRole('ADMIN')}"><span class="role-badge is-admin">Quản trị</span></c:when>
                    <c:when test="${pageContext.request.isUserInRole('TEACHER')}"><span class="role-badge is-teacher">Giáo viên</span></c:when>
                    <c:when test="${pageContext.request.isUserInRole('PARENT')}"><span class="role-badge is-parent">Phụ huynh</span></c:when>
                    <c:when test="${pageContext.request.isUserInRole('STUDENT')}"><span class="role-badge is-student">Học sinh</span></c:when>
                </c:choose>
                <a data-nav class="account-link" href="${pageContext.request.contextPath}/profile" aria-label="Thông tin cá nhân" title="Thông tin cá nhân"><ui:icon name="user"/><span>Tài khoản của tôi</span></a>
                <form id="logoutForm" method="post" action="${pageContext.request.contextPath}/api/v1/auth/logout">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                    <button class="btn btn-outline-primary btn-sm" type="submit">Đăng xuất</button>
                </form>
            </c:when>
            <c:otherwise>
                <a data-nav class="register-link" href="${pageContext.request.contextPath}/register">Đăng ký</a>
                <a data-nav class="btn btn-primary btn-sm" href="${pageContext.request.contextPath}/login">Đăng nhập</a>
            </c:otherwise>
        </c:choose>
    </div>
</header>