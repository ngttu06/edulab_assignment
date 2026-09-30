<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="app-context" content="${pageContext.request.contextPath}">
    <title><c:out value="${pageTitle}"/></title>
    <link href="${pageContext.request.contextPath}/css/vendor/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/fonts.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/edulab.css" rel="stylesheet">
    <script src="${pageContext.request.contextPath}/js/app.js" defer></script>
    <script src="${pageContext.request.contextPath}/js/auth.js" defer></script>
</head>
<body class="auth-page">
<a class="skip-link" href="#mainContent">Đến nội dung chính</a>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp"><jsp:param name="showMenu" value="false"/></jsp:include>
<main id="mainContent" class="main-content auth-main" tabindex="-1">
    <div id="globalNotice" class="alert alert-danger d-none" role="alert"></div>
    <div class="auth-layout"><jsp:include page="${contentPage}"/></div>
</main>
<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
</body>
</html>