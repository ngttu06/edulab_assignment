(() => {
'use strict';

const context = () => (window.edulab && window.edulab.context) || document.querySelector('meta[name="app-context"]')?.content || '';
const api = (path, options) => window.edulab.api(path, options);
const byId = id => document.getElementById(id);
const setText = (id, value) => { const node = byId(id); if (node) node.textContent = String(value); };
const wait = milliseconds => new Promise(resolve => window.setTimeout(resolve, milliseconds));

/* Every state-changing call is serialized: the CSRF endpoint rotates the XSRF cookie on each
   request, so two parallel writes would invalidate each other's token and fail with 403. */
let mutationQueue = Promise.resolve();
const mutate = task => {
    const run = mutationQueue.then(task, task);
    mutationQueue = run.catch(() => {});
    return run;
};

function formatDeadlines(root = document) {
    root.querySelectorAll('time[data-deadline]').forEach(node => {
        const value = new Date(node.getAttribute('datetime'));
        if (Number.isNaN(value.getTime())) return;
        node.textContent = value.toLocaleString('vi-VN', {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'});
    });
}

/* ===== Quiz list: start or resume, then jump to the dedicated quiz workspace ===== */
document.querySelectorAll('.startQuiz').forEach(button => {
    button.addEventListener('click', async () => {
        if (button.dataset.mode === 'TEST' && !window.confirm('Bài kiểm tra sẽ ghi nhận việc chuyển tab/rời trang và gửi số lần đến giáo viên. Đáp án chỉ mở theo lịch công bố; AI bị khóa khi đang làm bài. Bạn đã sẵn sàng?')) return;
        const original = button.innerHTML;
        button.disabled = true;
        button.textContent = 'Đang mở bài làm…';
        try {
            const attempt = await mutate(() => api('/api/v1/quizzes/' + button.dataset.id + '/attempts', {method: 'POST'}));
            window.location.assign(context() + '/quizzes/attempts/' + attempt.id);
        } catch (error) {
            const notice = byId('quizNotice');
            if (notice) notice.textContent = error.message;
            button.disabled = false;
            button.innerHTML = original;
        }
    });
});

formatDeadlines();

const quizForm = byId('quizForm');
if (quizForm && byId('quizQuestions')) initTaking(quizForm);
else initReview();

/* ===== Taking mode ===== */
function initTaking(form) {
    const attemptId = form.dataset.id;
    const isTest = form.dataset.mode === 'TEST';
    const questions = Array.from(form.querySelectorAll('.quiz-question'));
    const total = questions.length;
    const questionsArea = byId('quizQuestions');
    const toastNode = byId('quizToast');
    const warning = byId('tabWarning');
    const paletteToggle = byId('paletteToggle');
    const paletteBackdrop = byId('paletteBackdrop');
    const palettePanel = byId('quizPalettePanel');
    const guard = byId('quizGuard');
    const loading = byId('quizLoading');
    const loadingBar = byId('quizLoadingBar');
    const loadingMessage = byId('quizLoadingMessage');
    const loadingSteps = Array.from(document.querySelectorAll('.quiz-loading-steps li'));
    const timerNode = byId('quizTimer');
    const deadlineNode = byId('deadline');
    const deadlineAt = deadlineNode ? new Date(deadlineNode.getAttribute('datetime')).getTime() : NaN;

    let marked = new Set();
    let currentId = null;
    let submitting = false;
    let expired = false;
    let toastTimer = 0;
    let guardTimer = 0;
    let saveTimer = 0;
    let lastNotice = 0;
    let loadingTimers = [];
    let lastSaved = null;

    const markKey = 'edulab-quiz-flags-' + attemptId;
    try {
        const stored = JSON.parse(window.localStorage.getItem(markKey) || '[]');
        if (Array.isArray(stored)) marked = new Set(stored.map(String));
    } catch (error) { marked = new Set(); }

    const chips = new Map();
    document.querySelectorAll('#quizPalette .quiz-chip').forEach(chip => chips.set(chip.dataset.question, chip));
    const markButtons = new Map();
    form.querySelectorAll('.quiz-mark').forEach(button => markButtons.set(button.dataset.question, button));

    const persistMarks = () => {
        try { window.localStorage.setItem(markKey, JSON.stringify([...marked])); } catch (error) { /* storage may be blocked */ }
    };
    const selectedAnswers = () => {
        const answers = {};
        new FormData(form).forEach((value, key) => { answers[key] = Number(value); });
        return answers;
    };
    const answeredSet = () => new Set(questions
        .filter(question => question.querySelector('input[type="radio"]:checked'))
        .map(question => question.dataset.question));

    function toast(message) {
        if (!toastNode) return;
        toastNode.textContent = message;
        toastNode.hidden = false;
        window.clearTimeout(toastTimer);
        toastTimer = window.setTimeout(() => { toastNode.hidden = true; }, 2800);
    }
    function notify(message) {
        const now = Date.now();
        if (now - lastNotice < 1300) return;
        lastNotice = now;
        toast(message);
    }
    function setSaveState(message) { setText('quizSaveState', message); }

    /* ----- Palette, marking and progress ----- */
    function paintMark(id) {
        const on = marked.has(id);
        const button = markButtons.get(id);
        if (button) {
            button.setAttribute('aria-pressed', String(on));
            button.classList.toggle('is-on', on);
            const label = button.querySelector('.quiz-mark-text');
            if (label) label.textContent = on ? 'Đã đánh dấu' : 'Đánh dấu';
        }
        const chip = chips.get(id);
        if (chip) chip.classList.toggle('is-marked', on);
    }

    function syncProgress() {
        const answered = answeredSet();
        const percent = total ? Math.round(answered.size * 100 / total) : 0;
        questions.forEach(question => {
            const id = question.dataset.question;
            const isAnswered = answered.has(id);
            question.classList.toggle('is-answered', isAnswered);
            question.classList.toggle('is-marked', marked.has(id));
            const state = question.querySelector('[data-state-label]');
            if (state) state.textContent = isAnswered ? 'Đã chọn đáp án' : 'Chưa chọn đáp án';
            question.querySelectorAll('.quiz-option').forEach(option => {
                option.classList.toggle('is-checked', Boolean(option.querySelector('input:checked')));
            });
            const chip = chips.get(id);
            if (chip) {
                chip.classList.toggle('is-answered', isAnswered);
                chip.classList.toggle('is-marked', marked.has(id));
            }
        });
        setText('answeredCount', answered.size);
        setText('answeredCountInline', answered.size);
        setText('unansweredCount', total - answered.size);
        setText('markedCount', marked.size);
        setText('quizProgressValue', percent);
        setText('paletteToggleCount', answered.size + '/' + total);
        const bar = byId('quizProgressBar');
        if (bar) bar.style.width = percent + '%';
        const paletteBar = byId('quizPaletteProgressBar');
        if (paletteBar) paletteBar.style.width = percent + '%';
        const track = byId('quizProgressTrack');
        if (track) track.setAttribute('aria-valuenow', String(percent));
    }

    function setCurrent(id) {
        if (id === currentId) return;
        const previous = chips.get(currentId);
        if (previous) previous.classList.remove('is-current');
        currentId = id;
        const chip = chips.get(id);
        if (chip) chip.classList.add('is-current');
    }

    function goToQuestion(id) {
        const question = byId('question-' + id);
        if (!question) return;
        setCurrent(id);
        question.scrollIntoView({behavior: 'smooth', block: 'start'});
        question.focus({preventScroll: true});
        closePalette();
    }

    function paletteIsDialog() { return window.matchMedia('(max-width: 1099px)').matches; }

    function openPalette() {
        document.body.classList.add('quiz-palette-open');
        if (paletteToggle) paletteToggle.setAttribute('aria-expanded', 'true');
        if (paletteBackdrop) paletteBackdrop.hidden = false;
        if (palettePanel && paletteIsDialog()) {
            palettePanel.setAttribute('role', 'dialog');
            palettePanel.setAttribute('aria-modal', 'true');
        }
        const first = chips.values().next().value;
        window.requestAnimationFrame(() => { if (first) first.focus(); });
    }

    function closePalette() {
        document.body.classList.remove('quiz-palette-open');
        if (paletteToggle) paletteToggle.setAttribute('aria-expanded', 'false');
        if (paletteBackdrop) paletteBackdrop.hidden = true;
        if (palettePanel) {
            palettePanel.removeAttribute('role');
            palettePanel.removeAttribute('aria-modal');
        }
    }

    if (paletteToggle) paletteToggle.addEventListener('click', () => {
        if (document.body.classList.contains('quiz-palette-open')) closePalette(); else openPalette();
    });
    const paletteClose = byId('paletteClose');
    if (paletteClose) paletteClose.addEventListener('click', closePalette);
    if (paletteBackdrop) paletteBackdrop.addEventListener('click', closePalette);

    chips.forEach((chip, id) => chip.addEventListener('click', () => goToQuestion(id)));

    const firstUnanswered = byId('paletteFirstUnanswered');
    if (firstUnanswered) firstUnanswered.addEventListener('click', () => {
        const answered = answeredSet();
        const next = questions.find(question => !answered.has(question.dataset.question));
        if (next) goToQuestion(next.dataset.question);
        else toast('Bạn đã chọn đáp án cho tất cả câu hỏi.');
    });

    const firstMarked = byId('paletteFirstMarked');
    if (firstMarked) firstMarked.addEventListener('click', () => {
        const next = questions.find(question => marked.has(question.dataset.question));
        if (next) goToQuestion(next.dataset.question);
        else toast('Chưa có câu nào được đánh dấu. Dùng nút Đánh dấu ở mỗi câu.');
    });

    markButtons.forEach((button, id) => button.addEventListener('click', () => {
        if (marked.has(id)) marked.delete(id); else marked.add(id);
        persistMarks();
        paintMark(id);
        syncProgress();
        const number = questions.findIndex(question => question.dataset.question === id) + 1;
        toast((marked.has(id) ? 'Đã đánh dấu' : 'Đã bỏ đánh dấu') + ' câu ' + number + '.');
    }));

    if ('IntersectionObserver' in window) {
        const observer = new IntersectionObserver(entries => {
            entries.forEach(entry => { if (entry.isIntersecting) setCurrent(entry.target.dataset.question); });
        }, {rootMargin: '-42% 0px -52% 0px', threshold: 0});
        questions.forEach(question => observer.observe(question));
    }

    /* ----- Countdown (the server deadline is authoritative) ----- */
    const clockText = seconds => {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const rest = seconds % 60;
        const pad = value => String(value).padStart(2, '0');
        return (hours > 0 ? hours + ':' + pad(minutes) : pad(minutes)) + ':' + pad(rest);
    };

    function tickClock() {
        if (!Number.isFinite(deadlineAt)) { if (timerNode) timerNode.textContent = '—'; return; }
        const seconds = Math.max(0, Math.round((deadlineAt - Date.now()) / 1000));
        if (timerNode) {
            timerNode.textContent = clockText(seconds);
            const clock = timerNode.closest('.quiz-clock');
            if (clock) clock.classList.toggle('is-urgent', seconds <= 60);
        }
        if (seconds === 0 && !expired && !submitting) {
            expired = true;
            collectAndExit('timeout');
        }
    }

    /* ----- Autosave ----- */
    let saving = Promise.resolve();

    function flushSave(answers = selectedAnswers()) {
        window.clearTimeout(saveTimer);
        saving = saving.catch(() => {}).then(async () => {
            setSaveState('Đang lưu câu trả lời…');
            const result = await mutate(() => api('/api/v1/quiz-attempts/' + attemptId + '/answers', {method: 'PUT', body: {answers}}));
            if (result && result.status === 'GRADED') {
                expired = true;
                tracking = false;
                collectAndExit('timeout');
                return;
            }
            lastSaved = new Date();
            setSaveState('Đã lưu lúc ' + lastSaved.toLocaleTimeString('vi-VN', {hour: '2-digit', minute: '2-digit'}) + '.');
        }).catch(error => {
            setSaveState(error.message + ' Câu trả lời mới có thể chưa được lưu.');
        });
        return saving;
    }

    form.addEventListener('change', event => {
        if (!event.target.matches('input[type="radio"]')) return;
        syncProgress();
        window.clearTimeout(saveTimer);
        setSaveState('Đang lưu câu trả lời…');
        saveTimer = window.setTimeout(flushSave, 400);
    });

    form.addEventListener('submit', event => {
        event.preventDefault();
        collectAndExit('manual');
    });

    /* ----- Anti copy / paste / devtools shortcuts ----- */
    const blockedEvents = ['copy', 'cut', 'paste', 'contextmenu', 'dragstart', 'selectstart'];
    blockedEvents.forEach(type => questionsArea.addEventListener(type, event => {
        if (type === 'selectstart' && event.target.closest('input, textarea')) return;
        event.preventDefault();
        notify({
            copy: 'Không thể sao chép nội dung bài làm.',
            cut: 'Không thể cắt nội dung bài làm.',
            paste: 'Không thể dán nội dung vào bài làm.',
            contextmenu: 'Chuột phải bị khóa trong lúc làm bài.',
            dragstart: 'Không thể kéo nội dung bài làm.',
            selectstart: 'Không thể bôi đen nội dung bài làm.'
        }[type] || 'Thao tác này bị khóa trong lúc làm bài.');
    }));

    document.addEventListener('keydown', event => {
        if (submitting || expired) return;
        const key = event.key.toLowerCase();
        const modifier = event.ctrlKey || event.metaKey;
        const target = event.target;
        const typing = target instanceof HTMLElement && (target.isContentEditable || target.matches('textarea, input[type="text"], input[type="search"], input[type="email"], input[type="password"]'));
        if (event.key === 'F12' || (modifier && event.shiftKey && ['i', 'j', 'c', 'k'].includes(key)) || (modifier && ['u', 's', 'p'].includes(key))) {
            event.preventDefault();
            notify('Phím tắt này bị khóa trong lúc làm bài.');
            return;
        }
        if (modifier && ['c', 'x', 'v', 'a'].includes(key) && !typing) {
            event.preventDefault();
            notify('Sao chép / dán bị khóa trong lúc làm bài.');
        }
    });
    document.addEventListener('keyup', event => {
        if (event.key === 'PrintScreen') notify('Chụp màn hình bài làm không được khuyến khích trong giờ kiểm tra.');
    });

    /* ----- Tab-out tracking (the server records events for TEST attempts only) ----- */
    const tabPath = '/api/v1/quiz-attempts/' + attemptId + '/tab-events';
    const tabKey = 'edulab-quiz-tabs-' + attemptId;
    let tracking = isTest;
    let tabCount = 0;
    let pending = [];
    let flushing = null;
    try {
        const stored = JSON.parse(window.sessionStorage.getItem(tabKey) || '[]');
        if (Array.isArray(stored)) pending = stored.filter(value => typeof value === 'string').slice(0, 50);
    } catch (error) { pending = []; }

    const persistTabs = () => { try { window.sessionStorage.setItem(tabKey, JSON.stringify(pending)); } catch (error) { /* ignore */ } };

    function informTabs() {
        if (!warning) return;
        const queued = pending.length ? ' · ' + pending.length + ' sự kiện đang chờ gửi.' : (tracking ? ' trên máy chủ.' : '.');
        warning.textContent = 'Đã ghi nhận ' + tabCount + ' lần' + queued;
    }

    function newEventId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, character => {
            const random = Math.random() * 16 | 0;
            return (character === 'x' ? random : (random & 0x3) | 0x8).toString(16);
        });
    }

    function flushTabs() {
        if (flushing) return flushing;
        if (!tracking || !pending.length) return Promise.resolve();
        flushing = (async () => {
            for (const eventId of [...pending]) {
                try {
                    const result = await mutate(() => api(tabPath, {method: 'POST', body: {eventId}, keepalive: true}));
                    tabCount = Math.max(tabCount, Number(result && result.count) || 0);
                    pending = pending.filter(value => value !== eventId);
                    persistTabs();
                    informTabs();
                } catch (error) { informTabs(); break; }
            }
        })().finally(() => { flushing = null; });
        return flushing;
    }

    function leave() {
        document.documentElement.classList.add('quiz-away');
        if (!tracking || submitting || expired) return;
        if (pending.length >= 50) { if (warning) warning.textContent = 'Có quá nhiều sự kiện chưa gửi. Kiểm tra kết nối mạng.'; return; }
        pending.push(newEventId());
        persistTabs();
        tabCount += 1;
        informTabs();
        flushTabs();
    }

    function showGuard() {
        if (!guard) return;
        guard.hidden = false;
        document.body.classList.add('quiz-guard-open');
        setText('quizGuardTotal', 'Tổng số lần rời tab đã ghi nhận: ' + tabCount + '.');
        let left = 3;
        setText('quizGuardCount', left);
        window.clearInterval(guardTimer);
        const resume = byId('quizGuardResume');
        window.requestAnimationFrame(() => { if (resume) resume.focus(); });
        guardTimer = window.setInterval(() => {
            left -= 1;
            setText('quizGuardCount', Math.max(left, 0));
            if (left <= 0) hideGuard();
        }, 1000);
    }

    function hideGuard() {
        window.clearInterval(guardTimer);
        if (guard) guard.hidden = true;
        document.body.classList.remove('quiz-guard-open');
    }

    const guardResume = byId('quizGuardResume');
    if (guardResume) guardResume.addEventListener('click', hideGuard);

    function returned() {
        document.documentElement.classList.remove('quiz-away');
        if (submitting || expired) return;
        informTabs();
        flushTabs();
        if (isTest) showGuard();
    }

    document.addEventListener('visibilitychange', () => {
        if (document.hidden) leave(); else returned();
    });
    window.addEventListener('pagehide', leave);
    window.addEventListener('online', () => flushTabs());


    /* ----- Submit flow with a short simulated grading screen ----- */
    const reviewUrl = () => context() + '/quizzes/attempts/' + attemptId + '?review=1';

    function setStep(index) {
        loadingSteps.forEach((step, position) => {
            step.classList.toggle('is-active', position === index);
            step.classList.toggle('is-done', position < index);
        });
    }

    function showLoading(message) {
        if (!loading) return;
        loading.hidden = false;
        document.body.classList.add('quiz-submitting');
        if (loadingMessage) loadingMessage.textContent = message;
        if (loadingBar) loadingBar.style.width = '8%';
        setStep(0);
    }

    function scheduleLoading(totalMs) {
        loadingTimers.forEach(window.clearTimeout);
        loadingTimers = [];
        [[totalMs * 0.30, '38%', 1], [totalMs * 0.62, '72%', 2], [totalMs * 0.88, '92%', 2]]
            .forEach(([delay, width, step]) => loadingTimers.push(window.setTimeout(() => {
                if (loadingBar) loadingBar.style.width = width;
                setStep(step);
            }, delay)));
    }

    function hideLoading() {
        loadingTimers.forEach(window.clearTimeout);
        loadingTimers = [];
        if (loading) loading.hidden = true;
        document.body.classList.remove('quiz-submitting');
    }

    async function collectAndExit(reason) {
        if (submitting) return;
        const unanswered = questions.filter(question => !question.querySelector('input[type="radio"]:checked')).length;
        if (reason === 'manual' && unanswered > 0 && !window.confirm('Bạn còn ' + unanswered + ' câu chưa trả lời. Vẫn nộp bài?')) return;
        submitting = true;
        tracking = false;
        hideGuard();
        closePalette();
        window.removeEventListener('beforeunload', beforeUnload);
        const answers = selectedAnswers();
        form.inert = true;
        form.classList.add('is-submitting');
        const minimum = isTest ? 2400 : 1800;
        const started = Date.now();
        showLoading(reason === 'timeout' ? 'Hết giờ làm bài — đang thu câu trả lời đã lưu…' : 'Đang khóa bài làm và thu câu trả lời…');
        scheduleLoading(minimum);
        try {
            await flushSave(answers).catch(() => {});
            await flushTabs().catch(() => {});
            await mutate(() => api('/api/v1/quiz-attempts/' + attemptId + '/submit', {method: 'POST', body: {answers}}));
            await wait(Math.max(0, minimum - (Date.now() - started)));
            if (loadingBar) loadingBar.style.width = '100%';
            setStep(3);
            await wait(280);
            hideLoading();
            window.location.assign(reviewUrl());
        } catch (error) {
            let graded = false;
            try {
                const state = await api('/api/v1/quiz-attempts/' + attemptId);
                graded = Boolean(state && state.status === 'GRADED');
            } catch (probeError) { graded = false; }
            if (graded) { hideLoading(); window.location.assign(reviewUrl()); return; }
            hideLoading();
            submitting = false;
            tracking = isTest && !expired;
            form.inert = false;
            form.classList.remove('is-submitting');
            syncProgress();
            setSaveState(error.message + ' Bài làm chưa được nộp. Thử lại hoặc tải lại trang.');
            toast(error.message);
            window.addEventListener('beforeunload', beforeUnload);
        }
    }

    function beforeUnload(event) {
        if (submitting || expired) return;
        event.preventDefault();
        event.returnValue = 'Bài làm chưa được nộp. Bạn có chắc muốn rời trang?';
        return event.returnValue;
    }
    window.addEventListener('beforeunload', beforeUnload);

    const exitLink = byId('quizExit');
    if (exitLink) exitLink.addEventListener('click', event => {
        if (!window.confirm('Thoát khỏi bài làm? Câu trả lời đã lưu vẫn còn và bạn có thể tiếp tục sau.')) { event.preventDefault(); return; }
        tracking = false;
        window.removeEventListener('beforeunload', beforeUnload);
    });

    /* ----- Initial paint ----- */
    questions.forEach(question => { if (marked.has(question.dataset.question)) paintMark(question.dataset.question); });
    syncProgress();
    tickClock();
    window.setInterval(tickClock, 1000);
    setSaveState('Chọn đáp án để bắt đầu. Câu trả lời tự lưu.');

    if (tracking) {
        api(tabPath).then(result => {
            tabCount = Math.max(tabCount, Number(result && result.count) || 0);
            informTabs();
            flushTabs();
        }).catch(() => { if (warning) warning.textContent = 'Chưa tải được số lần rời tab. Kiểm tra kết nối.'; });
    }
}

/* ===== Review mode ===== */
function initReview() {
    const filter = byId('questionFilter');
    if (filter) {
        const empty = byId('questionFilterEmpty');
        filter.addEventListener('change', () => {
            let shown = 0;
            document.querySelectorAll('.assessment-question').forEach(question => {
                question.hidden = filter.value !== 'all' && question.dataset.outcome !== filter.value;
                if (!question.hidden) shown += 1;
            });
            if (empty) empty.hidden = shown !== 0;
        });
    }

    const form = byId('quizForm');
    const summaryButton = byId('retryAiReview');
    if (summaryButton && form) {
        const load = () => showAi(summaryButton, byId('aiReviewText'), '/api/v1/quiz-attempts/' + form.dataset.id + '/ai-review');
        summaryButton.addEventListener('click', load);
        load();
    }

    document.querySelectorAll('.askQuestionAi').forEach(button => button.addEventListener('click', () => {
        if (!form) return;
        showAi(button, byId('ai-question-' + button.dataset.question), '/api/v1/quiz-attempts/' + form.dataset.id + '/questions/' + button.dataset.question + '/ai-explanation');
    }));
}

async function showAi(button, target, path) {
    if (button.disabled || !target) return;
    button.disabled = true;
    target.textContent = 'Đang hỏi AI…';
    try {
        const result = await mutate(() => api(path, {method: 'POST'}));
        target.textContent = (result.sample ? '[AI mẫu] ' : '') + result.text;
    } catch (error) {
        target.textContent = error.message + ' Bài làm và điểm vẫn được lưu. Bạn có thể hỏi giáo viên hoặc thử lại sau.';
    } finally {
        button.disabled = false;
    }
}
})();

