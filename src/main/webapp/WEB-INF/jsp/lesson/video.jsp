<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<a href="${pageContext.request.contextPath}/lessons">← Thư viện bài học</a>
<header class="video-page-heading mt-3 mb-3"><div><span class="eyebrow">HỌC TỪNG ĐOẠN. NHỚ TỪNG BƯỚC.</span>
<h1 class="h3"><c:out value="${lesson.title()}"/></h1>
<p class="text-muted"><c:out value="${lesson.courseTitle()}"/> · <c:out value="${lesson.topicName()}"/></p></div><button id="videoAiToggle" type="button" class="btn btn-sm btn-outline-primary video-ai-toggle" aria-expanded="true" aria-controls="videoAi"><ui:icon name="spark"/><span id="videoAiToggleLabel">Ẩn trợ lý AI</span></button></header>
<div class="video-learning-layout">
<section id="videoLearning" aria-label="Video bài học" data-lesson="${lesson.id()}" data-video="${video.id()}" data-student="${pageContext.request.isUserInRole('STUDENT')}" data-provider="${video.provider()}" data-source="<c:out value='${video.sourceUrl()}'/>" data-youtube="<c:out value='${video.youtubeId()}'/>" data-duration="${video.durationSeconds()}" data-threshold="${video.completionThreshold()}" data-player-path="<c:out value='${playerPath}'/>" data-player-available="${playerAvailable}">
    <div id="videoFrame" class="video-frame">
        <div class="video-screen"><div id="lessonPlayer"></div><p id="playerLoading" class="video-loading" role="status">Đang chuẩn bị trình phát…</p></div>
        <div class="video-controls">
            <div class="video-control-row"><button id="videoPlay" type="button" class="btn btn-sm btn-light" aria-label="Phát video" title="Phát video" disabled><svg class="video-play-icon" width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"><path class="video-play-shape" d="m8 4 12 8-12 8Z"/><path class="video-pause-shape" d="M6 4h4v16H6zM14 4h4v16h-4z"/></svg></button><span id="videoTime">00:00 / 00:00</span><button id="videoFullscreen" type="button" class="btn btn-sm btn-outline-light" aria-label="Toàn màn hình" aria-pressed="false" title="Toàn màn hình" disabled><svg class="fullscreen-icon" width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path class="fullscreen-enter" d="M8 3H3v5M16 3h5v5M3 16v5h5M21 16v5h-5"/><path class="fullscreen-exit" d="M3 8h5V3M21 8h-5V3M8 21v-5H3M16 21v-5h5"/></svg><span id="videoFullscreenLabel" class="visually-hidden">Toàn màn hình</span></button></div>
            <div class="watched-timeline"><svg id="watchedTrack" viewBox="0 0 1000 12" preserveAspectRatio="none" aria-hidden="true"><rect width="1000" height="12" class="unwatched-segment"/><g id="watchedSegments"></g><line id="videoPlayhead" x1="0" x2="0" y1="0" y2="12"/></svg><input id="videoSeek" type="range" min="0" max="${video.durationSeconds()}" step="0.1" value="0" disabled aria-label="Tua video" aria-valuetext="00:00"></div>
        </div>
    </div>
    <div class="card video-progress-card mt-3"><div class="card-body">
        <div class="video-progress-heading"><h2 class="h5 mb-0">Tiến trình của em</h2><strong id="videoPercent">0%</strong></div>
        <progress id="videoProgress" class="w-100 mt-2" max="100" value="0" aria-label="Phần trăm timeline đã xem">0%</progress>
        <p class="timeline-legend"><span><span class="watched-dot" aria-hidden="true"></span> Xanh: đã xem</span><span><span class="unwatched-dot" aria-hidden="true"></span> Xám: chưa xem</span><span>Vạch trắng: vị trí hiện tại</span></p>
        <p id="watchedTime" class="small text-muted mt-2">Chưa có đoạn đã xem.</p>
        <p class="small text-muted">Hoàn thành khi xem ít nhất ${video.completionThreshold()}%. Tua bỏ qua không được tính; xem lại không cộng trùng.</p>
        <p id="videoNotice" role="status" aria-live="polite" class="mb-0"></p>
        <c:if test="${not pageContext.request.isUserInRole('STUDENT')}"><p class="small text-muted mt-2 mb-0">Chế độ xem thử: chỉ tài khoản học sinh được lưu tiến độ.</p></c:if>
    </div></div>
    <a class="btn btn-outline-primary mt-3" href="${pageContext.request.contextPath}/lessons/${lesson.id()}">Đọc nội dung bài học →</a>
