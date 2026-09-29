<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<h1 class="h3">Diễn đàn hỏi đáp</h1><p>Không đăng số điện thoại, địa chỉ, mật khẩu hoặc thông tin riêng của bạn và người khác.</p>
<nav class="d-flex flex-wrap gap-2 my-3"><c:forEach var="category" items="${categories}"><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/forum?category=${category.id}"><c:out value="${category.name}"/></a></c:forEach></nav>
<ul class="list-group"><c:forEach var="thread" items="${threads}"><li class="list-group-item"><a href="${pageContext.request.contextPath}/forum/threads/${thread.id}"><c:out value="${thread.title}"/></a> — <c:out value="${thread.author}"/> (<c:out value="${thread.status}"/>)</li></c:forEach></ul>
<c:if test="${not empty categoryId}"><nav class="my-3" aria-label="Phân trang"><c:if test="${page > 0}"><a href="${pageContext.request.contextPath}/forum?category=${categoryId}&amp;page=${page-1}">Trang trước</a> · </c:if><span>Trang ${page+1}</span><c:if test="${threads.size() == 20 and page < 10000}"> · <a href="${pageContext.request.contextPath}/forum?category=${categoryId}&amp;page=${page+1}">Trang sau</a></c:if></nav>
<h2 class="h5">Đặt câu hỏi</h2><p id="forumNotice" role="alert"></p>
<form id="threadForm" data-category="${categoryId}"><label for="threadTitle">Tiêu đề</label><input id="threadTitle" class="form-control mb-2" required maxlength="240"><label for="threadBody">Nội dung (văn bản)</label><textarea id="threadBody" class="form-control mb-3" required maxlength="10000" rows="5"></textarea><button class="btn btn-primary">Đăng câu hỏi</button></form>
<p class="small mt-2">Sau khi tạo câu hỏi, bạn có thể thêm ảnh/PDF trong phần trả lời.</p></c:if>
<script src="${pageContext.request.contextPath}/js/forum.js" defer></script>
