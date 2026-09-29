<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Thông báo · EduLab</title></head>
<body><main><h1>Không thể mở trang (<c:out value="${status}"/>)</h1><p><c:out value="${message}"/></p><a href="${pageContext.request.contextPath}/dashboard">Về trang tổng quan</a></main></body></html>
