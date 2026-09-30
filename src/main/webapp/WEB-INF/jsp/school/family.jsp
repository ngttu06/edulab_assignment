<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<span class="eyebrow">CÙNG CON TIẾN BỘ</span><h1 class="h3">Liên kết phụ huynh — học sinh</h1>
<p>Chỉ chia sẻ mã riêng với phụ huynh của bạn. Mã dùng một lần, hết hạn sau 15 phút. Khi nhập mã hợp lệ, phụ huynh được xem tiến độ và điểm. Bạn có thể hủy quyền này bên dưới.</p>
<p id="schoolNotice" role="status"></p>
<c:if test="${pageContext.request.isUserInRole('STUDENT')}"><button id="generateCode" class="btn btn-primary">Tạo mã mới</button> <button id="revokeCodes" class="btn btn-outline-danger">Hủy mã chưa dùng</button><p id="linkCode" class="mt-3 text-break" role="status"></p></c:if>
<c:if test="${pageContext.request.isUserInRole('PARENT')}"><form id="redeemCode" class="my-3"><label for="code">Mã liên kết học sinh cung cấp</label><input id="code" class="form-control my-2" required pattern="[a-f0-9]{32}" maxlength="32" autocomplete="off"><button class="btn btn-primary">Liên kết</button></form></c:if>
<ul class="list-group mt-4"><c:forEach var="link" items="${links}"><li class="list-group-item"><c:out value="${link.parent_name}"/> ↔ <a href="${pageContext.request.contextPath}/school/students/${link.student_id}"><c:out value="${link.student_name}"/></a> <button class="btn btn-sm btn-outline-danger revokeLink" data-id="${link.id}">Hủy liên kết</button></li></c:forEach></ul>
<c:if test="${empty links}"><p class="mt-3">Chưa có liên kết đang hoạt động.</p></c:if>
<script src="${pageContext.request.contextPath}/js/school.js" defer></script>
