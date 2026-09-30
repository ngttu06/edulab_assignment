-- Additive migration: existing lesson/progress/attempt identifiers are preserved.
CREATE TABLE courses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    kind VARCHAR(20) NOT NULL DEFAULT 'FOUNDATION',
    teacher_id BIGINT NULL,
    theme VARCHAR(20) NOT NULL DEFAULT 'blue',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_course_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES users(id),
    CONSTRAINT uq_course_subject UNIQUE (id, subject_id),
    CONSTRAINT chk_course_kind CHECK (kind IN ('FOUNDATION','TOPIC','REVISION')),
    CONSTRAINT chk_course_theme CHECK (theme IN ('blue','purple','green','orange'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO courses(subject_id,title,summary)
SELECT id,CONCAT(name,' ',grade_level,' · Khám phá nền tảng'),
       'Học theo chủ đề, luyện tập và theo dõi tiến độ. Nội dung khởi động, chưa phải chương trình đầy đủ.' FROM subjects;
ALTER TABLE topics ADD COLUMN course_id BIGINT NULL;
UPDATE topics t JOIN courses c ON c.subject_id=t.subject_id SET t.course_id=c.id;
ALTER TABLE topics MODIFY course_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_topic_course_subject FOREIGN KEY (course_id,subject_id) REFERENCES courses(id,subject_id),
    ADD INDEX idx_topic_course_order(course_id,display_order);

-- Standalone legacy quizzes remain unclassified unless their lesson identifies a course.
ALTER TABLE quizzes ADD COLUMN course_id BIGINT NULL,
    ADD CONSTRAINT fk_quiz_course FOREIGN KEY(course_id) REFERENCES courses(id);
UPDATE quizzes q JOIN lessons l ON l.id=q.lesson_id JOIN topics t ON t.id=l.topic_id SET q.course_id=t.course_id;

INSERT INTO subjects(name,grade_level,description) VALUES
('Toán',8,'Tư duy toán học và vận dụng.'),
('Ngữ văn',8,'Đọc hiểu và diễn đạt.'),
('Tiếng Anh',8,'Ngôn ngữ trong cuộc sống.'),
('Toán',10,'Nền tảng toán học trung học phổ thông.'),
('Tiếng Anh',10,'Củng cố ngữ pháp và kỹ năng diễn đạt.');
INSERT INTO courses(subject_id,title,summary,theme)
SELECT s.id,CONCAT(s.name,' ',s.grade_level,' · Bắt đầu vững chắc'),
       'Khóa khởi động với bài học và câu hỏi mẫu. Học từng bước, hiểu từ gốc; chưa phải chương trình đầy đủ.',
       CASE s.name WHEN 'Toán' THEN 'purple' WHEN 'Ngữ văn' THEN 'orange' ELSE 'green' END
FROM subjects s WHERE NOT EXISTS(SELECT 1 FROM courses c WHERE c.subject_id=s.id);
INSERT INTO topics(subject_id,course_id,name,display_order)
SELECT c.subject_id,c.id,'Kiến thức khởi động',1 FROM courses c
WHERE NOT EXISTS(SELECT 1 FROM topics t WHERE t.course_id=c.id);

INSERT INTO lessons(topic_id,title,slug,summary,duration_minutes,status)
SELECT t.id,x.title,x.slug,x.summary,20,'PUBLISHED'
FROM topics t JOIN subjects s ON s.id=t.subject_id JOIN (
 SELECT 'Toán' subject_name,8 grade,'Hằng đẳng thức bình phương của một tổng' title,'toan-8-binh-phuong-tong' slug,'Hiểu và vận dụng công thức (a + b)².' summary
 UNION ALL SELECT 'Ngữ văn',8,'Luận điểm và lí lẽ trong văn nghị luận','ngu-van-8-luan-diem','Phân biệt điều cần khẳng định với lí lẽ và bằng chứng.'
 UNION ALL SELECT 'Tiếng Anh',8,'Present simple: thói quen hằng ngày','tieng-anh-8-present-simple','Sử dụng thì hiện tại đơn để nói về thói quen.'
 UNION ALL SELECT 'Toán',10,'Mệnh đề và giá trị đúng sai','toan-10-menh-de','Nhận biết mệnh đề và phủ định của một mệnh đề.'
 UNION ALL SELECT 'Tiếng Anh',10,'Present continuous: điều đang diễn ra','tieng-anh-10-present-continuous','Mô tả hành động đang diễn ra ở thời điểm nói.'
) x ON x.subject_name=s.name AND x.grade=s.grade_level WHERE t.name='Kiến thức khởi động';
INSERT INTO lesson_versions(lesson_id,version_number,objectives,content_html,status)
SELECT l.id,1,l.summary,x.html,'PUBLISHED' FROM lessons l JOIN (
 SELECT 'toan-8-binh-phuong-tong' slug,'<h2>Bình phương của một tổng</h2><p>(a + b)² = a² + 2ab + b². Công thức thu được bằng cách nhân (a + b) với chính nó và thu gọn các hạng tử.</p><h3>Ví dụ</h3><p>(x + 3)² = x² + 6x + 9. Chú ý hạng tử giữa là 2 × x × 3, không phải 3x.</p><h3>Tự luyện</h3><p>Tính 102² bằng (100 + 2)². Kết quả: 10000 + 400 + 4 = 10404.</p>' html
 UNION ALL SELECT 'ngu-van-8-luan-diem','<h2>Luận điểm, lí lẽ và bằng chứng</h2><p>Luận điểm là ý kiến người viết muốn khẳng định. Lí lẽ giải thích vì sao ý kiến đó hợp lí. Bằng chứng là sự việc, số liệu hoặc ví dụ cụ thể hỗ trợ lập luận.</p><h3>Ví dụ</h3><p>Luận điểm: Đọc sách giúp mở rộng hiểu biết. Lí lẽ: Sách giới thiệu kiến thức vượt ra ngoài trải nghiệm trực tiếp. Bằng chứng: Một cuốn sách lịch sử cụ thể giúp người đọc tìm hiểu một giai đoạn đã qua.</p><h3>Tự luyện</h3><p>Viết một luận điểm về việc bảo vệ môi trường, kèm một lí lẽ và một ví dụ có thể kiểm chứng. Không dùng số liệu không rõ nguồn.</p>'
 UNION ALL SELECT 'tieng-anh-8-present-simple','<h2>Present simple</h2><p>Dùng thì hiện tại đơn để diễn tả thói quen hoặc sự thật. Với I/you/we/they, dùng động từ nguyên mẫu; với he/she/it, thường thêm -s hoặc -es.</p><h3>Ví dụ</h3><p>I read every day. She reads every day. He does not play tennis. Do you like books?</p><h3>Tự luyện</h3><p>Điền từ: She ___ (go) to school every morning. Đáp án: goes. Trong câu phủ định có does not, động từ trở về nguyên mẫu.</p>'
 UNION ALL SELECT 'toan-10-menh-de','<h2>Mệnh đề</h2><p>Mệnh đề là câu khẳng định có giá trị đúng hoặc sai. Câu hỏi hoặc câu cảm thán không phải mệnh đề.</p><h3>Ví dụ</h3><p>“2 là số chẵn” là mệnh đề đúng. “3 lớn hơn 5” là mệnh đề sai. “Bạn có khỏe không?” không phải mệnh đề.</p><h3>Phủ định</h3><p>Phủ định của “2 là số chẵn” là “2 không phải số chẵn”. Mệnh đề và phủ định của nó có giá trị đúng sai trái ngược nhau.</p>'
 UNION ALL SELECT 'tieng-anh-10-present-continuous','<h2>Present continuous</h2><p>Cấu trúc: chủ ngữ + am/is/are + động từ thêm -ing. Thường dùng cho hành động đang diễn ra ở thời điểm nói.</p><h3>Ví dụ</h3><p>I am studying now. She is reading a book. They are playing football.</p><h3>Phân biệt</h3><p>“I study every day” nói về thói quen. “I am studying now” nói về hành động đang diễn ra.</p><h3>Tự luyện</h3><p>Điền từ: They ___ (read) now. Đáp án: are reading.</p>'
) x ON x.slug=l.slug;
INSERT INTO question_banks(name,subject_id)
SELECT CONCAT(s.name,' ',s.grade_level,' · Khởi động'),s.id FROM subjects s
WHERE NOT EXISTS(SELECT 1 FROM question_banks b WHERE b.subject_id=s.id);
INSERT INTO questions(bank_id,content,explanation,difficulty)
SELECT b.id,x.content,x.explanation,'EASY' FROM question_banks b JOIN subjects s ON s.id=b.subject_id JOIN (
 SELECT 'Toán' name,8 grade,'Khai triển (x + 2)² được kết quả nào?' content,'Áp dụng (a + b)² = a² + 2ab + b²: x² + 4x + 4.' explanation
 UNION ALL SELECT 'Ngữ văn',8,'Luận điểm trong văn nghị luận là gì?','Luận điểm là ý kiến người viết muốn khẳng định và làm sáng tỏ.'
 UNION ALL SELECT 'Tiếng Anh',8,'Chọn từ đúng: She ___ to school every morning.','Chủ ngữ she ở thì hiện tại đơn đi với goes.'
 UNION ALL SELECT 'Toán',10,'Câu nào là một mệnh đề?','“2 là số chẵn” là câu khẳng định có giá trị đúng.'
 UNION ALL SELECT 'Tiếng Anh',10,'Chọn từ đúng: They ___ reading now.','They đi với are trong cấu trúc hiện tại tiếp diễn.'
) x ON x.name=s.name AND x.grade=s.grade_level;
INSERT INTO question_options(question_id,option_text,is_correct,display_order)
SELECT q.id,x.answer,TRUE,1 FROM questions q JOIN (
 SELECT 'Khai triển (x + 2)² được kết quả nào?' content,'x² + 4x + 4' answer
 UNION ALL SELECT 'Luận điểm trong văn nghị luận là gì?','Ý kiến người viết muốn khẳng định'
 UNION ALL SELECT 'Chọn từ đúng: She ___ to school every morning.','goes'
 UNION ALL SELECT 'Câu nào là một mệnh đề?','2 là số chẵn'
 UNION ALL SELECT 'Chọn từ đúng: They ___ reading now.','are'
) x ON x.content=q.content;
INSERT INTO question_options(question_id,option_text,is_correct,display_order)
SELECT q.id,x.answer,FALSE,2 FROM questions q JOIN (
 SELECT 'Khai triển (x + 2)² được kết quả nào?' content,'x² + 4' answer
 UNION ALL SELECT 'Luận điểm trong văn nghị luận là gì?','Mọi câu hỏi người đọc đặt ra'
 UNION ALL SELECT 'Chọn từ đúng: She ___ to school every morning.','go'
 UNION ALL SELECT 'Câu nào là một mệnh đề?','Bạn có khỏe không?'
 UNION ALL SELECT 'Chọn từ đúng: They ___ reading now.','is'
) x ON x.content=q.content;
INSERT INTO quizzes(title,lesson_id,course_id,duration_minutes,max_attempts,published)
SELECT CONCAT('Tự luyện: ',l.title),l.id,t.course_id,5,10,TRUE FROM lessons l JOIN topics t ON t.id=l.topic_id
WHERE l.slug IN ('toan-8-binh-phuong-tong','ngu-van-8-luan-diem','tieng-anh-8-present-simple','toan-10-menh-de','tieng-anh-10-present-continuous');
INSERT INTO quiz_items(quiz_id,question_id,display_order,points)
SELECT z.id,q.id,1,1 FROM quizzes z JOIN courses c ON c.id=z.course_id
JOIN question_banks b ON b.subject_id=c.subject_id JOIN questions q ON q.bank_id=b.id
WHERE z.title LIKE 'Tự luyện: %';
INSERT INTO forum_categories(name,description) VALUES ('Hỏi bài đa môn','Trao đổi kiến thức Toán, Ngữ văn, Tiếng Anh và các môn học khác. Hãy ghi rõ môn và khối lớp.');