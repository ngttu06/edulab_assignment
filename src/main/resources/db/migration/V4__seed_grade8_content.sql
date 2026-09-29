-- Demo password for all local accounts: ChangeMe123!
-- BCrypt hash generated for the demo password.
INSERT INTO users (username, email, password_hash, full_name, role) VALUES
('admin', 'admin@edulab.local', '$2y$10$JBMcsVrzfuorYdoRnb863uRpbjASC7Le1YpJseW2YkIq6UxGGriqC', 'Quản trị viên EduLab', 'ADMIN'),
('giaovien', 'giaovien@edulab.local', '$2y$10$JBMcsVrzfuorYdoRnb863uRpbjASC7Le1YpJseW2YkIq6UxGGriqC', 'Nguyễn Minh Anh', 'TEACHER'),
('hocsinh', 'hocsinh@edulab.local', '$2y$10$JBMcsVrzfuorYdoRnb863uRpbjASC7Le1YpJseW2YkIq6UxGGriqC', 'Trần Minh Khang', 'STUDENT'),
('phuhuynh', 'phuhuynh@edulab.local', '$2y$10$JBMcsVrzfuorYdoRnb863uRpbjASC7Le1YpJseW2YkIq6UxGGriqC', 'Trần Thị Lan', 'PARENT');

INSERT INTO subjects (name, grade_level, description)
VALUES ('Khoa học tự nhiên', 8, 'Chương trình Khoa học tự nhiên lớp 8 - học qua khám phá và thực hành.');

INSERT INTO topics (subject_id, name, display_order)
SELECT id, 'Chất và sự biến đổi của chất', 1 FROM subjects WHERE name = 'Khoa học tự nhiên' AND grade_level = 8;
INSERT INTO topics (subject_id, name, display_order)
SELECT id, 'Năng lượng và sự biến đổi', 2 FROM subjects WHERE name = 'Khoa học tự nhiên' AND grade_level = 8;
INSERT INTO topics (subject_id, name, display_order)
SELECT id, 'Vật sống và môi trường', 3 FROM subjects WHERE name = 'Khoa học tự nhiên' AND grade_level = 8;
INSERT INTO topics (subject_id, name, display_order)
SELECT id, 'Trái Đất và bầu trời', 4 FROM subjects WHERE name = 'Khoa học tự nhiên' AND grade_level = 8;

INSERT INTO lessons (topic_id, title, slug, summary, duration_minutes, status)
SELECT id, 'Phản ứng hóa học quanh ta', 'phan-ung-hoa-hoc-quanh-ta', 'Nhận biết dấu hiệu và mô tả phản ứng hóa học trong đời sống.', 35, 'PUBLISHED'
FROM topics WHERE name = 'Chất và sự biến đổi của chất';
INSERT INTO lessons (topic_id, title, slug, summary, duration_minutes, status)
SELECT id, 'Lực và áp suất', 'luc-va-ap-suat', 'Khám phá tác dụng của lực và áp suất trong thực tế.', 40, 'PUBLISHED'
FROM topics WHERE name = 'Năng lượng và sự biến đổi';
INSERT INTO lessons (topic_id, title, slug, summary, duration_minutes, status)
SELECT id, 'Tế bào - đơn vị của sự sống', 'te-bao-don-vi-cua-su-song', 'Tìm hiểu cấu tạo cơ bản và vai trò của tế bào.', 40, 'PUBLISHED'
FROM topics WHERE name = 'Vật sống và môi trường';

