-- Keep KHTN 8 lessons in textbook order regardless of legacy lesson ids.
ALTER TABLE lessons
    ADD COLUMN display_order INT NOT NULL DEFAULT 0,
    ADD INDEX idx_lessons_topic_display_order (topic_id, display_order);

UPDATE lessons l
JOIN topics t ON t.id=l.topic_id
JOIN courses c ON c.id=t.course_id
JOIN subjects s ON s.id=c.subject_id
SET l.display_order = CASE
    WHEN l.slug='bai-mo-dau-khtn-8' THEN 0
    ELSE CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(l.slug,'-',2),'-',-1) AS UNSIGNED)
END
WHERE s.name='Khoa học tự nhiên'
  AND s.grade_level=8
  AND l.slug LIKE 'bai-%';
