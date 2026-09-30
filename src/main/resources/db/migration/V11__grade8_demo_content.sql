-- Correct the introductory biology sample to a grade-8 body/health topic.
UPDATE lessons SET title='Hệ tiêu hóa và dinh dưỡng ở người',slug='he-tieu-hoa-va-dinh-duong',
    summary='Tìm hiểu quá trình tiêu hóa và lựa chọn chế độ ăn hợp lí.' WHERE slug='te-bao-don-vi-cua-su-song';
UPDATE lesson_versions v JOIN lessons l ON l.id=v.lesson_id
SET v.objectives='Nêu được chức năng của hệ tiêu hóa; phân biệt tiêu hóa và hấp thụ; vận dụng kiến thức để chăm sóc sức khỏe.',
    v.content_html='<h2>Hệ tiêu hóa ở người</h2><p>Thức ăn đi qua miệng, thực quản, dạ dày, ruột non và ruột già. Các tuyến tiêu hóa tiết dịch góp phần phân giải thức ăn thành chất đơn giản.</p><h3>Tiêu hóa và hấp thụ</h3><p>Tiêu hóa gồm biến đổi cơ học và hóa học. Ruột non là nơi chủ yếu hấp thụ chất dinh dưỡng vào máu và bạch huyết. Ruột già tiếp tục hấp thụ nước và tạo phân.</p><h3>Dinh dưỡng hợp lí</h3><p>Ăn đa dạng thực phẩm, đủ năng lượng theo nhu cầu, uống đủ nước, giữ vệ sinh thực phẩm và răng miệng. Không tự dùng thuốc hoặc thực hiện chế độ ăn cực đoan.</p><h3>Tự kiểm tra</h3><p>Vì sao cần nhai kĩ? Nhai làm nhỏ thức ăn, trộn thức ăn với nước bọt và hỗ trợ các bước tiêu hóa tiếp theo.</p>'
WHERE l.slug='he-tieu-hoa-va-dinh-duong';
UPDATE lesson_versions v JOIN lessons l ON l.id=v.lesson_id
SET v.content_html='<h2>Áp suất</h2><p>Áp lực là lực ép có phương vuông góc với mặt bị ép. Áp suất đặc trưng cho tác dụng của áp lực trên một đơn vị diện tích bị ép.</p><p>Công thức: p = F/S, với p đo bằng pascal (Pa), F là áp lực (N), S là diện tích bị ép (m²).</p><h3>Ví dụ</h3><p>Một áp lực 100 N tác dụng lên diện tích 0,02 m² tạo áp suất 5000 Pa. Với cùng áp lực, giảm diện tích làm tăng áp suất.</p><h3>Liên hệ đời sống</h3><p>Mũi đinh nhọn tạo áp suất lớn. Giày trượt tuyết có bản rộng làm giảm áp suất lên tuyết.</p>'
WHERE l.slug='luc-va-ap-suat';

INSERT INTO questions(bank_id,content,explanation,difficulty)
SELECT b.id,x.content,x.explanation,'EASY' FROM (SELECT MIN(id) id FROM question_banks) b CROSS JOIN (
    SELECT 'Trong phản ứng hóa học, chất mới tạo thành gọi là gì?' content,'Chất mới tạo thành được gọi là sản phẩm.' explanation
    UNION ALL SELECT 'Theo định luật bảo toàn khối lượng, tổng khối lượng sản phẩm bằng gì?','Trong hệ kín, tổng khối lượng sản phẩm bằng tổng khối lượng các chất tham gia.'
    UNION ALL SELECT 'Hiện tượng nào là biến đổi vật lí?','Nước đá tan chỉ thay đổi trạng thái, không tạo chất mới.'
    UNION ALL SELECT 'Công thức tính áp suất là gì?','Áp suất bằng áp lực chia diện tích bị ép: p = F/S.'
    UNION ALL SELECT 'Đơn vị SI của áp suất là gì?','Đơn vị SI là pascal, kí hiệu Pa; 1 Pa = 1 N/m².'
    UNION ALL SELECT 'Giữ nguyên áp lực, tăng diện tích bị ép làm áp suất thế nào?','Theo p = F/S, tăng S khi F không đổi làm p giảm.'
    UNION ALL SELECT 'Nơi hấp thụ phần lớn chất dinh dưỡng là cơ quan nào?','Ruột non có cấu tạo thích nghi để hấp thụ phần lớn chất dinh dưỡng.'
    UNION ALL SELECT 'Nhai kĩ giúp tiêu hóa như thế nào?','Nhai làm nhỏ thức ăn và trộn với nước bọt, hỗ trợ tiêu hóa.'
    UNION ALL SELECT 'Thói quen nào hỗ trợ sức khỏe hệ tiêu hóa?','Ăn đa dạng, giữ vệ sinh thực phẩm và răng miệng hỗ trợ hệ tiêu hóa.'
) x;

