// Assignments Page JavaScript
(function() {
    'use strict';

    let currentStatus = new URLSearchParams(window.location.search).get('status') || 'all';
    let currentCourseId = null;

    const assignmentList = document.getElementById('assignmentList');
    const courseFilter = document.getElementById('courseFilter');
    const tabButtons = document.querySelectorAll('.tab-btn');

    // Tab switching
    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            currentStatus = btn.dataset.status;

            // Update active state
            tabButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            // Update URL
            const url = new URL(window.location);
            url.searchParams.set('status', currentStatus);
            window.history.pushState({}, '', url);

            // Reload assignments
            loadAssignments();
        });
    });

    // Course filter
    if (courseFilter) {
        courseFilter.addEventListener('change', (e) => {
            currentCourseId = e.target.value || null;
            loadAssignments();
        });
    }

    // Load assignments
    async function loadAssignments() {
        try {
            assignmentList.innerHTML = '<div class="loading-message">Đang tải bài tập...</div>';

            const params = new URLSearchParams();
            if (currentStatus && currentStatus !== 'all') {
                params.append('status', currentStatus);
            }
            if (currentCourseId) {
                params.append('courseId', currentCourseId);
            }

            const response = await fetch(`/api/v1/learn/assignments?${params}`);
            if (!response.ok) throw new Error('Failed to load assignments');

            const assignments = await response.json();
            renderAssignments(assignments);

        } catch (error) {
            console.error('Error loading assignments:', error);
            assignmentList.innerHTML = '<div class="loading-message">Không thể tải bài tập. Vui lòng thử lại.</div>';
        }
    }

    function renderAssignments(assignments) {
        if (assignments.length === 0) {
            assignmentList.innerHTML = `
                <div class="empty-state">
                    <svg class="icon" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M9 11l3 3L22 4"/>
                        <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                    </svg>
                    <h3>Không có bài tập</h3>
                    <p>${getEmptyMessage()}</p>
                </div>
            `;
            return;
        }

        const html = assignments.map(assignment => {
            const deadline = new Date(assignment.dueAt);
            const now = new Date();
            const hoursUntilDue = (deadline - now) / (1000 * 60 * 60);
            const isUrgent = hoursUntilDue > 0 && hoursUntilDue < 24;

            const deadlineClass = assignment.overdue ? 'overdue' : isUrgent ? 'urgent' : '';
            const statusClass = `status-${assignment.status}`;
            const cardClass = assignment.overdue ? 'overdue' : assignment.completed ? 'completed' : '';

            return `
                <div class="assignment-card ${cardClass}">
                    <div class="assignment-info">
                        <h3 class="assignment-title">${escapeHtml(assignment.title)}</h3>
                        <div class="assignment-meta">
                            <span class="meta-item">
                                <svg class="icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                    <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2z"/>
                                    <path d="M12 6v6l4 2"/>
                                </svg>
                                ${escapeHtml(assignment.courseName)}
                            </span>
                            <span class="meta-item">
                                <svg class="icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                                    <line x1="16" y1="2" x2="16" y2="6"/>
                                    <line x1="8" y1="2" x2="8" y2="6"/>
                                    <line x1="3" y1="10" x2="21" y2="10"/>
                                </svg>
                                ${escapeHtml(assignment.className)}
                            </span>
                        </div>
                        <div class="assignment-deadline ${deadlineClass}">
                            ${assignment.overdue ? '⚠️ Quá hạn: ' : assignment.completed ? '✓ Đã nộp: ' : '📅 Hạn nộp: '}
                            ${formatDateTime(deadline)}
                            ${!assignment.completed && !assignment.overdue ? ` (${formatTimeUntil(deadline)})` : ''}
                        </div>
                    </div>
                    <div class="assignment-actions">
                        <span class="assignment-status-badge ${statusClass}">
                            ${getStatusLabel(assignment.status)}
                        </span>
                        ${assignment.score !== null && assignment.score !== undefined ?
                            `<div class="assignment-score">${assignment.score}/10</div>` : ''}
                        <a href="/quizzes/${assignment.quizId}" class="btn btn-sm ${assignment.completed ? 'btn-outline-secondary' : 'btn-primary'}">
                            ${assignment.completed ? 'Xem lại' : 'Bắt đầu'}
                        </a>
                    </div>
                </div>
            `;
        }).join('');

        assignmentList.innerHTML = html;
    }

    function getEmptyMessage() {
        switch(currentStatus) {
            case 'todo': return 'Bạn không có bài tập nào cần làm';
            case 'overdue': return 'Bạn không có bài tập nào quá hạn';
            case 'completed': return 'Bạn chưa hoàn thành bài tập nào';
            default: return 'Chưa có bài tập được giao';
        }
    }

    function getStatusLabel(status) {
        switch(status) {
            case 'todo': return 'Cần làm';
            case 'overdue': return 'Quá hạn';
            case 'completed': return 'Đã hoàn thành';
            default: return status;
        }
    }

    function formatDateTime(date) {
        return date.toLocaleString('vi-VN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function formatTimeUntil(date) {
        const now = new Date();
        const diff = date - now;

        if (diff < 0) return 'đã quá hạn';

        const days = Math.floor(diff / (1000 * 60 * 60 * 24));
        const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));

        if (days > 0) return `còn ${days} ngày`;
        if (hours > 0) return `còn ${hours} giờ`;
        return 'sắp đến hạn';
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Initialize
    loadAssignments();

})();
