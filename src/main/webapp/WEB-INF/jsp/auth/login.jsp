<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="vi"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="app-context" content="${pageContext.request.contextPath}">
<title>Đăng nhập · EduLab KHTN 8</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="${pageContext.request.contextPath}/css/edulab.css" rel="stylesheet">
<script src="${pageContext.request.contextPath}/js/app.js" defer></script>
<script src="${pageContext.request.contextPath}/js/auth.js" defer></script>
</head><body class="login-page">
<main class="container"><div class="row justify-content-center align-items-center min-vh-100 py-4"><div class="col-12 col-md-6 col-lg-4">
<h1 class="h3 text-white text-center mb-4">EduLab KHTN 8</h1>
<div class="card border-0 shadow-lg"><div class="card-body p-4">
<h2 class="h5">Đăng nhập</h2><p class="text-muted">Tiếp tục lộ trình học của bạn.</p>
<div id="error" role="alert" class="alert alert-danger d-none"></div>
<form id="loginForm">
<label for="username" class="form-label">Tên đăng nhập</label>
<input id="username" class="form-control mb-3" required maxlength="80" autocomplete="username">
<label for="password" class="form-label">Mật khẩu</label>
<input id="password" class="form-control mb-3" type="password" required autocomplete="current-password">
<button class="btn btn-primary w-100" type="submit">Đăng nhập</button>
</form>
<p class="mt-3 mb-0">Chưa có tài khoản? <a href="${pageContext.request.contextPath}/register">Đăng ký</a></p>
</div></div></div></div></main>
</body></html>