INSERT INTO question_options(question_id,option_text,is_correct,display_order)
SELECT q.id,x.answer,TRUE,1 FROM questions q JOIN (
    SELECT 'Trong phản ứng hóa học, chất mới tạo thành gọi là gì?' content,'Sản phẩm' answer
    UNION ALL SELECT 'Theo định luật bảo toàn khối lượng, tổng khối lượng sản phẩm bằng gì?','Tổng khối lượng các chất tham gia'
    UNION ALL SELECT 'Hiện tượng nào là biến đổi vật lí?','Nước đá tan'
    UNION ALL SELECT 'Công thức tính áp suất là gì?','p = F/S'
    UNION ALL SELECT 'Đơn vị SI của áp suất là gì?','Pa'
    UNION ALL SELECT 'Giữ nguyên áp lực, tăng diện tích bị ép làm áp suất thế nào?','Giảm'
    UNION ALL SELECT 'Nơi hấp thụ phần lớn chất dinh dưỡng là cơ quan nào?','Ruột non'
    UNION ALL SELECT 'Nhai kĩ giúp tiêu hóa như thế nào?','Làm nhỏ thức ăn và trộn với nước bọt'
    UNION ALL SELECT 'Thói quen nào hỗ trợ sức khỏe hệ tiêu hóa?','Ăn đa dạng và giữ vệ sinh thực phẩm'
) x ON q.content=x.content;
INSERT INTO question_options(question_id,option_text,is_correct,display_order)
SELECT q.id,x.wrong,FALSE,2 FROM questions q JOIN (
    SELECT 'Trong phản ứng hóa học, chất mới tạo thành gọi là gì?' content,'Chất tham gia' wrong
    UNION ALL SELECT 'Theo định luật bảo toàn khối lượng, tổng khối lượng sản phẩm bằng gì?','Luôn bằng không'
    UNION ALL SELECT 'Hiện tượng nào là biến đổi vật lí?','Sắt bị gỉ'
    UNION ALL SELECT 'Công thức tính áp suất là gì?','p = F × S'
    UNION ALL SELECT 'Đơn vị SI của áp suất là gì?','kg'
    UNION ALL SELECT 'Giữ nguyên áp lực, tăng diện tích bị ép làm áp suất thế nào?','Tăng'
    UNION ALL SELECT 'Nơi hấp thụ phần lớn chất dinh dưỡng là cơ quan nào?','Thực quản'
    UNION ALL SELECT 'Nhai kĩ giúp tiêu hóa như thế nào?','Làm thức ăn đi thẳng vào máu'
    UNION ALL SELECT 'Thói quen nào hỗ trợ sức khỏe hệ tiêu hóa?','Chỉ ăn một loại thực phẩm'
) x ON q.content=x.content;
INSERT INTO question_options(question_id,option_text,is_correct,display_order)
SELECT q.id,'Không có phương án nào ở trên',FALSE,3 FROM questions q
WHERE q.content IN ('Trong phản ứng hóa học, chất mới tạo thành gọi là gì?',
'Theo định luật bảo toàn khối lượng, tổng khối lượng sản phẩm bằng gì?','Hiện tượng nào là biến đổi vật lí?',
'Công thức tính áp suất là gì?','Đơn vị SI của áp suất là gì?',
'Giữ nguyên áp lực, tăng diện tích bị ép làm áp suất thế nào?',
'Nơi hấp thụ phần lớn chất dinh dưỡng là cơ quan nào?','Nhai kĩ giúp tiêu hóa như thế nào?',
'Thói quen nào hỗ trợ sức khỏe hệ tiêu hóa?');

INSERT INTO quizzes(title,lesson_id,duration_minutes,max_attempts,published,created_by)
SELECT CONCAT('Quiz: ',title),id,10,5,TRUE,(SELECT id FROM users WHERE username='giaovien')
FROM lessons WHERE slug IN ('luc-va-ap-suat','he-tieu-hoa-va-dinh-duong');
INSERT INTO quiz_items(quiz_id,question_id,display_order,points)
SELECT z.id,q.id,q.id,1 FROM quizzes z JOIN lessons l ON l.id=z.lesson_id CROSS JOIN questions q
WHERE (l.slug='phan-ung-hoa-hoc-quanh-ta' AND q.content IN ('Trong phản ứng hóa học, chất mới tạo thành gọi là gì?','Theo định luật bảo toàn khối lượng, tổng khối lượng sản phẩm bằng gì?','Hiện tượng nào là biến đổi vật lí?'))
OR (l.slug='luc-va-ap-suat' AND q.content IN ('Công thức tính áp suất là gì?','Đơn vị SI của áp suất là gì?','Giữ nguyên áp lực, tăng diện tích bị ép làm áp suất thế nào?'))
OR (l.slug='he-tieu-hoa-va-dinh-duong' AND q.content IN ('Nơi hấp thụ phần lớn chất dinh dưỡng là cơ quan nào?','Nhai kĩ giúp tiêu hóa như thế nào?','Thói quen nào hỗ trợ sức khỏe hệ tiêu hóa?'));
UPDATE quizzes SET max_attempts=5 WHERE title='Quiz: Phản ứng hóa học quanh ta';