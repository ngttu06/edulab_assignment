<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<!doctype html><html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="app-context" content="${pageContext.request.contextPath}">
    <title><c:out value="${pageTitle}" default="EduLab Admin"/></title>
    <link href="${pageContext.request.contextPath}/css/vendor/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/admin.css" rel="stylesheet">
    <c:if test="${adminActive == 'videos'}"><link href="${pageContext.request.contextPath}/css/lesson-video.css" rel="stylesheet"></c:if>
    <script src="${pageContext.request.contextPath}/js/app.js" defer></script>
    <script src="${pageContext.request.contextPath}/js/admin-workspace.js" defer></script>
</head><body class="admin-body">
<a class="skip-link" href="#mainContent">Đến nội dung chính</a>
<aside id="adminSidebar" class="admin-sidebar" aria-label="Điều hướng quản trị">
    <a class="admin-brand" href="${pageContext.request.contextPath}/admin"><span class="admin-mark"><ui:icon name="cap"/></span><span>EduLab<span class="admin-brand-sub">ADMIN CONSOLE</span></span></a>
    <button id="adminMenuClose" class="admin-mobile-close" type="button" aria-label="Đóng menu"><ui:icon name="close"/></button>
    <nav class="admin-nav" aria-label="Quản trị hệ thống">
        <a href="${pageContext.request.contextPath}/admin" ${adminActive == 'dashboard' ? 'aria-current="page"' : ''}><ui:icon name="chart"/>Tổng quan hệ thống</a>
        <c:forEach var="group" items="${['Con người & lớp học','Nội dung & học tập','Vận hành','Hệ thống']}">
            <h2><c:out value="${group}"/></h2>
            <c:forEach var="nav" items="${adminNavigation}"><c:if test="${nav.group() == group and nav.key() != 'administrators'}">
                <a href="${pageContext.request.contextPath}/admin/${nav.key()}" ${adminActive == nav.key() ? 'aria-current="page"' : ''}><ui:icon name="${nav.icon()}"/><c:out value="${nav.title()}"/></a>
                <c:if test="${nav.key() == 'classes'}"><a href="${pageContext.request.contextPath}/admin/teaching-assignments" ${adminActive == 'teaching-assignments' ? 'aria-current="page"' : ''}><ui:icon name="users"/>Phân công giảng dạy</a></c:if>
                <c:if test="${nav.key() == 'lessons'}"><a href="${pageContext.request.contextPath}/admin/videos" ${adminActive == 'videos' ? 'aria-current="page"' : ''}><ui:icon name="photo"/>Video bài học</a></c:if>
            </c:if></c:forEach>
            <c:if test="${group == 'Vận hành'}"><a href="${pageContext.request.contextPath}/admin/community/reports" ${adminActive == 'reports' ? 'aria-current="page"' : ''}><ui:icon name="chat"/>Báo cáo vi phạm</a></c:if>
            <c:if test="${group == 'Hệ thống'}">
                <a href="${pageContext.request.contextPath}/admin/administrators" ${adminActive == 'administrators' ? 'aria-current="page"' : ''}><ui:icon name="user"/>Tài khoản quản trị</a>
                <a href="${pageContext.request.contextPath}/admin/settings/ai" ${adminActive == 'ai' ? 'aria-current="page"' : ''}><ui:icon name="spark"/>Cấu hình AI</a>
                <a href="${pageContext.request.contextPath}/admin/settings/storage" ${adminActive == 'storage' ? 'aria-current="page"' : ''}><ui:icon name="settings"/>Cấu hình lưu trữ</a>
            </c:if>
        </c:forEach>
    </nav>
    <div class="admin-sidebar-footer"><span class="admin-secure-dot"></span>Không gian quản trị riêng tư<small>EduLab · Quản lý & vận hành</small></div>
</aside>
<button id="adminBackdrop" class="admin-backdrop" type="button" aria-label="Đóng menu" hidden></button>
<div class="admin-workspace">
    <header class="admin-topbar">
        <div class="admin-topbar-left"><button id="adminMenuToggle" class="admin-icon-button" type="button" aria-label="Mở menu" aria-controls="adminSidebar" aria-expanded="false"><ui:icon name="menu"/></button><span class="admin-breadcrumb">Không gian quản trị <span aria-hidden="true">/</span> <strong><c:out value="${adminTitle}"/></strong></span></div>
        <div class="admin-topbar-right"><a class="admin-site-link" href="${pageContext.request.contextPath}/">Xem website <span aria-hidden="true">↗</span></a><span class="admin-avatar" aria-hidden="true">AD</span><div class="admin-account"><strong><c:out value="${pageContext.request.userPrincipal.name}"/></strong><small>Quản trị viên</small></div><form id="logoutForm"><button class="admin-icon-button" type="submit" aria-label="Đăng xuất"><ui:icon name="lock"/></button></form></div>
    </header>
    <main id="mainContent" class="admin-main" tabindex="-1">
        <div id="globalNotice" class="alert alert-danger d-none" role="alert"></div>
        <div id="adminNotice" class="admin-notice" role="status" tabindex="-1" hidden></div>
        <jsp:include page="${contentPage}"/>
    </main>
    <footer class="admin-footer"><span>EduLab Admin</span><span>Quản trị tập trung · Dữ liệu được bảo vệ</span></footer>
</div>
<dialog id="adminConfirm" class="admin-dialog" aria-labelledby="adminConfirmTitle"><form method="dialog"><span class="admin-dialog-symbol"><ui:icon name="settings"/></span><h2 id="adminConfirmTitle">Xác nhận thao tác</h2><p id="adminConfirmMessage"></p><div class="admin-dialog-actions"><button class="btn btn-outline-secondary" value="cancel" autofocus>Quay lại</button><button class="btn btn-danger" value="confirm">Xác nhận</button></div></form></dialog>
</body></html>