<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="app-context" content="${pageContext.request.contextPath}">
    <title><c:out value="${pageTitle}" default="EduLab KHTN 8" /></title>
    <script src="${pageContext.request.contextPath}/js/app.js"></script>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/edulab.css" rel="stylesheet">
</head>
<body class="bg-light">
<nav class="navbar navbar-expand-lg navbar-dark edulab-nav">
    <div class="container">
        <a class="navbar-brand fw-bold" href="${pageContext.request.contextPath}/dashboard">EduLab <span>KHTN 8</span></a>
        <button class="navbar-toggler" data-bs-toggle="collapse" data-bs-target="#mainNav"><span class="navbar-toggler-icon"></span></button>
        <div class="collapse navbar-collapse" id="mainNav">
            <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/dashboard">Tổng quan</a></li>
                <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/lessons">Bài học</a></li>
                <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/quizzes">Quiz</a></li>
                <li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/forum">Diễn đàn</a></li>
                <c:if test="${pageContext.request.isUserInRole('TEACHER') or pageContext.request.isUserInRole('ADMIN')}"><li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/teacher/classes">Lớp học</a></li></c:if>
                <c:if test="${pageContext.request.isUserInRole('STUDENT') or pageContext.request.isUserInRole('PARENT')}"><li class="nav-item"><a class="nav-link" href="${pageContext.request.contextPath}/family">Gia đình</a></li></c:if>
            </ul>
            <form id="logoutForm" method="post" action="${pageContext.request.contextPath}/api/v1/auth/logout">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                <button class="btn btn-outline-light btn-sm" type="submit">Đăng xuất</button>
            </form>
        </div>
    </div>
</nav>
<main class="container py-4"><div id="globalNotice" class="alert alert-danger d-none" role="alert"></div><jsp:include page="${contentPage}" /></main>
<footer class="container py-4 text-muted small border-top">EduLab KHTN 8 · Học để hiểu, khám phá để tiến bộ.</footer>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
