<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<a href="${pageContext.request.contextPath}/lessons">← Thư viện bài học</a>
<header class="mt-3 mb-3"><span class="eyebrow">HỌC TỪNG ĐOẠN. NHỚ TỪNG BƯỚC.</span>
<h1 class="h3"><c:out value="${lesson.title()}"/></h1>
<p class="text-muted"><c:out value="${lesson.courseTitle()}"/> · <c:out value="${lesson.topicName()}"/></p></header>
<section id="videoLearning" data-lesson="${lesson.id()}" data-student="${pageContext.request.isUserInRole('STUDENT')}" data-provider="${video.provider()}" data-source="<c:out value='${video.sourceUrl()}'/>" data-youtube="<c:out value='${video.youtubeId()}'/>" data-duration="${video.durationSeconds()}" data-threshold="${video.completionThreshold()}" data-player-path="<c:out value='${playerPath}'/>" data-player-available="${playerAvailable}">
    <div id="videoFrame" class="video-frame">
        <div class="video-screen"><div id="lessonPlayer"></div><p id="playerLoading" class="video-loading" role="status">Đang chuẩn bị trình phát…</p></div>
        <div class="video-controls">
            <div class="video-control-row"><button id="videoPlay" type="button" class="btn btn-sm btn-light" disabled>Phát / Tạm dừng</button><span id="videoTime">00:00 / 00:00</span><button id="videoFullscreen" type="button" class="btn btn-sm btn-outline-light" disabled>Toàn màn hình</button></div>
            <div class="watched-timeline"><svg id="watchedTrack" viewBox="0 0 1000 12" preserveAspectRatio="none" aria-hidden="true"><rect width="1000" height="12" class="unwatched-segment"/><g id="watchedSegments"></g><line id="videoPlayhead" x1="0" x2="0" y1="0" y2="12"/></svg><input id="videoSeek" type="range" min="0" max="${video.durationSeconds()}" step="0.1" value="0" disabled aria-label="Tua video" aria-valuetext="00:00"></div>
            <p class="timeline-legend"><span class="watched-dot" aria-hidden="true"></span> Xanh: đã xem <span class="unwatched-dot" aria-hidden="true"></span> Xám: chưa xem <span>Vạch trắng: vị trí hiện tại</span></p>
        </div>
    </div>
    <div class="card mt-3"><div class="card-body">
        <div class="video-progress-heading"><h2 class="h5 mb-0">Tiến trình của em</h2><strong id="videoPercent">0%</strong></div>
        <progress id="videoProgress" class="w-100 mt-2" max="100" value="0" aria-label="Phần trăm timeline đã xem">0%</progress>
        <p id="watchedTime" class="small text-muted mt-2">Chưa có đoạn đã xem.</p>
        <p class="small text-muted">Xem đoạn nào, timeline đánh dấu đoạn đó. Tua bỏ qua không được tính; xem lại không cộng trùng. Hoàn thành khi xem ít nhất ${video.completionThreshold()}%. Tốc độ ghi nhận tối đa 2x.</p>
        <p id="videoNotice" role="status" aria-live="polite" class="mb-0"></p>
        <c:if test="${not pageContext.request.isUserInRole('STUDENT')}"><p class="small text-muted mt-2 mb-0">Chế độ xem thử: chỉ tài khoản học sinh được lưu tiến độ.</p></c:if>
    </div></div>
</section>
<a class="btn btn-outline-primary mt-3" href="${pageContext.request.contextPath}/lessons/${lesson.id()}">Đọc nội dung bài học →</a>
<script src="${pageContext.request.contextPath}/js/video-player.js" defer></script>
<script src="${pageContext.request.contextPath}/js/lesson-video.js" defer></script>