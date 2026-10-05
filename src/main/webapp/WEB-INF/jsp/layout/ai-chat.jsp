<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<div class="ai-widget">
    <button id="aiChatToggle" class="ai-chat-toggle" type="button" aria-haspopup="dialog" aria-expanded="false" aria-controls="aiChatDialog"><ui:icon name="spark"/><span>Hỏi AI</span></button>
    <dialog id="aiChatDialog" class="ai-chat-dialog" aria-labelledby="aiChatTitle" aria-describedby="aiChatPrivacy">
        <header class="ai-chat-header"><span class="ai-chat-avatar"><ui:icon name="spark"/></span><div><h2 id="aiChatTitle">Trợ lý EduLab</h2><p>Hiểu website · Chọn khóa học phù hợp</p></div><button id="aiChatClose" type="button" class="icon-button" aria-label="Đóng hộp chat"><ui:icon name="close"/></button></header>
        <div id="aiChatMessages" class="ai-chat-messages" role="log" aria-label="Cuộc trò chuyện" aria-live="polite" aria-relevant="additions" tabindex="0">
            <div class="ai-chat-message assistant"><strong>EduLab AI</strong><p>Xin chào! Mình có thể hướng dẫn bạn sử dụng EduLab hoặc tìm khóa học phù hợp. Bạn đang quan tâm môn học và khối lớp nào?</p></div>
        </div>
        <div id="aiChatSuggestions" class="ai-chat-suggestions" aria-label="Câu hỏi gợi ý"><button type="button">Website có những chức năng gì?</button><button type="button">Tư vấn khóa học phù hợp</button><button type="button">Bắt đầu học như thế nào?</button></div>
        <p id="aiChatStatus" class="ai-chat-status" role="status"></p>
        <form id="aiChatForm" class="ai-chat-form"><label class="visually-hidden" for="aiChatQuestion">Câu hỏi của bạn</label><div class="ai-chat-compose"><textarea id="aiChatQuestion" rows="2" maxlength="2000" required placeholder="Bạn muốn tìm hiểu điều gì?" aria-describedby="aiChatPrivacy"></textarea><button id="aiChatSend" type="submit" class="btn btn-primary" aria-label="Gửi câu hỏi"><ui:icon name="send"/></button></div><p id="aiChatPrivacy">AI có thể trả lời sai. Câu hỏi được gửi tới nhà cung cấp AI. Không gửi mật khẩu hoặc thông tin cá nhân.</p></form>
        <footer class="ai-chat-footer"><button id="aiChatReset" type="button">Cuộc trò chuyện mới</button><span>EduLab · Học chủ động</span></footer>
    </dialog>
</div>