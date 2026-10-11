-- Seed one YouTube lesson video for every numbered KHTN 8 Kết nối tri thức lesson.
-- URLs and durations were checked against the public YouTube result and metadata pages on 2026-10-11.
-- The course itself is unified by V22; this migration only attaches the curated video sources.
SET @khtn8_subject_id := (
    SELECT id FROM subjects WHERE name='Khoa học tự nhiên' AND grade_level=8 ORDER BY id LIMIT 1
);
SET @khtn8_course_id := (SELECT id FROM courses WHERE subject_id=@khtn8_subject_id ORDER BY id LIMIT 1);
SET @khtn8_admin_id := (SELECT id FROM users WHERE username='admin' ORDER BY id LIMIT 1);

DROP TEMPORARY TABLE IF EXISTS khtn8_video_seed;
CREATE TEMPORARY TABLE khtn8_video_seed (
    slug VARCHAR(220) NOT NULL PRIMARY KEY,
    youtube_id VARCHAR(11) NOT NULL,
    duration_seconds INT NOT NULL
);

INSERT INTO khtn8_video_seed(slug,youtube_id,duration_seconds) VALUES
('bai-1-phong-thi-nghiem','OyDeok1liok',228),
('bai-2-phan-ung-hoa-hoc','4uIUmVHR1mY',793),
('bai-3-mol-ti-khoi-chat-khi','eSX9xVQNhtg',1140),
('bai-4-dung-dich-nong-do','UB4OaK8DQ_0',1008),
('bai-5-bao-toan-khoi-luong-phuong-trinh','teALgIk7MRg',1412),
('bai-6-tinh-theo-phuong-trinh','gOmxQSvyZF0',1053),
('bai-7-toc-do-phan-ung-chat-xuc-tac','jMckAJDra7Q',1084),
('bai-8-acid','BiVaV_kiyWs',470),
('bai-9-base-thang-ph','l2wtiyGPPOo',910),
('bai-10-oxide','PVBd2seFqgY',930),
('bai-11-muoi','QCaaDYHADUU',1310),
('bai-12-phan-bon-hoa-hoc','ubmiXXwbIkU',470),
('bai-13-khoi-luong-rieng','5fakBxfbDGM',415),
('bai-14-thuc-hanh-xac-dinh-khoi-luong-rieng','TagXl7JINFA',569),
('bai-15-ap-suat-tren-mot-be-mat','pUTrPAyh4gk',904),
('bai-16-ap-suat-chat-long-khi-quyen','Cc6pN3lPMGo',1035),
('bai-17-luc-day-archimedes','vg7z9fG0nik',1313),
('bai-18-tac-dung-lam-quay-cua-luc','CVT9NEtNCls',1055),
('bai-19-don-bay-va-ung-dung','_Lbz0iP38gk',1364),
('bai-20-nhiem-dien-do-co-xat','1PGsU1Drh6M',1378),
('bai-21-dong-dien-nguon-dien','U7LkV4TYJL8',989),
('bai-22-mach-dien-don-gian','jpvJlXFOs7w',1099),
('bai-23-tac-dung-cua-dong-dien','4c5AwaUptmQ',1431),
('bai-24-cuong-do-dong-dien-hieu-dien-the','q7PayAYd_no',949),
('bai-25-thuc-hanh-do-cuong-do-hieu-dien-the','3mBVqlW8OSo',266),
('bai-26-nang-luong-nhiet-nhiet-nang','HEZt1Dwz7k0',1243),
('bai-27-thuc-hanh-joulemeter','pkj82U0wQYA',297),
('bai-28-su-truyen-nhiet','oQXNrbdHSpI',1628),
('bai-29-su-no-vi-nhiet','HVmmw4OayoE',1369),
('bai-30-khai-quat-co-the-nguoi','3bKo3peAjqE',993),
('bai-31-he-van-dong-o-nguoi','kd0bX-ceI-8',1395),
('bai-32-dinh-duong-tieu-hoa-o-nguoi','ebpDgpQq5RI',3227),
('bai-33-mau-he-tuan-hoan','gxgqhwK2tfo',1762),
('bai-34-he-ho-hap-o-nguoi','urYYF7UouRo',1052),
('bai-35-he-bai-tiet-o-nguoi','q-mBCMy7Szo',1414),
('bai-36-dieu-hoa-moi-truong-trong','LLLsDvB5CQ0',997),
('bai-37-he-than-kinh-giac-quan','s5mYE7uGJJI',649),
('bai-38-he-noi-tiet-o-nguoi','0ma2Ul8tG9U',1384),
('bai-39-da-dieu-hoa-than-nhiet','sPwOskutU18',1120),
('bai-40-sinh-san-o-nguoi','0Wzj6k7jkhQ',963),
('bai-41-moi-truong-nhan-to-sinh-thai','_bcEjtyowUs',1234),
('bai-42-quan-the-sinh-vat','DJ8W58BOZy8',1117),
('bai-43-quan-xa-sinh-vat','tmnmlDPfiYA',934),
('bai-44-he-sinh-thai','wL_7TfP7tTg',1319),
('bai-45-sinh-quyen','txyQJ4P2gBM',743),
('bai-46-can-bang-tu-nhien','3PrB9RuDYFY',1193),
('bai-47-bao-ve-moi-truong','nj76BICMKg0',1116);

-- Keep every seeded video inside the single merged KHTN 8 course.
INSERT INTO lesson_videos(lesson_id,revision,provider,source_url,youtube_id,duration_seconds,completion_threshold,created_by)
SELECT l.id, COALESCE((SELECT MAX(old_v.revision) FROM lesson_videos old_v WHERE old_v.lesson_id=l.id),0)+1,
       'YOUTUBE', CONCAT('https://www.youtube.com/watch?v=',s.youtube_id), s.youtube_id,
       s.duration_seconds, 95, @khtn8_admin_id
FROM lessons l
JOIN topics t ON t.id=l.topic_id
JOIN khtn8_video_seed s ON s.slug=l.slug
WHERE t.course_id=@khtn8_course_id;

-- Make the newly seeded revision the current video shown by the learning flow.
UPDATE lessons l
JOIN topics t ON t.id=l.topic_id
JOIN khtn8_video_seed s ON s.slug=l.slug
JOIN lesson_videos v ON v.lesson_id=l.id AND v.youtube_id=s.youtube_id
SET l.video_id=v.id
WHERE t.course_id=@khtn8_course_id
  AND v.revision=(SELECT MAX(new_v.revision) FROM lesson_videos new_v WHERE new_v.lesson_id=l.id);

DROP TEMPORARY TABLE khtn8_video_seed;
