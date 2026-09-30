<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<section class="auth-story">
    <span class="eyebrow">CHÀO MỪNG TRỞ LẠI</span>
    <h2>Tiếp tục hành trình<br><span class="text-primary">khám phá kiến thức.</span></h2>
    <p>Đăng nhập để xem lộ trình, bài học, quiz và tiến độ của riêng bạn.</p>
    <div class="banner-illustration" aria-hidden="true">
        <span class="orbit orbit-one"></span><span class="orbit orbit-two"></span>
        <div class="banner-book"><span class="icon">E</span><strong>EduLab</strong><small>HỌC ĐỂ HIỂU</small></div>
        <span class="formula formula-one">H₂O</span><span class="formula formula-two">x²</span><span class="banner-star">✦</span>
    </div>
</section>
<section class="auth-card">
    <span class="eyebrow">GÓC HỌC TẬP</span>
    <h1>Đăng nhập</h1>
    <p>Tiếp tục lộ trình học của bạn.</p>
    <div id="error" role="alert" class="alert alert-danger d-none"></div>
    <form id="loginForm">
        <label for="username" class="form-label">Tên đăng nhập</label>
        <input id="username" class="form-control mb-3" required maxlength="80" autocomplete="username">
        <label for="password" class="form-label">Mật khẩu</label>
        <input id="password" class="form-control mb-3" type="password" required autocomplete="current-password">
        <button class="btn btn-primary w-100" type="submit">Đăng nhập</button>
    </form>
    <p class="auth-switch">Chưa có tài khoản? <a href="${pageContext.request.contextPath}/register">Tạo tài khoản EduLab</a></p>
    <p class="auth-footnote">Học tập an toàn: không chia sẻ mật khẩu hoặc thông tin riêng tư.</p>
</section>