</section>
<aside id="videoAi" class="video-ai" aria-labelledby="videoAiTitle">
    <header class="video-ai-header"><span class="video-ai-avatar"><ui:icon name="spark"/></span><div><span class="eyebrow">HỌC CÙNG EDULAB</span><h2 id="videoAiTitle">Trợ lý AI bài học</h2></div><button id="videoAiReset" class="icon-button" type="button" aria-label="Bắt đầu cuộc trò chuyện mới" title="Cuộc trò chuyện mới"><ui:icon name="refresh"/></button></header>
    <div class="video-ai-context"><span class="video-ai-context-dot" aria-hidden="true"></span><span>Đang học cùng bạn</span><span id="videoAiTime">00:00</span></div>
    <div id="videoAiMessages" class="video-ai-messages" role="log" aria-live="polite" aria-relevant="additions" aria-label="Cuộc trò chuyện với trợ lý AI">
        <div class="video-ai-message assistant"><strong>EduLab AI</strong><p>Bạn chưa hiểu đoạn nào? Chọn khung hình đang xem và đặt câu hỏi, mình sẽ giúp bạn từng bước.</p></div>
    </div>
    <div id="videoAiSuggestions" class="video-ai-suggestions"><button type="button">Giải thích nội dung khung hình</button><button type="button">Cho mình một ví dụ dễ hiểu</button></div>
    <form id="videoAiForm" class="video-ai-composer">
        <figure id="videoAiPreview" class="video-ai-preview" hidden><img id="videoAiPreviewImage" alt="Khung hình bạn sắp gửi cho AI"><figcaption><span id="videoAiFrameTime"></span><button id="videoAiRemoveFrame" type="button" class="icon-button" aria-label="Bỏ khung hình"><ui:icon name="close"/></button></figcaption></figure>
        <label class="visually-hidden" for="videoAiQuestion">Câu hỏi dành cho trợ lý AI</label>
        <textarea id="videoAiQuestion" maxlength="2000" rows="3" placeholder="Hỏi về đoạn video đang xem…" required aria-describedby="videoAiPrivacy"></textarea>
        <div class="video-ai-actions"><button id="videoAiCapture" class="video-ai-capture" type="button" disabled><ui:icon name="camera"/>Lấy khung hình</button><label class="video-ai-upload" title="Chọn ảnh khung hình thủ công"><ui:icon name="photo"/><span class="visually-hidden">Chọn ảnh khung hình thủ công</span><input id="videoAiImage" type="file" accept="image/jpeg,image/png,image/webp" aria-label="Chọn ảnh khung hình thủ công"></label><button id="videoAiSend" class="btn btn-primary" type="submit" aria-label="Gửi câu hỏi"><ui:icon name="send"/></button></div>
        <p id="videoAiStatus" class="video-ai-status" role="status" aria-live="polite"></p>
        <p id="videoAiPrivacy" class="video-ai-privacy">Chỉ lấy ảnh trực tiếp từ video khi nguồn cho phép, không chụp tab hay màn hình. Chỉ ảnh bạn chọn và câu hỏi được gửi cho AI. Câu trả lời cần được kiểm chứng.</p>
    </form>
</aside>
</div>
<script src="${pageContext.request.contextPath}/js/video-player.js" defer></script>
<script src="${pageContext.request.contextPath}/js/lesson-video.js" defer></script>
<script src="${pageContext.request.contextPath}/js/video-ai.js" defer></script>