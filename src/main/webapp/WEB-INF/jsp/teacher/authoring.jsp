<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<span class="eyebrow">KHÔNG GIAN GIÁO VIÊN</span>
<h1 class="h3">Soạn bài học &amp; quiz</h1>
<p class="text-muted">Nội dung chỉ hiển thị trong các lớp đã chọn sau khi xuất bản. Chỉ tác giả và quản trị viên được chỉnh sửa.</p>
<div class="d-flex gap-2 flex-wrap mb-4"><a class="btn btn-outline-primary" href="#lessonEditor">Bài học của tôi</a><a class="btn btn-outline-primary" href="#quizEditor">Quiz của tôi</a><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/teacher/classes">Quản lý lớp</a></div>
<c:if test="${empty data.classes}"><p class="alert alert-warning">Bạn chưa được phân công lớp. Hãy liên hệ quản trị viên trước khi tạo nội dung.</p></c:if>
<p id="authorNotice" role="status" aria-live="polite"></p>
<section id="lessonEditor" class="card mb-4"><div class="card-body">
    <h2 class="h4">Bài học của tôi</h2>
    <label for="lessonSelect" class="form-label">Mở bài học để chỉnh sửa</label>
    <select id="lessonSelect" class="form-select mb-3"><option value="">Tạo bài học mới</option><c:forEach var="lesson" items="${data.lessons}"><option value="${lesson.id}"><c:out value="${lesson.title}"/> — <c:out value="${lesson.status}"/></option></c:forEach></select>
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
        <div class="d-flex gap-2 mt-3"><button class="btn btn-primary" type="submit">Lưu bài học</button><button class="btn btn-outline-primary" type="button" id="newLesson">Tạo mới</button></div>
    </form>
</div></section>
<section id="quizEditor" class="card"><div class="card-body">
    <h2 class="h4">Quiz của tôi</h2>
    <label for="quizSelect" class="form-label">Mở quiz để chỉnh sửa</label>
    <select id="quizSelect" class="form-select mb-3"><option value="">Tạo quiz mới</option><c:forEach var="quiz" items="${data.quizzes}"><option value="${quiz.id}"><c:out value="${quiz.title}"/></option></c:forEach></select>
    <div class="border rounded p-3 mb-4">
        <h3 class="h6">Nhập câu hỏi từ file</h3><p class="small">CSV UTF-8, tối đa 1 MB / 200 câu. Có 2–4 lựa chọn, một đáp án đúng. Nhập file chỉ tạo bản xem trước bên dưới, chưa lưu vào hệ thống.</p>
        <a class="btn btn-outline-primary btn-sm mb-2" href="${pageContext.request.contextPath}/teacher/quizzes/template.csv" download>Tải file CSV mẫu</a>
        <label for="quizCsv" class="form-label d-block">Chọn file câu hỏi và đáp án</label><input id="quizCsv" type="file" class="form-control mb-2" accept=".csv,text/csv"><button id="importQuiz" type="button" class="btn btn-outline-primary">Nhập &amp; xem trước</button>
    </div>
    <form id="teacherQuizForm">
        <div class="row g-3">
            <div class="col-12"><label for="quizTitle" class="form-label">Tên quiz</label><input id="quizTitle" class="form-control" required maxlength="200"></div>
            <div class="col-md-6"><label for="quizDuration" class="form-label">Thời gian làm (phút)</label><input id="quizDuration" class="form-control" type="number" min="1" max="600" value="15" required></div>
            <div class="col-md-6"><label for="quizAttempts" class="form-label">Số lượt làm tối đa</label><input id="quizAttempts" class="form-control" type="number" min="1" max="100" value="1" required></div>
            <div class="col-md-6"><label for="quizMode" class="form-label">Chế độ</label><select id="quizMode" class="form-select"><option value="PRACTICE">Luyện tập — hiện đáp án sau khi nộp</option><option value="TEST">Kiểm tra — công bố đáp án theo lịch</option></select></div>
            <div class="col-md-6"><label for="quizReveal" class="form-label">Công bố đáp án (giờ thiết bị)</label><input id="quizReveal" class="form-control" type="datetime-local"></div>
            <fieldset class="col-12"><legend class="h6">Lớp được làm quiz (chọn ít nhất một)</legend><c:forEach var="classroom" items="${data.classes}"><div class="form-check"><input class="form-check-input" type="checkbox" name="quizClass" value="${classroom.id}" id="quizClass${classroom.id}"><label class="form-check-label" for="quizClass${classroom.id}"><c:out value="${classroom.name}"/> · <c:out value="${classroom.academic_year}"/></label></div></c:forEach></fieldset>
        </div>
        <h3 class="h5 mt-4">Câu hỏi <span id="questionCount" class="text-muted"></span></h3><p class="small text-muted">Điểm từng câu là trọng số; kết quả cuối được quy đổi về thang 10. Sửa quiz không thay đổi các lượt làm đã bắt đầu.</p>
        <div id="questionList"></div>
        <button id="addQuestion" type="button" class="btn btn-outline-primary mb-3">+ Thêm câu hỏi</button>
        <div class="form-check mb-3"><input id="quizPublished" class="form-check-input" type="checkbox"><label for="quizPublished" class="form-check-label">Xuất bản cho các lớp đã chọn (bỏ chọn để lưu nháp/ẩn)</label></div>
        <div class="d-flex flex-wrap gap-2"><button class="btn btn-primary" type="submit">Lưu quiz</button><button class="btn btn-outline-primary" type="button" id="newQuiz">Tạo mới</button><a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/teacher/classes">Giao quiz và đặt hạn nộp</a></div>
    </form>
</div></section>
<template id="questionTemplate">
    <fieldset class="border rounded p-3 mb-3" data-question><legend class="h6">Câu hỏi</legend>
        <label class="form-label d-block">Nội dung<textarea data-field="content" class="form-control" rows="2" required maxlength="10000"></textarea></label>
        <div class="row g-2"><label class="col-md-6">A<input data-option class="form-control" required maxlength="1000"></label><label class="col-md-6">B<input data-option class="form-control" required maxlength="1000"></label><label class="col-md-6">C (không bắt buộc)<input data-option class="form-control" maxlength="1000"></label><label class="col-md-6">D (không bắt buộc)<input data-option class="form-control" maxlength="1000"></label></div>
        <div class="row g-2 mt-2"><label class="col-md-6">Đáp án đúng<select data-field="correctIndex" class="form-select"><option value="0">A</option><option value="1">B</option><option value="2">C</option><option value="3">D</option></select></label><label class="col-md-6">Trọng số điểm<input data-field="points" class="form-control" type="number" min="0.01" max="9999.99" step="0.01" value="1" required></label></div>
        <label class="form-label d-block mt-2">Giải thích<textarea data-field="explanation" class="form-control" rows="2" maxlength="10000"></textarea></label>
        <div class="d-flex flex-wrap gap-2"><button data-action="up" class="btn btn-outline-primary btn-sm" type="button">↑ Lên</button><button data-action="down" class="btn btn-outline-primary btn-sm" type="button">↓ Xuống</button><button data-action="remove" class="btn btn-outline-danger btn-sm" type="button">Xóa câu</button></div>
    </fieldset>
</template>
<script src="${pageContext.request.contextPath}/js/teacher-authoring.js" defer></script>