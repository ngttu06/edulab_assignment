<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<div class="page-heading">
    <span class="eyebrow">TÀI KHOẢN CỦA BẠN</span>
    <h1>Thông tin cá nhân</h1>
    <p>Cập nhật thông tin và giữ tài khoản EduLab của bạn an toàn.</p>
</div>
<section class="profile-summary" aria-label="Tổng quan tài khoản">
    <span class="profile-avatar" aria-hidden="true"><ui:icon name="user"/></span>
    <div>
        <h2 id="profileDisplayName"><c:out value="${profile.fullName()}"/></h2>
        <p>@<c:out value="${profile.username()}"/> <span aria-hidden="true">·</span>
            <c:choose>
                <c:when test="${profile.role() == 'ADMIN'}">Quản trị viên</c:when>
                <c:when test="${profile.role() == 'TEACHER'}">Giáo viên</c:when>
                <c:when test="${profile.role() == 'PARENT'}">Phụ huynh</c:when>
                <c:otherwise>Học sinh</c:otherwise>
            </c:choose>
        </p>
    </div>
    <a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/dashboard">Về góc của tôi</a>
</section>
<div class="profile-grid">
    <section class="profile-panel" aria-labelledby="profileInfoHeading">
        <div class="profile-panel-heading"><span class="nav-icon blue" aria-hidden="true"><ui:icon name="user"/></span><div><h2 id="profileInfoHeading">Thông tin cá nhân</h2><p>Thông tin cơ bản của tài khoản.</p></div></div>
        <div id="profileNotice" class="alert d-none" role="status" aria-live="polite" tabindex="-1"></div>
        <form id="profileForm">
            <div class="profile-field">
                <label class="form-label" for="profileFullName">Họ và tên</label>
                <input id="profileFullName" name="fullName" class="form-control" value="<c:out value='${profile.fullName()}'/>" required maxlength="160" autocomplete="name">
            </div>
            <div class="profile-field">
                <label class="form-label" for="profileEmail">Email</label>
                <input id="profileEmail" name="email" class="form-control" type="email" value="<c:out value='${profile.email()}'/>" required maxlength="160" autocomplete="email">
            </div>
            <div class="profile-field">
                <label class="form-label" for="profileUsername">Tên đăng nhập</label>
                <input id="profileUsername" class="form-control" value="<c:out value='${profile.username()}'/>" readonly aria-describedby="usernameHelp">
                <p id="usernameHelp" class="form-text">Tên đăng nhập và vai trò do hệ thống quản lý, không thể tự thay đổi.</p>
            </div>
            <div class="profile-actions"><button class="btn btn-primary" type="submit">Lưu thay đổi</button><button class="btn btn-outline-secondary" type="reset">Hoàn tác</button></div>
        </form>
    </section>
    <section class="profile-panel" aria-labelledby="passwordHeading">
        <div class="profile-panel-heading"><span class="nav-icon orange" aria-hidden="true"><ui:icon name="lock"/></span><div><h2 id="passwordHeading">Đổi mật khẩu</h2><p>Bảo vệ tài khoản bằng mật khẩu riêng.</p></div></div>
        <div id="passwordNotice" class="alert d-none" role="status" aria-live="polite" tabindex="-1"></div>
        <form id="passwordForm">
            <div class="profile-field">
                <label class="form-label" for="currentPassword">Mật khẩu hiện tại</label>
                <input id="currentPassword" name="currentPassword" class="form-control" type="password" required maxlength="72" autocomplete="current-password">
            </div>
            <div class="profile-field">
                <label class="form-label" for="newPassword">Mật khẩu mới</label>
                <input id="newPassword" name="newPassword" class="form-control" type="password" required minlength="8" maxlength="72" autocomplete="new-password" aria-describedby="passwordHelp">
                <p id="passwordHelp" class="form-text">Ít nhất 8 ký tự, tối đa 72 byte UTF-8 và khác mật khẩu hiện tại.</p>
            </div>
            <div class="profile-field">
                <label class="form-label" for="confirmPassword">Xác nhận mật khẩu mới</label>
                <input id="confirmPassword" name="confirmPassword" class="form-control" type="password" required minlength="8" maxlength="72" autocomplete="new-password">
            </div>
            <label class="profile-show-password"><input id="showPasswords" class="form-check-input" type="checkbox">Hiển thị mật khẩu</label>
            <p class="profile-security-note"><ui:icon name="lock"/>Sau khi đổi mật khẩu, mọi phiên đăng nhập sẽ kết thúc. Bạn cần đăng nhập lại bằng mật khẩu mới.</p>
            <div class="profile-actions"><button class="btn btn-primary" type="submit">Đổi mật khẩu</button></div>
        </form>
    </section>
</div>
<script src="${pageContext.request.contextPath}/js/profile.js" defer></script>