<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="vi"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="app-context" content="${pageContext.request.contextPath}">
<title>Đăng ký · EduLab KHTN 8</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="${pageContext.request.contextPath}/css/edulab.css" rel="stylesheet">
<script src="${pageContext.request.contextPath}/js/app.js" defer></script>
<script src="${pageContext.request.contextPath}/js/auth.js" defer></script>
</head><body class="login-page"><main class="container">
<div class="row justify-content-center py-5"><div class="col-12 col-md-7 col-lg-5">
<h1 class="h3 text-white text-center mb-4">EduLab KHTN 8</h1>
<div class="card"><div class="card-body p-4"><h2 class="h5">Tạo tài khoản</h2>
<p class="text-muted">Dành cho học sinh và phụ huynh.</p>
<div id="notice" role="alert" class="alert d-none"></div>
<form id="registerForm">
<label for="fullName" class="form-label">Họ và tên</label><input class="form-control mb-3" id="fullName" required maxlength="160" autocomplete="name">
<label for="role" class="form-label">Vai trò</label><select class="form-select mb-3" id="role"><option value="STUDENT">Học sinh</option><option value="PARENT">Phụ huynh</option></select>
<label for="username" class="form-label">Tên đăng nhập</label><input class="form-control mb-3" id="username" required pattern="[A-Za-z0-9._-]{3,80}" autocomplete="username">
<label for="email" class="form-label">Email</label><input class="form-control mb-3" id="email" type="email" required maxlength="160" autocomplete="email">
<label for="password" class="form-label">Mật khẩu (8–72 byte UTF-8)</label><input class="form-control mb-3" id="password" type="password" minlength="8" maxlength="72" required autocomplete="new-password">
<button class="btn btn-primary w-100" type="submit">Tạo tài khoản</button>
</form><p class="mt-3 mb-0">Đã có tài khoản? <a href="${pageContext.request.contextPath}/login">Đăng nhập</a></p>
</div></div></div></div></main></body></html>
