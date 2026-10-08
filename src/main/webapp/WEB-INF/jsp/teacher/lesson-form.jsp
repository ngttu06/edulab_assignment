<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="nav.jspf" %>
<a href="${pageContext.request.contextPath}/teacher/${kind}">← Danh sách</a>
<h1 class="h3 my-3">${empty editId ? (importMode ? "Nhập quiz từ CSV" : "Tạo mới") : "Chỉnh sửa"}</h1>
<p id="authorNotice" role="status" aria-live="polite"></p>
<section id="lessonEditor" class="card mb-4"><div class="card-body">
    <h2 class="h4">Bài học của tôi</h2>
<input id="lessonSelect" type="hidden" value="${editId}">
    <form id="teacherLessonForm">
        <div class="row g-3">
            <div class="col-md-8"><label for="lessonTitle" class="form-label">Tiêu đề</label><input id="lessonTitle" class="form-control" required maxlength="200"></div>
            <div class="col-md-4"><label for="lessonDuration" class="form-label">Thời lượng (phút)</label><input id="lessonDuration" class="form-control" type="number" min="1" max="600" value="30" required></div>
            <div class="col-12"><label for="lessonTopic" class="form-label">Môn / khóa học / chủ đề</label><select id="lessonTopic" class="form-select" required><c:forEach var="topic" items="${data.topics}"><option value="${topic.id}"><c:out value="${topic.name}"/></option></c:forEach></select></div>
            <div class="col-12"><label for="lessonSummary" class="form-label">Mô tả ngắn</label><textarea id="lessonSummary" class="form-control" maxlength="500" rows="2"></textarea></div>
            <div class="col-12"><label for="lessonObjectives" class="form-label">Mục tiêu học tập</label><textarea id="lessonObjectives" class="form-control" maxlength="10000" rows="2"></textarea></div>
            <div class="col-12"><label for="lessonContent" class="form-label">Nội dung bài học</label><textarea id="lessonContent" class="form-control" required maxlength="100000" rows="10" aria-describedby="lessonContentHelp"></textarea><p id="lessonContentHelp" class="form-text">Có thể nhập văn bản hoặc HTML cơ bản: p, h2, h3, ul, li, strong. Mã script và HTML không an toàn sẽ bị loại bỏ.</p></div>
            <fieldset class="col-12"><legend class="h6">Lớp được học (chọn ít nhất một)</legend><c:forEach var="classroom" items="${data.classes}"><div class="form-check"><input class="form-check-input" type="checkbox" name="lessonClass" value="${classroom.id}" id="lessonClass${classroom.id}"><label class="form-check-label" for="lessonClass${classroom.id}"><c:out value="${classroom.name}"/> · <c:out value="${classroom.academic_year}"/></label></div></c:forEach></fieldset>
            <div class="col-md-6"><label for="lessonStatus" class="form-label">Trạng thái</label><select id="lessonStatus" class="form-select"><option value="DRAFT">Bản nháp</option><option value="PUBLISHED">Xuất bản</option><option value="ARCHIVED">Lưu trữ</option></select></div>
        </div>
        <div class="teacher-actions mt-3"><button class="btn btn-primary" type="submit">Lưu bài học</button><button id="previewLesson" type="button" class="btn btn-outline-primary">Xem trước nội dung</button><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/teacher/lessons">Hủy</a></div>
    </form>
    <section id="lessonPreview" class="border rounded p-3 mt-3 d-none" aria-label="Xem trước bài học"><h3 class="h5">Xem trước nội dung đã lọc</h3><div id="lessonPreviewBody"></div></section>
</div></section>
<script src="${pageContext.request.contextPath}/js/teacher-authoring.js" defer></script>
