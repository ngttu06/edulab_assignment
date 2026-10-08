<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="class-nav.jspf" %>
<h1 class="h3">Thêm học sinh</h1>
<p id="classNotice" role="status" aria-live="polite"></p>
<section class="card mb-4"><div class="card-body"><h2 class="h5">Thêm học sinh vào lớp</h2><p class="small text-muted">Nhập chính xác tên đăng nhập của tài khoản học sinh đã có. Thêm lại sẽ khôi phục tư cách thành viên và giữ lịch sử học tập.</p>
<form id="teacherEnrollForm" data-class="${classId}" class="d-flex gap-2 flex-wrap"><label for="studentUsername" class="visually-hidden">Tên đăng nhập học sinh</label><input id="studentUsername" class="form-control" maxlength="80" required placeholder="Tên đăng nhập học sinh"><button class="btn btn-primary" type="submit">Thêm vào lớp</button></form></div></section>
<script src="${pageContext.request.contextPath}/js/teacher-class.js" defer></script>