INSERT INTO lesson_versions (lesson_id, version_number, objectives, content_html, status, created_by)
SELECT id, 1, 'Nêu được dấu hiệu của phản ứng hóa học; phân biệt chất tham gia và sản phẩm.',
'<h2>Phản ứng hóa học quanh ta</h2><p>Phản ứng hóa học là quá trình biến đổi từ chất này thành chất khác. Trong phản ứng, chất ban đầu gọi là chất tham gia, chất mới tạo thành gọi là sản phẩm.</p><h3>Dấu hiệu nhận biết</h3><ul><li>Có khí thoát ra hoặc xuất hiện kết tủa.</li><li>Màu sắc, mùi hoặc nhiệt độ thay đổi.</li><li>Có thể phát sáng hoặc phát nhiệt.</li></ul><div class="alert alert-info">Ví dụ: Khi đốt nến, sáp nến phản ứng với oxygen tạo ra khí carbon dioxide và hơi nước.</div>', 'PUBLISHED', (SELECT id FROM users WHERE username = 'admin')
FROM lessons WHERE slug = 'phan-ung-hoa-hoc-quanh-ta';
INSERT INTO lesson_versions (lesson_id, version_number, objectives, content_html, status, created_by)
SELECT id, 1, 'Mô tả được tác dụng của lực và liên hệ áp suất với diện tích bị ép.',
'<h2>Lực và áp suất</h2><p>Lực có thể làm thay đổi chuyển động hoặc làm biến dạng vật. Áp suất phụ thuộc vào áp lực và diện tích bị ép.</p><p>Trong đời sống, mũi đinh nhọn để tăng áp suất; giày trượt tuyết có bản rộng để giảm áp suất.</p>', 'PUBLISHED', (SELECT id FROM users WHERE username = 'admin')
FROM lessons WHERE slug = 'luc-va-ap-suat';
INSERT INTO lesson_versions (lesson_id, version_number, objectives, content_html, status, created_by)
SELECT id, 1, 'Nhận biết tế bào là đơn vị cấu tạo và chức năng cơ bản của cơ thể sống.',
'<h2>Tế bào - đơn vị của sự sống</h2><p>Mọi cơ thể sống đều được cấu tạo từ tế bào. Tế bào thường gồm màng tế bào, tế bào chất và nhân hoặc vùng nhân.</p><p>Các tế bào phối hợp với nhau tạo thành mô, cơ quan và hệ cơ quan.</p>', 'PUBLISHED', (SELECT id FROM users WHERE username = 'admin')
FROM lessons WHERE slug = 'te-bao-don-vi-cua-su-song';

INSERT INTO question_banks (name, subject_id, created_by)
SELECT 'Ngân hàng KHTN 8 - Khởi động', id, (SELECT id FROM users WHERE username = 'admin') FROM subjects WHERE grade_level = 8;
INSERT INTO questions (bank_id, content, explanation, difficulty)
SELECT id, 'Dấu hiệu nào có thể cho thấy đã xảy ra phản ứng hóa học?', 'Sủi bọt khí, tạo kết tủa hoặc đổi màu có thể là dấu hiệu phản ứng.', 'EASY' FROM question_banks LIMIT 1;
INSERT INTO question_options (question_id, option_text, is_correct, display_order)
SELECT id, 'Có chất khí thoát ra', TRUE, 1 FROM questions LIMIT 1;
INSERT INTO question_options (question_id, option_text, is_correct, display_order)
SELECT id, 'Vật chỉ thay đổi vị trí', FALSE, 2 FROM questions LIMIT 1;
INSERT INTO question_options (question_id, option_text, is_correct, display_order)
SELECT id, 'Vật được đặt lên bàn', FALSE, 3 FROM questions LIMIT 1;

INSERT INTO quizzes (title, lesson_id, duration_minutes, max_attempts, published, created_by)
SELECT 'Quiz: Phản ứng hóa học quanh ta', id, 10, 3, TRUE, (SELECT id FROM users WHERE username = 'giaovien')
FROM lessons WHERE slug = 'phan-ung-hoa-hoc-quanh-ta';
INSERT INTO quiz_items (quiz_id, question_id, display_order, points)
SELECT (SELECT id FROM quizzes WHERE title = 'Quiz: Phản ứng hóa học quanh ta'), id, 1, 10 FROM questions LIMIT 1;
