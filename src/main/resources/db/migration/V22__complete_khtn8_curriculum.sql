-- Complete KHTN 8 curriculum aligned with the Kết nối tri thức table of contents.
-- Source outline: Loigiaihay, SGK Khoa học tự nhiên 8 - Kết nối tri thức.
-- This migration keeps existing lesson identifiers and replaces demo placeholders.

SET @khtn8_subject_id := (
    SELECT id FROM subjects
    WHERE name = 'Khoa học tự nhiên' AND grade_level = 8
    ORDER BY id LIMIT 1
);
SET @khtn8_course_id := (
    SELECT id FROM courses
    WHERE subject_id = @khtn8_subject_id
    ORDER BY id LIMIT 1
);
SET @khtn8_admin_id := (SELECT id FROM users WHERE username = 'admin' ORDER BY id LIMIT 1);

-- Normalize the four original demo topics to the textbook chapters.
UPDATE topics SET name='Chương 1. Phản ứng hóa học', display_order=1
WHERE course_id=@khtn8_course_id AND name='Chất và sự biến đổi của chất';
UPDATE topics SET name='Chương 4. Tác dụng làm quay của lực', display_order=4
WHERE course_id=@khtn8_course_id AND name='Năng lượng và sự biến đổi';
UPDATE topics SET name='Chương 7. Sinh học cơ thể người', display_order=7
WHERE course_id=@khtn8_course_id AND name='Vật sống và môi trường';
UPDATE topics SET name='Chương 8. Sinh vật và môi trường', display_order=8
WHERE course_id=@khtn8_course_id AND name='Trái Đất và bầu trời';

INSERT INTO topics(subject_id, course_id, name, display_order)
SELECT @khtn8_subject_id,@khtn8_course_id,x.name,x.display_order
FROM (
    SELECT 'Mở đầu' name,0 display_order
    UNION ALL SELECT 'Chương 1. Phản ứng hóa học',1
    UNION ALL SELECT 'Chương 2. Một số hợp chất thông dụng',2
    UNION ALL SELECT 'Chương 3. Khối lượng riêng và áp suất',3
    UNION ALL SELECT 'Chương 4. Tác dụng làm quay của lực',4
    UNION ALL SELECT 'Chương 5. Điện',5
    UNION ALL SELECT 'Chương 6. Nhiệt',6
    UNION ALL SELECT 'Chương 7. Sinh học cơ thể người',7
    UNION ALL SELECT 'Chương 8. Sinh vật và môi trường',8
) x
WHERE NOT EXISTS (
    SELECT 1 FROM topics t
    WHERE t.course_id=@khtn8_course_id AND t.name=x.name
);

-- Keep the existing demo lesson ids while replacing their titles, slugs and placement.
UPDATE lessons l
JOIN topics t ON t.course_id=@khtn8_course_id AND t.name='Chương 1. Phản ứng hóa học'
SET l.topic_id=t.id, l.title='Bài 2 · Phản ứng hóa học',
    l.slug='bai-2-phan-ung-hoa-hoc',
    l.summary='Nhận biết biến đổi hóa học, chất tham gia, sản phẩm và năng lượng trong phản ứng.',
    l.duration_minutes=45, l.status='PUBLISHED', l.created_by=@khtn8_admin_id
WHERE l.slug='phan-ung-hoa-hoc-quanh-ta';
UPDATE lessons l
JOIN topics t ON t.course_id=@khtn8_course_id AND t.name='Chương 4. Tác dụng làm quay của lực'
SET l.topic_id=t.id, l.title='Bài 18 · Tác dụng làm quay của lực. Moment lực',
    l.slug='bai-18-tac-dung-lam-quay-cua-luc',
    l.summary='Nhận biết tác dụng làm quay của lực và tính moment lực trong tình huống đơn giản.',
    l.duration_minutes=40, l.status='PUBLISHED', l.created_by=@khtn8_admin_id
WHERE l.slug='luc-va-ap-suat';
UPDATE lessons l
JOIN topics t ON t.course_id=@khtn8_course_id AND t.name='Chương 7. Sinh học cơ thể người'
SET l.topic_id=t.id, l.title='Bài 32 · Dinh dưỡng và tiêu hóa ở người',
    l.slug='bai-32-dinh-duong-tieu-hoa-o-nguoi',
    l.summary='Mô tả quá trình tiêu hóa, hấp thụ và xây dựng chế độ dinh dưỡng hợp lí.',
    l.duration_minutes=45, l.status='PUBLISHED', l.created_by=@khtn8_admin_id
WHERE l.slug='he-tieu-hoa-va-dinh-duong';

UPDATE quizzes q JOIN lessons l ON l.id=q.lesson_id
SET q.title=CONCAT('Tự luyện: ',l.title)
WHERE l.slug IN ('bai-2-phan-ung-hoa-hoc','bai-18-tac-dung-lam-quay-cua-luc','bai-32-dinh-duong-tieu-hoa-o-nguoi');

DROP TEMPORARY TABLE IF EXISTS khtn8_lesson_seed;
CREATE TEMPORARY TABLE khtn8_lesson_seed (
    chapter_name VARCHAR(120) NOT NULL,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL PRIMARY KEY,
    summary VARCHAR(500) NOT NULL,
    duration_minutes INT NOT NULL,
    objectives TEXT NOT NULL,
    content_html MEDIUMTEXT NOT NULL
);

INSERT INTO khtn8_lesson_seed(chapter_name,title,slug,summary,duration_minutes,objectives,content_html) VALUES
('Mở đầu','Bài mở đầu · Khám phá Khoa học tự nhiên 8','bai-mo-dau-khtn-8','Làm quen với cách học, tư duy khoa học và quy tắc an toàn trong môn Khoa học tự nhiên 8.',25,'Nhận biết các lĩnh vực của Khoa học tự nhiên; mô tả được quy trình tìm hiểu tự nhiên; hình thành thói quen học tập và làm việc an toàn.','<h2>Khám phá Khoa học tự nhiên 8</h2>
<p>Khoa học tự nhiên tìm hiểu vật chất, năng lượng, sự sống và môi trường bằng quan sát, đặt câu hỏi, dự đoán, thí nghiệm và lập luận dựa trên bằng chứng.</p>
<h3>Quy trình tìm hiểu</h3><ol><li>Nêu vấn đề và câu hỏi nghiên cứu.</li><li>Đưa ra dự đoán có thể kiểm tra.</li><li>Lập phương án, thu thập và xử lí dữ liệu.</li><li>Rút ra kết luận, trao đổi và điều chỉnh dự đoán.</li></ol>
<h3>Thói quen học tập</h3><p>Đọc mục tiêu trước khi học, ghi lại đơn vị đo, trình bày kết quả trung thực và phân biệt rõ quan sát với suy luận. Khi làm thực hành, luôn tuân thủ hướng dẫn của giáo viên và giữ gìn thiết bị.</p>'),
('Mở đầu','Bài 1 · Sử dụng một số hóa chất, thiết bị cơ bản trong phòng thí nghiệm','bai-1-phong-thi-nghiem','Sử dụng đúng dụng cụ đo, hóa chất và thiết bị thí nghiệm; ưu tiên an toàn trong mọi hoạt động thực hành.',35,'Đọc được nhãn hóa chất; sử dụng cơ bản ống nghiệm, cốc, ống đong, cân và nhiệt kế; nêu được quy tắc xử lí sự cố.','<h2>An toàn phòng thí nghiệm</h2>
<p>Trước khi thực hành, đọc kĩ nhãn và kí hiệu cảnh báo, chuẩn bị kính bảo hộ, áo choàng và găng tay khi cần. Không nếm, ngửi trực tiếp hoặc tự ý trộn hóa chất.</p>
<h3>Thiết bị và phép đo</h3><ul><li>Ống đong dùng để đo thể tích chất lỏng; đọc đáy mặt khum ở tầm mắt.</li><li>Cân dùng để đo khối lượng; đặt cân trên mặt phẳng và chỉnh về số không.</li><li>Nhiệt kế phải có giới hạn đo phù hợp; không dùng nhiệt kế làm đũa khuấy.</li></ul>
<h3>Làm việc an toàn</h3><p>Khi đun, hướng miệng ống nghiệm ra xa người. Nếu hóa chất bắn vào da hoặc mắt, rửa ngay bằng nhiều nước và báo giáo viên. Chất thải được thu gom đúng nơi quy định, không đổ tùy tiện xuống bồn rửa.</p>'),
('Chương 1. Phản ứng hóa học','Bài 2 · Phản ứng hóa học','bai-2-phan-ung-hoa-hoc','Nhận biết biến đổi hóa học, chất tham gia, sản phẩm và năng lượng trong phản ứng.',45,'Phân biệt biến đổi vật lí và hóa học; nêu được khái niệm phản ứng hóa học; nhận biết dấu hiệu và mô tả phản ứng bằng sơ đồ chữ.','<h2>Phản ứng hóa học</h2>
<p>Biến đổi hóa học là quá trình tạo ra chất mới. Phản ứng hóa học là quá trình trong đó các chất ban đầu, gọi là chất tham gia, biến đổi thành chất mới, gọi là sản phẩm.</p>
<h3>Dấu hiệu</h3><ul><li>Xuất hiện chất khí hoặc kết tủa.</li><li>Đổi màu, phát sáng, tỏa nhiệt hoặc thu nhiệt.</li><li>Tính chất của chất sau phản ứng khác chất ban đầu.</li></ul>
<p>Ví dụ: nến cháy tạo khí carbon dioxide và hơi nước. Khi viết sơ đồ chữ, đặt chất tham gia ở bên trái mũi tên và sản phẩm ở bên phải.</p>
<h3>Năng lượng</h3><p>Phản ứng tỏa nhiệt giải phóng năng lượng ra môi trường; phản ứng thu nhiệt nhận năng lượng từ môi trường. Đốt nhiên liệu, hô hấp tế bào và quang hợp là những ví dụ quen thuộc.</p>'),
('Chương 1. Phản ứng hóa học','Bài 3 · Mol và tỉ khối chất khí','bai-3-mol-ti-khoi-chat-khi','Dùng mol, khối lượng mol, thể tích mol và tỉ khối để định lượng chất và so sánh khí.',40,'Nêu được ý nghĩa của mol và số Avogadro; tính được khối lượng, thể tích khí và tỉ khối từ công thức cơ bản.','<h2>Mol và khối lượng mol</h2>
<p>Mol là lượng chất chứa khoảng 6,022 × 10²³ hạt vi mô. Khối lượng mol M có đơn vị g/mol và bằng khối lượng của 1 mol chất.</p>
<h3>Công thức cần nhớ</h3><ul><li><strong>n = m/M</strong>, trong đó n là số mol, m là khối lượng và M là khối lượng mol.</li><li>Ở cùng điều kiện quy ước, <strong>V = nV<sub>m</sub></strong>; với khí ở 25 °C và 1 bar, V<sub>m</sub> xấp xỉ 24,79 L/mol.</li><li><strong>d<sub>A/B</sub> = M<sub>A</sub>/M<sub>B</sub></strong> là tỉ khối của khí A so với khí B.</li></ul>
<p>Khi giải bài, đổi các đại lượng về đúng đơn vị, xác định dữ kiện đã biết rồi chọn công thức phù hợp. Kết quả cần ghi kèm đơn vị và làm tròn hợp lí.</p>'),
('Chương 1. Phản ứng hóa học','Bài 4 · Dung dịch và nồng độ','bai-4-dung-dich-nong-do','Mô tả dung dịch, độ tan và tính nồng độ phần trăm, nồng độ mol.',45,'Phân biệt chất tan, dung môi và dung dịch; tính được độ tan, nồng độ phần trăm và nồng độ mol trong tình huống đơn giản.','<h2>Dung dịch và nồng độ</h2>
<p>Dung dịch là hỗn hợp đồng nhất gồm chất tan và dung môi. Dung dịch chưa bão hòa còn có thể hòa tan thêm chất tan; dung dịch bão hòa không thể hòa tan thêm ở nhiệt độ xác định.</p>
<h3>Độ tan</h3><p>Độ tan S cho biết số gam chất tan tối đa trong 100 g nước để tạo dung dịch bão hòa: <strong>S = m<sub>ct</sub>/m<sub>nước</sub> × 100</strong>.</p>
<h3>Nồng độ</h3><ul><li>Nồng độ phần trăm: <strong>C% = m<sub>ct</sub>/m<sub>dd</sub> × 100%</strong>, với m<sub>dd</sub> = m<sub>ct</sub> + m<sub>dm</sub>.</li><li>Nồng độ mol: <strong>C<sub>M</sub> = n/V</strong>, V tính bằng lít.</li></ul>
<p>Khi pha loãng, lượng chất tan không đổi nhưng thể tích dung dịch tăng nên nồng độ giảm. Luôn dùng dụng cụ sạch và ghi rõ nồng độ trên bình chứa.</p>'),
('Chương 1. Phản ứng hóa học','Bài 5 · Định luật bảo toàn khối lượng và phương trình hóa học','bai-5-bao-toan-khoi-luong-phuong-trinh','Vận dụng định luật bảo toàn khối lượng và lập phương trình hóa học.',50,'Phát biểu được định luật bảo toàn khối lượng; lập và cân bằng được phương trình hóa học ở mức độ cơ bản; đọc được tỉ lệ mol.','<h2>Bảo toàn khối lượng</h2>
<p>Trong một phản ứng hóa học xảy ra trong hệ kín, tổng khối lượng các chất tham gia bằng tổng khối lượng các sản phẩm. Nguyên tử không mất đi hay sinh ra, chỉ sắp xếp lại.</p>
<h3>Phương trình hóa học</h3><ol><li>Viết sơ đồ phản ứng bằng công thức hóa học.</li><li>Đếm số nguyên tử mỗi nguyên tố ở hai vế.</li><li>Đặt hệ số nguyên tối giản trước công thức để số nguyên tử bằng nhau.</li><li>Kiểm tra lại và ghi điều kiện nếu cần.</li></ol>
<p>Ví dụ: <strong>2H<sub>2</sub> + O<sub>2</sub> → 2H<sub>2</sub>O</strong>. Các hệ số cho biết tỉ lệ số mol giữa các chất, không được thay đổi chỉ số trong công thức.</p>'),
('Chương 1. Phản ứng hóa học','Bài 6 · Tính theo phương trình hóa học','bai-6-tinh-theo-phuong-trinh','Tính lượng chất tham gia và sản phẩm từ phương trình hóa học và hiệu suất.',50,'Chuyển đổi giữa khối lượng, số mol và thể tích khí; dùng tỉ lệ hệ số để tính lượng chất; hiểu ý nghĩa hiệu suất phản ứng.','<h2>Tính theo phương trình hóa học</h2>
<p>Phương trình hóa học là “cầu nối” giữa các lượng chất. Từ dữ kiện đã biết, đổi về số mol, dùng tỉ lệ hệ số để tìm số mol cần tính rồi đổi sang khối lượng hoặc thể tích.</p>
<h3>Các bước</h3><ol><li>Viết và cân bằng phương trình.</li><li>Đổi dữ kiện về mol.</li><li>Dùng tỉ lệ hệ số để tìm mol chất cần tính.</li><li>Đổi về đại lượng đề bài yêu cầu và kiểm tra đơn vị.</li></ol>
<p>Hiệu suất phản ứng: <strong>H = lượng thực tế/lượng lí thuyết × 100%</strong>. Hiệu suất không vượt quá 100%; sản phẩm thực tế thường nhỏ hơn sản phẩm tính theo phương trình.</p>'),
('Chương 1. Phản ứng hóa học','Bài 7 · Tốc độ phản ứng và chất xúc tác','bai-7-toc-do-phan-ung-chat-xuc-tac','Giải thích tốc độ phản ứng và các yếu tố làm phản ứng nhanh hoặc chậm.',45,'Nêu được ý nghĩa tốc độ phản ứng; phân tích ảnh hưởng của nồng độ, nhiệt độ, diện tích tiếp xúc và chất xúc tác.','<h2>Tốc độ phản ứng</h2>
<p>Tốc độ phản ứng đặc trưng cho mức độ nhanh hay chậm của một phản ứng. Có thể nhận biết qua thời gian xuất hiện sản phẩm hoặc mức thay đổi nồng độ theo thời gian.</p>
<h3>Yếu tố ảnh hưởng</h3><ul><li>Tăng nồng độ chất tham gia thường làm phản ứng nhanh hơn.</li><li>Tăng nhiệt độ làm các hạt chuyển động nhanh, va chạm hiệu quả hơn.</li><li>Tăng diện tích bề mặt tiếp xúc, chẳng hạn nghiền nhỏ chất rắn, làm tăng tốc độ.</li><li>Chất xúc tác làm tăng tốc độ nhưng sau phản ứng hầu như không bị tiêu hao.</li></ul>
<p>Trong thí nghiệm cần chỉ thay đổi một yếu tố, giữ các yếu tố khác như nhau để so sánh công bằng.</p>'),
('Chương 2. Một số hợp chất thông dụng','Bài 8 · Acid','bai-8-acid','Nhận biết acid, tính chất hóa học và cách sử dụng an toàn một số acid thông dụng.',45,'Nêu được khái niệm acid và ion H⁺; nhận biết dung dịch acid bằng chất chỉ thị; viết được phản ứng acid với kim loại và base ở mức cơ bản.','<h2>Acid</h2>
<p>Acid là hợp chất khi tan trong nước tạo ra ion H⁺. Dung dịch acid thường làm quỳ tím hóa đỏ và có pH nhỏ hơn 7.</p>
<h3>Tính chất</h3><ul><li>Acid tác dụng với một số kim loại tạo muối và khí hydrogen.</li><li>Acid tác dụng với base tạo muối và nước (phản ứng trung hòa).</li><li>Acid tác dụng với carbonate tạo muối, carbon dioxide và nước.</li></ul>
<p>Ví dụ: <strong>2HCl + Mg → MgCl<sub>2</sub> + H<sub>2</sub></strong>; <strong>HCl + NaOH → NaCl + H<sub>2</sub>O</strong>.</p>
<h3>An toàn</h3><p>Không nếm acid. Khi pha loãng acid, rót từ từ acid vào nước và khuấy nhẹ theo hướng dẫn, không làm ngược lại.</p>'),
('Chương 2. Một số hợp chất thông dụng','Bài 9 · Base – thang pH','bai-9-base-thang-ph','Tìm hiểu base, thang pH, chất chỉ thị và phản ứng trung hòa trong đời sống.',45,'Nêu được khái niệm base; sử dụng thang pH để đánh giá tính acid/base; giải thích được ứng dụng của trung hòa.','<h2>Base và pH</h2>
<p>Base là hợp chất khi tan trong nước tạo ra ion OH⁻. Dung dịch base có thể làm quỳ tím hóa xanh; base tan trong nước còn gọi là kiềm.</p>
<h3>Thang pH</h3><p>Ở điều kiện thông thường, pH nhỏ hơn 7 biểu thị môi trường acid, pH bằng 7 là trung tính, pH lớn hơn 7 là môi trường base. Có thể dùng giấy chỉ thị hoặc máy đo pH để kiểm tra.</p>
<h3>Trung hòa</h3><p>Acid và base phản ứng tạo muối và nước. Phản ứng này được vận dụng để xử lí đất chua, điều chỉnh pH nước và giảm tác động của một số chất gây kích ứng. Không tự bôi hóa chất lên da; cần hỏi người lớn hoặc nhân viên y tế.</p>'),
('Chương 2. Một số hợp chất thông dụng','Bài 10 · Oxide','bai-10-oxide','Phân loại oxide và dự đoán một số phản ứng của oxide acid, oxide base.',40,'Nêu được oxide là hợp chất của oxygen với một nguyên tố khác; phân loại và viết được phản ứng minh họa đơn giản.','<h2>Oxide</h2>
<p>Oxide là hợp chất gồm oxygen và một nguyên tố khác. Oxide kim loại thường có tính base; oxide phi kim thường có tính acid, nhưng có những oxide trung tính hoặc lưỡng tính.</p>
<h3>Tính chất</h3><ul><li>Oxide base tác dụng với acid tạo muối và nước, ví dụ: <strong>CuO + 2HCl → CuCl<sub>2</sub> + H<sub>2</sub>O</strong>.</li><li>Oxide acid tác dụng với base tạo muối và nước, ví dụ: <strong>CO<sub>2</sub> + Ca(OH)<sub>2</sub> → CaCO<sub>3</sub> + H<sub>2</sub>O</strong>.</li><li>Oxide lưỡng tính có thể phản ứng với cả acid và base trong điều kiện thích hợp.</li></ul>
<p>Cần dựa vào thành phần và tính chất để gọi tên, phân loại, không suy đoán chỉ từ màu sắc của chất.</p>'),
('Chương 2. Một số hợp chất thông dụng','Bài 11 · Muối','bai-11-muoi','Nhận biết thành phần, tính tan, tính chất hóa học và cách điều chế muối.',50,'Nêu được khái niệm muối; dùng bảng tính tan ở mức cơ bản; viết được phản ứng trao đổi tạo kết tủa, khí hoặc nước.','<h2>Muối</h2>
<p>Muối là hợp chất gồm cation kim loại hoặc NH₄⁺ và anion gốc acid. Nhiều muối là chất rắn; độ tan của muối khác nhau nên cần tra bảng tính tan khi dự đoán phản ứng.</p>
<h3>Tính chất hóa học</h3><ul><li>Muối có thể tác dụng với kim loại hoạt động hơn.</li><li>Muối tác dụng với acid, base hoặc muối khác khi tạo sản phẩm kết tủa, khí hoặc chất điện li yếu.</li></ul>
<p>Ví dụ: <strong>AgNO<sub>3</sub> + NaCl → AgCl↓ + NaNO<sub>3</sub></strong>. Muối có thể điều chế bằng phản ứng acid–base, acid với oxide base hoặc phản ứng trao đổi thích hợp.</p>
<p>Khi làm thí nghiệm với muối, quan sát màu, trạng thái và kết tủa; không nếm hóa chất dù đó là muối quen thuộc.</p>'),
('Chương 2. Một số hợp chất thông dụng','Bài 12 · Phân bón hóa học','bai-12-phan-bon-hoa-hoc','Phân biệt phân đạm, lân, kali và sử dụng phân bón hợp lí, an toàn.',35,'Nêu được vai trò của N, P, K; đọc được kí hiệu dinh dưỡng trên bao bì; đề xuất cách bón phân giảm ô nhiễm.','<h2>Phân bón hóa học</h2>
<p>Phân bón cung cấp nguyên tố dinh dưỡng cần cho cây. Ba nhóm thường gặp là phân đạm cung cấp nitrogen, phân lân cung cấp phosphorus và phân kali cung cấp potassium.</p>
<h3>Chọn và dùng phân</h3><ul><li>Chọn loại phân theo cây, giai đoạn sinh trưởng và kết quả kiểm tra đất.</li><li>Bón đúng liều, đúng lúc, đúng cách; không bón quá gần nguồn nước.</li><li>Kết hợp phân hữu cơ và luân canh để duy trì độ phì đất.</li></ul>
<p>Bón thừa có thể làm chai đất, phú dưỡng nguồn nước và phát thải khí gây hại. Đọc hướng dẫn, đeo găng khi bón và bảo quản phân xa thức ăn, trẻ nhỏ.</p>'),
('Chương 3. Khối lượng riêng và áp suất','Bài 13 · Khối lượng riêng','bai-13-khoi-luong-rieng','Tính khối lượng riêng và dùng đại lượng này để nhận biết, so sánh vật liệu.',40,'Nêu được ý nghĩa khối lượng riêng; sử dụng công thức D = m/V; đổi đúng đơn vị và giải thích ứng dụng.','<h2>Khối lượng riêng</h2>
<p>Khối lượng riêng cho biết khối lượng chứa trong một đơn vị thể tích của chất. Công thức: <strong>D = m/V</strong>, trong đó D đo bằng kg/m³, m bằng kg và V bằng m³.</p>
<p>Với cùng thể tích, vật có khối lượng lớn hơn có khối lượng riêng lớn hơn. Với vật đồng chất, có thể suy ra <strong>m = DV</strong> và <strong>V = m/D</strong>.</p>
<h3>Ứng dụng</h3><p>Khối lượng riêng giúp lựa chọn vật liệu, kiểm tra độ tinh khiết và tính tải trọng. Khi giải bài, đổi cm³ sang m³ hoặc g/cm³ sang kg/m³ trước khi thay số.</p>'),
('Chương 3. Khối lượng riêng và áp suất','Bài 14 · Thực hành xác định khối lượng riêng','bai-14-thuc-hanh-xac-dinh-khoi-luong-rieng','Đo khối lượng và thể tích để xác định khối lượng riêng của vật rắn hoặc chất lỏng.',45,'Lập được bảng đo; sử dụng cân, ống đong và thước đúng cách; tính và đánh giá sai số đo đơn giản.','<h2>Thực hành xác định khối lượng riêng</h2>
<h3>Vật rắn có hình dạng đều</h3><p>Đo khối lượng bằng cân, đo các kích thước bằng thước rồi tính thể tích theo hình học. Tính D = m/V và ghi kết quả kèm đơn vị.</p>
<h3>Vật rắn không thấm nước</h3><p>Đo thể tích nước ban đầu trong ống đong, nhúng chìm vật, đọc thể tích sau rồi lấy hiệu. Tránh bọt khí và không để vật chạm thành khi đọc.</p>
<h3>Chất lỏng</h3><p>Cân bình rỗng và bình có chất lỏng, lấy hiệu để tìm khối lượng chất lỏng; đo thể tích bằng ống đong. Lặp lại phép đo, tính trung bình và nêu nguyên nhân sai lệch.</p>'),
('Chương 3. Khối lượng riêng và áp suất','Bài 15 · Áp suất trên một bề mặt','bai-15-ap-suat-tren-mot-be-mat','Mô tả tác dụng của áp lực và tính áp suất trên một bề mặt.',40,'Nêu được áp lực; dùng công thức p = F/S; giải thích tác dụng của tăng, giảm diện tích bị ép trong thực tế.','<h2>Áp suất trên một bề mặt</h2>
<p>Áp lực là lực ép có phương vuông góc với mặt bị ép. Áp suất đặc trưng cho tác dụng của áp lực trên một đơn vị diện tích: <strong>p = F/S</strong>, đơn vị pascal (Pa), 1 Pa = 1 N/m².</p>
<p>Giữ nguyên áp lực, giảm diện tích tiếp xúc làm áp suất tăng; tăng diện tích làm áp suất giảm. Mũi kim nhọn, lưỡi dao sắc tạo áp suất lớn. Bánh xe bản rộng, ván trượt tuyết và móng nhà lớn giúp giảm áp suất.</p>
<p>Khi tính toán, xác định đúng lực vuông góc và đổi diện tích về m².</p>'),
('Chương 3. Khối lượng riêng và áp suất','Bài 16 · Áp suất chất lỏng và áp suất khí quyển','bai-16-ap-suat-chat-long-khi-quyen','Giải thích áp suất trong chất lỏng, áp suất khí quyển và một số ứng dụng.',45,'Nêu được chất lỏng gây áp suất theo mọi phương; nhận biết sự phụ thuộc vào độ sâu và khối lượng riêng; mô tả tác dụng của khí quyển.','<h2>Áp suất chất lỏng và khí quyển</h2>
<p>Chất lỏng gây áp suất lên đáy bình, thành bình và vật đặt trong nó theo mọi phương. Ở cùng chất lỏng, càng xuống sâu áp suất càng lớn; chất lỏng có khối lượng riêng lớn tạo áp suất lớn hơn.</p>
<p>Trong trường hợp đơn giản, áp suất do cột chất lỏng được tính gần đúng bằng <strong>p = ρgh</strong>. Đập nước thường xây chân đập dày hơn vì áp suất tăng theo độ sâu.</p>
<h3>Áp suất khí quyển</h3><p>Không khí có trọng lượng nên gây áp suất lên mọi vật. Ống hút, giác hút và khí áp kế hoạt động nhờ chênh lệch áp suất khí quyển. Áp suất khí quyển thay đổi theo độ cao và thời tiết.</p>'),
('Chương 3. Khối lượng riêng và áp suất','Bài 17 · Lực đẩy Archimedes','bai-17-luc-day-archimedes','Giải thích lực đẩy của chất lỏng lên vật và điều kiện vật nổi, chìm.',45,'Nêu được hướng và độ lớn tương đối của lực đẩy; vận dụng Fₐ = ρgV để giải thích hiện tượng nổi, chìm.','<h2>Lực đẩy Archimedes</h2>
<p>Vật nhúng trong chất lỏng chịu lực đẩy hướng từ dưới lên. Độ lớn lực đẩy bằng trọng lượng phần chất lỏng bị vật chiếm chỗ: <strong>Fₐ = ρgV</strong>.</p>
<h3>Vật nổi và chìm</h3><ul><li>Vật chìm khi trọng lượng lớn hơn lực đẩy cực đại.</li><li>Vật nổi khi lực đẩy cân bằng trọng lượng ở một phần thể tích chìm.</li><li>Vật lơ lửng khi hai lực cân bằng trong lòng chất lỏng.</li></ul>
<p>Tàu thép nổi nhờ khoang rỗng làm khối lượng riêng trung bình của cả tàu nhỏ hơn nước. Khi làm thí nghiệm, nhúng vật từ từ và tránh để dây treo tạo lực đáng kể.</p>'),
('Chương 4. Tác dụng làm quay của lực','Bài 18 · Tác dụng làm quay của lực. Moment lực','bai-18-tac-dung-lam-quay-cua-luc','Nhận biết tác dụng làm quay của lực và tính moment lực trong tình huống đơn giản.',40,'Nêu được moment lực phụ thuộc vào lực và cánh tay đòn; xác định chiều quay và điều kiện cân bằng.','<h2>Moment lực</h2>
<p>Lực có thể làm vật quay quanh trục cố định. Mức độ làm quay được đặc trưng bởi moment lực: <strong>M = Fd</strong>, trong đó F là độ lớn lực và d là cánh tay đòn, tức khoảng cách vuông góc từ trục quay đến giá của lực.</p>
<p>Tăng lực hoặc tăng cánh tay đòn làm moment tăng. Muốn mở cửa dễ hơn, ta tác dụng lực ở tay nắm xa bản lề và theo phương thích hợp.</p>
<h3>Cân bằng</h3><p>Vật cân bằng quanh trục khi các moment theo hai chiều quay cân bằng nhau. Khi phân tích, cần xác định trục quay, giá lực và đơn vị N·m.</p>'),
('Chương 4. Tác dụng làm quay của lực','Bài 19 · Đòn bẩy và ứng dụng','bai-19-don-bay-va-ung-dung','Phân tích cấu tạo đòn bẩy và giải thích ứng dụng trong đời sống.',35,'Xác định điểm tựa, lực tác dụng và tải; phân biệt các loại đòn bẩy; vận dụng quy tắc moment để chọn vị trí có lợi.','<h2>Đòn bẩy</h2>
<p>Đòn bẩy là vật rắn có thể quay quanh điểm tựa. Ba yếu tố cần xác định là điểm tựa, lực tác dụng và tải. Tùy vị trí của chúng, đòn bẩy cho lợi về lực, về đường đi hoặc đổi hướng lực.</p>
<p>Kìm, kéo, bập bênh, xe cút kít và cần câu là những ứng dụng quen thuộc. Khi cánh tay đòn của lực tác dụng dài hơn cánh tay đòn của tải, có thể nâng tải bằng lực nhỏ hơn.</p>
<h3>Thiết kế an toàn</h3><p>Đặt điểm tựa vững, không đứng trong vùng quét của tay đòn và không dùng dụng cụ vượt quá tải cho phép. Hãy giải thích lợi ích của mỗi đòn bẩy bằng so sánh moment lực.</p>'),
('Chương 5. Điện','Bài 20 · Hiện tượng nhiễm điện do cọ xát','bai-20-nhiem-dien-do-co-xat','Giải thích sự nhiễm điện do cọ xát và lực hút, đẩy giữa các vật nhiễm điện.',35,'Mô tả sự chuyển electron khi cọ xát; phân biệt vật nhiễm điện dương, âm; nhận biết vật dẫn và vật cách điện.','<h2>Nhiễm điện do cọ xát</h2>
<p>Khi cọ xát hai vật khác nhau, electron có thể chuyển từ vật này sang vật kia. Vật nhận thêm electron nhiễm điện âm; vật mất electron nhiễm điện dương.</p>
<p>Các vật nhiễm điện cùng dấu đẩy nhau, trái dấu hút nhau. Vật trung hòa vẫn có thể bị hút do sự phân bố lại điện tích trong vật.</p>
<h3>Ứng dụng và an toàn</h3><p>Lược nhựa cọ xát hút mẩu giấy; sơn tĩnh điện giúp lớp sơn bám đều. Kim loại dẫn điện tốt, nhựa và cao su thường cách điện. Không thử hiện tượng với ổ điện hoặc thiết bị điện đang hoạt động.</p>'),
('Chương 5. Điện','Bài 21 · Dòng điện, nguồn điện','bai-21-dong-dien-nguon-dien','Hiểu dòng điện, nguồn điện và điều kiện duy trì dòng điện trong mạch.',35,'Nêu được dòng điện là dòng chuyển dời có hướng của hạt mang điện; nhận biết nguồn điện và hai cực; phân biệt chiều quy ước.','<h2>Dòng điện và nguồn điện</h2>
<p>Dòng điện là dòng chuyển dời có hướng của các hạt mang điện. Trong kim loại, electron chuyển động có hướng; chiều dòng điện quy ước từ cực dương qua mạch ngoài đến cực âm của nguồn.</p>
<p>Nguồn điện duy trì hiệu điện thế giữa hai cực, chẳng hạn pin, acquy hoặc máy phát điện. Mạch phải có đường dẫn kín và nguồn phù hợp thì dòng điện mới tồn tại ổn định.</p>
<p>Không nối trực tiếp hai cực pin bằng dây dẫn vì có thể làm nóng dây, hỏng pin hoặc gây cháy. Thu gom pin đã dùng đúng nơi quy định.</p>'),
('Chương 5. Điện','Bài 22 · Mạch điện đơn giản','bai-22-mach-dien-don-gian','Lắp và đọc sơ đồ mạch điện gồm nguồn, công tắc, dây dẫn và thiết bị tiêu thụ.',40,'Nhận biết kí hiệu mạch điện; lắp được mạch kín đơn giản; giải thích trạng thái sáng, tắt của bóng đèn.','<h2>Mạch điện đơn giản</h2>
<p>Mạch điện gồm nguồn điện, dây dẫn, thiết bị tiêu thụ và thiết bị đóng ngắt. Mạch kín tạo thành đường liên tục cho dòng điện; mạch hở làm dòng điện bị ngắt.</p>
<h3>Cách đọc sơ đồ</h3><p>Đối chiếu kí hiệu pin, công tắc, bóng đèn và dây nối với linh kiện thực tế. Khi lắp, nối đúng cực nguồn, siết chặt tiếp điểm và kiểm tra công tắc trước khi đóng mạch.</p>
<p>Nếu đèn không sáng, kiểm tra lần lượt nguồn, bóng, dây nối và trạng thái công tắc. Không chạm hai đầu dây trần khi đang nối với nguồn có điện áp cao.</p>'),
('Chương 5. Điện','Bài 23 · Tác dụng của dòng điện','bai-23-tac-dung-cua-dong-dien','Nhận biết các tác dụng nhiệt, phát sáng, từ, hóa học và sinh lí của dòng điện.',35,'Nêu được biểu hiện và ứng dụng của từng tác dụng; nhận biết nguy cơ điện giật và cách phòng tránh.','<h2>Tác dụng của dòng điện</h2>
<ul><li><strong>Tác dụng nhiệt:</strong> dây dẫn nóng lên, dùng trong bàn là, bếp điện.</li><li><strong>Tác dụng phát sáng:</strong> đèn LED, đèn sợi đốt phát sáng.</li><li><strong>Tác dụng từ:</strong> dòng điện tạo từ trường, dùng trong nam châm điện và động cơ.</li><li><strong>Tác dụng hóa học:</strong> mạ điện, điện phân.</li><li><strong>Tác dụng sinh lí:</strong> dòng điện lớn có thể gây co cơ, bỏng và nguy hiểm tính mạng.</li></ul>
<p>Chỉ sử dụng thiết bị đúng điện áp, không chạm điện khi tay ướt và ngắt nguồn trước khi sửa chữa. Thiết bị bảo vệ như cầu chì, aptomat giúp hạn chế sự cố.</p>'),
('Chương 5. Điện','Bài 24 · Cường độ dòng điện và hiệu điện thế','bai-24-cuong-do-dong-dien-hieu-dien-the','Đo và giải thích ý nghĩa của cường độ dòng điện, hiệu điện thế trong mạch.',40,'Nêu được đơn vị A và V; mắc ampe kế nối tiếp, vôn kế song song; đọc số chỉ và chọn thang đo an toàn.','<h2>Cường độ dòng điện và hiệu điện thế</h2>
<p>Cường độ dòng điện I cho biết mức độ mạnh yếu của dòng điện, đơn vị ampe (A). Hiệu điện thế U giữa hai điểm tạo khả năng làm dịch chuyển điện tích, đơn vị volt (V).</p>
<h3>Nguyên tắc mắc đo</h3><ul><li>Ampe kế mắc nối tiếp với bộ phận cần đo, cực dương về phía cực dương nguồn.</li><li>Vôn kế mắc song song với bộ phận cần đo, chọn thang đo lớn trước nếu chưa biết giá trị.</li></ul>
<p>Không mắc ampe kế trực tiếp vào hai cực nguồn. Ghi giá trị, đơn vị và độ chia nhỏ nhất của dụng cụ; ngắt mạch sau khi đo.</p>'),
('Chương 5. Điện','Bài 25 · Thực hành đo cường độ dòng điện và hiệu điện thế','bai-25-thuc-hanh-do-cuong-do-hieu-dien-the','Lắp mạch, đo cường độ dòng điện và hiệu điện thế bằng ampe kế, vôn kế.',45,'Vẽ sơ đồ; lắp mạch đúng; thực hiện phép đo an toàn; lập bảng kết quả và rút ra nhận xét.','<h2>Thực hành đo I và U</h2>
<ol><li>Kiểm tra nguồn, dây nối, bóng đèn và giới hạn đo của đồng hồ.</li><li>Lắp mạch theo sơ đồ, để công tắc mở trong lúc nối dây.</li><li>Đóng mạch, đọc ampe kế mắc nối tiếp với đèn.</li><li>Mắc vôn kế song song với đèn, đọc và ghi hiệu điện thế.</li><li>Ngắt mạch, đổi vị trí đo nếu cần rồi so sánh kết quả.</li></ol>
<p>Không để mạch đóng quá lâu khi nguồn hoặc dây nóng. Nếu kim lệch ngược hoặc vượt thang, mở công tắc ngay và kiểm tra cực nối.</p>'),
('Chương 6. Nhiệt','Bài 26 · Năng lượng nhiệt và nhiệt năng','bai-26-nang-luong-nhiet-nhiet-nang','Giải thích năng lượng nhiệt, nội năng và cách làm thay đổi nội năng.',40,'Mô tả chuyển động nhiệt của hạt; phân biệt nhiệt năng với nhiệt độ; nêu được truyền năng lượng bằng thực hiện công và truyền nhiệt.','<h2>Năng lượng nhiệt và nhiệt năng</h2>
<p>Các phân tử, nguyên tử luôn chuyển động hỗn loạn. Năng lượng gắn với chuyển động và tương tác của chúng tạo nên nội năng của vật; phần năng lượng do chuyển động nhiệt thường gọi là nhiệt năng.</p>
<p>Nhiệt độ phản ánh mức độ nóng lạnh và liên quan đến tốc độ chuyển động nhiệt, còn nhiệt năng phụ thuộc cả nhiệt độ, khối lượng và chất. Vật có thể tăng nội năng khi được làm nóng hoặc khi có công thực hiện lên nó.</p>
<h3>Ví dụ</h3><p>Chà xát hai bàn tay làm tay ấm lên do thực hiện công. Đặt thìa kim loại vào nước nóng làm nhiệt năng truyền từ nước sang thìa.</p>'),
('Chương 6. Nhiệt','Bài 27 · Thực hành đo năng lượng nhiệt bằng Joulemeter','bai-27-thuc-hanh-joulemeter','Đo năng lượng điện chuyển hóa thành nhiệt bằng Joulemeter và phân tích số liệu.',45,'Lắp được mạch đo; đọc năng lượng, thời gian và nhiệt độ; đánh giá ảnh hưởng của điều kiện đo.','<h2>Đo năng lượng nhiệt bằng Joulemeter</h2>
<p>Joulemeter đo năng lượng điện truyền cho bộ phận đun. Trong thí nghiệm, ghi năng lượng cung cấp, thời gian và nhiệt độ của nước ở các thời điểm.</p>
<h3>Quy trình</h3><ol><li>Đặt cảm biến đúng vị trí, kiểm tra dây và chọn giới hạn đo.</li><li>Đo khối lượng nước, ghi nhiệt độ ban đầu.</li><li>Đóng mạch trong thời gian xác định, khuấy nhẹ để nhiệt phân bố đều.</li><li>Ghi số chỉ Joulemeter và nhiệt độ cuối; lặp lại nếu cần.</li></ol>
<p>So sánh các lần đo trong cùng điều kiện. Hao phí ra môi trường, vị trí cảm biến và sai số đọc là những nguyên nhân làm kết quả khác lí thuyết.</p>'),
('Chương 6. Nhiệt','Bài 28 · Sự truyền nhiệt','bai-28-su-truyen-nhiet','Phân biệt dẫn nhiệt, đối lưu, bức xạ nhiệt và vận dụng trong đời sống.',40,'Nêu được chiều truyền nhiệt; mô tả ba hình thức truyền nhiệt; chọn vật liệu phù hợp để cách nhiệt.','<h2>Sự truyền nhiệt</h2>
<p>Nhiệt truyền tự phát từ vật có nhiệt độ cao sang vật có nhiệt độ thấp cho đến khi cân bằng nhiệt.</p>
<ul><li><strong>Dẫn nhiệt:</strong> truyền qua các phần liền kề, rõ ở chất rắn; kim loại dẫn nhiệt tốt.</li><li><strong>Đối lưu:</strong> truyền theo dòng chuyển dời của chất lỏng hoặc chất khí.</li><li><strong>Bức xạ nhiệt:</strong> truyền bằng sóng điện từ, có thể đi qua chân không.</li></ul>
<p>Nồi dùng kim loại ở đáy để dẫn nhiệt, cán nồi dùng nhựa hoặc gỗ để cách nhiệt. Màu tối hấp thụ bức xạ tốt hơn màu sáng trong nhiều tình huống.</p>'),
('Chương 6. Nhiệt','Bài 29 · Sự nở vì nhiệt','bai-29-su-no-vi-nhiet','Giải thích sự nở vì nhiệt của chất rắn, lỏng, khí và biện pháp kĩ thuật.',40,'Nêu được chất nở ra khi nóng, co lại khi lạnh; so sánh mức nở; giải thích khe co giãn và ứng dụng nhiệt kế.','<h2>Sự nở vì nhiệt</h2>
<p>Chất rắn, chất lỏng và chất khí thường nở ra khi nóng lên, co lại khi lạnh đi. Với cùng độ tăng nhiệt độ, chất khí nở nhiều hơn chất lỏng, chất lỏng nở nhiều hơn chất rắn.</p>
<h3>Ứng dụng</h3><p>Nhiệt kế dùng sự nở vì nhiệt của chất lỏng hoặc khí. Cầu, đường ray và đường ống có khe co giãn để tránh cong vênh, nứt gãy. Băng kép dùng hai kim loại nở khác nhau để đóng ngắt tự động.</p>
<p>Không đun kín bình chứa chất lỏng; chất khí bị nhốt và nóng lên có thể làm tăng áp suất nguy hiểm.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 30 · Khái quát về cơ thể người','bai-30-khai-quat-co-the-nguoi','Mô tả các mức tổ chức và sự phối hợp của các hệ cơ quan trong cơ thể người.',35,'Nêu được các mức tế bào–mô–cơ quan–hệ cơ quan; nhận biết chức năng chính của các hệ; hiểu cơ thể là một thể thống nhất.','<h2>Khái quát về cơ thể người</h2>
<p>Cơ thể người được tổ chức theo các mức: tế bào, mô, cơ quan và hệ cơ quan. Các tế bào cùng chức năng tạo mô; nhiều mô tạo cơ quan; các cơ quan phối hợp thành hệ cơ quan.</p>
<ul><li>Hệ vận động nâng đỡ và giúp cơ thể di chuyển.</li><li>Hệ tiêu hóa, hô hấp, tuần hoàn và bài tiết trao đổi chất.</li><li>Hệ thần kinh và nội tiết điều khiển, phối hợp hoạt động.</li></ul>
<p>Các hệ liên hệ chặt chẽ. Ví dụ, cơ cần oxygen và chất dinh dưỡng do máu vận chuyển, đồng thời thải carbon dioxide qua hệ hô hấp.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 31 · Hệ vận động ở người','bai-31-he-van-dong-o-nguoi','Tìm hiểu bộ xương, hệ cơ, khớp và cách bảo vệ hệ vận động.',40,'Mô tả cấu tạo, chức năng hệ vận động; giải thích co cơ và vận động; đề xuất biện pháp phòng cong vẹo, chấn thương.','<h2>Hệ vận động</h2>
<p>Hệ vận động gồm bộ xương, khớp và hệ cơ. Xương nâng đỡ, bảo vệ cơ quan và làm chỗ bám cho cơ; khớp nối các xương; cơ co và dãn tạo lực kéo xương.</p>
<h3>Bảo vệ hệ vận động</h3><ul><li>Ăn đủ calcium, vitamin D và protein; vận động phù hợp lứa tuổi.</li><li>Ngồi, đứng, đeo cặp đúng tư thế; không mang vác quá sức.</li><li>Khi nghi gãy xương, cố định vùng tổn thương, không tự nắn và gọi trợ giúp y tế.</li></ul>
<p>Luyện tập đều giúp xương chắc, cơ khỏe và phối hợp vận động tốt hơn.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 32 · Dinh dưỡng và tiêu hóa ở người','bai-32-dinh-duong-tieu-hoa-o-nguoi','Mô tả quá trình tiêu hóa, hấp thụ và xây dựng chế độ dinh dưỡng hợp lí.',45,'Nêu được vai trò chất dinh dưỡng; mô tả đường đi của thức ăn và các biến đổi; đề xuất thói quen bảo vệ hệ tiêu hóa.','<h2>Dinh dưỡng và tiêu hóa</h2>
<p>Chất dinh dưỡng cung cấp năng lượng và vật liệu xây dựng cơ thể. Hệ tiêu hóa gồm ống tiêu hóa và các tuyến tiêu hóa, biến đổi thức ăn thành chất đơn giản để hấp thụ.</p>
<p>Tiêu hóa cơ học nghiền, trộn thức ăn; tiêu hóa hóa học dùng enzyme phân giải chất dinh dưỡng. Ruột non là nơi hấp thụ phần lớn chất dinh dưỡng; ruột già hấp thụ thêm nước và tạo phân.</p>
<h3>Ăn uống hợp lí</h3><p>Ăn đa dạng, đủ năng lượng và chất xơ, nhai kĩ, uống đủ nước, giữ vệ sinh thực phẩm và răng miệng. Hạn chế đồ quá nhiều đường, muối, chất béo và không tự dùng thuốc.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 33 · Máu và hệ tuần hoàn của cơ thể người','bai-33-mau-he-tuan-hoan','Tìm hiểu thành phần máu, cấu tạo tim, mạch máu và vòng tuần hoàn.',45,'Nêu được chức năng huyết tương và tế bào máu; mô tả đường đi của máu; đề xuất cách bảo vệ tim mạch.','<h2>Máu và hệ tuần hoàn</h2>
<p>Máu gồm huyết tương và tế bào máu. Hồng cầu vận chuyển oxygen và carbon dioxide; bạch cầu bảo vệ cơ thể; tiểu cầu tham gia đông máu.</p>
<p>Tim co bóp đẩy máu qua động mạch, mao mạch và tĩnh mạch. Vòng tuần hoàn nhỏ trao đổi khí ở phổi; vòng tuần hoàn lớn đưa oxygen và chất dinh dưỡng đến mô, thu chất thải về cơ quan thải.</p>
<h3>Bảo vệ tim mạch</h3><p>Vận động vừa sức, ăn giảm muối và chất béo bão hòa, ngủ đủ, không hút thuốc, theo dõi huyết áp. Khi chảy máu, dùng gạc sạch ép trực tiếp và tìm trợ giúp.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 34 · Hệ hô hấp ở người','bai-34-he-ho-hap-o-nguoi','Mô tả đường dẫn khí, trao đổi khí và biện pháp bảo vệ hệ hô hấp.',40,'Nêu được cấu tạo hệ hô hấp; giải thích trao đổi khí ở phế nang; thực hiện thói quen phòng bệnh hô hấp.','<h2>Hệ hô hấp</h2>
<p>Hệ hô hấp gồm đường dẫn khí và phổi. Không khí đi qua mũi, hầu, thanh quản, khí quản, phế quản rồi tới phế nang.</p>
<p>Ở phế nang, oxygen khuếch tán vào máu còn carbon dioxide khuếch tán từ máu ra không khí nhờ chênh lệch nồng độ và thành phế nang mỏng, nhiều mao mạch.</p>
<h3>Bảo vệ</h3><p>Thở bằng mũi, giữ ấm, đeo khẩu trang khi không khí ô nhiễm, thông thoáng phòng, tập thể dục và không hút thuốc. Khi ho kéo dài hoặc khó thở, cần báo người lớn và khám y tế.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 35 · Hệ bài tiết ở người','bai-35-he-bai-tiet-o-nguoi','Tìm hiểu cấu tạo thận, sự tạo nước tiểu và cách bảo vệ hệ bài tiết.',40,'Nêu được chức năng bài tiết; mô tả vai trò của thận, ống dẫn, bàng quang và niệu đạo; đề xuất thói quen phòng bệnh.','<h2>Hệ bài tiết</h2>
<p>Hệ bài tiết loại chất cặn bã và chất dư thừa khỏi cơ thể. Hệ bài tiết nước tiểu gồm hai thận, ống dẫn nước tiểu, bàng quang và niệu đạo.</p>
<p>Thận lọc máu ở nephron, giữ lại chất cần thiết, tạo nước tiểu rồi đưa xuống bàng quang. Uống đủ nước, không nhịn tiểu và giữ vệ sinh giúp giảm nguy cơ viêm đường tiết niệu, sỏi thận.</p>
<p>Ăn quá mặn, dùng thuốc tùy tiện hoặc mất nước kéo dài có thể làm tăng gánh nặng cho thận. Khi tiểu buốt, tiểu máu hoặc đau lưng bất thường, cần khám sớm.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 36 · Điều hòa môi trường trong của cơ thể người','bai-36-dieu-hoa-moi-truong-trong','Hiểu môi trường trong và cơ chế duy trì ổn định nhiệt độ, nước, muối và đường huyết.',45,'Nêu được thành phần môi trường trong; giải thích khái niệm cân bằng nội môi; liên hệ vai trò của thần kinh, hormone và cơ quan điều hòa.','<h2>Điều hòa môi trường trong</h2>
<p>Môi trường trong gồm máu, dịch mô và bạch huyết, tạo môi trường trao đổi giữa tế bào với cơ thể. Cân bằng nội môi là duy trì tương đối ổn định các điều kiện như nhiệt độ, pH, nước, muối và glucose.</p>
<h3>Cơ chế phản hồi</h3><p>Khi một chỉ số lệch khỏi mức thích hợp, cơ quan cảm nhận báo về trung tâm điều khiển; cơ quan thực hiện tạo đáp ứng ngược chiều để đưa chỉ số trở lại. Mồ hôi và giãn mạch giúp hạ nhiệt; run và co mạch giúp giữ nhiệt.</p>
<p>Ăn uống cân đối, uống đủ nước, ngủ và vận động hợp lí hỗ trợ cân bằng nội môi.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 37 · Hệ thần kinh và các giác quan ở người','bai-37-he-than-kinh-giac-quan','Mô tả cấu tạo hệ thần kinh, cung phản xạ và hoạt động của các giác quan.',45,'Phân biệt hệ thần kinh trung ương và ngoại biên; mô tả cung phản xạ; đề xuất biện pháp bảo vệ não, mắt và tai.','<h2>Hệ thần kinh và giác quan</h2>
<p>Hệ thần kinh gồm não, tủy sống và dây thần kinh, tiếp nhận thông tin, xử lí và điều khiển đáp ứng. Cung phản xạ gồm bộ phận thụ cảm, dây hướng tâm, trung ương, dây li tâm và cơ quan đáp ứng.</p>
<p>Mắt tiếp nhận ánh sáng, tai tiếp nhận âm thanh và góp phần giữ thăng bằng; da, mũi và lưỡi tiếp nhận các kích thích khác.</p>
<h3>Vệ sinh</h3><p>Ngủ đủ, học xen kẽ nghỉ, tránh màn hình quá lâu, đọc nơi đủ sáng, không đưa vật nhọn vào tai và bảo vệ đầu khi chơi thể thao. Không tự ý dùng thuốc nhỏ mắt hoặc thuốc tai.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 38 · Hệ nội tiết ở người','bai-38-he-noi-tiet-o-nguoi','Tìm hiểu hormone, các tuyến nội tiết và vai trò điều hòa hoạt động cơ thể.',40,'Nêu được hormone và tuyến nội tiết; mô tả vai trò của tuyến yên, giáp, tụy, thượng thận, sinh dục; liên hệ phòng bệnh.','<h2>Hệ nội tiết</h2>
<p>Tuyến nội tiết tiết hormone vào máu. Hormone tác động đến cơ quan đích với lượng nhỏ nhưng có vai trò điều khiển tăng trưởng, trao đổi chất, sinh sản và thích nghi.</p>
<ul><li>Tuyến yên điều hòa nhiều tuyến khác và tiết hormone tăng trưởng.</li><li>Tuyến giáp tiết thyroxine, cần iodine cho hoạt động bình thường.</li><li>Tuyến tụy tiết insulin và glucagon giúp ổn định đường huyết.</li><li>Tuyến thượng thận và tuyến sinh dục tiết hormone đáp ứng stress, dậy thì và sinh sản.</li></ul>
<p>Ăn đủ iodine, vận động, hạn chế đồ uống nhiều đường và khám khi có dấu hiệu bất thường giúp bảo vệ hệ nội tiết.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 39 · Da và điều hòa thân nhiệt ở người','bai-39-da-dieu-hoa-than-nhiet','Mô tả cấu tạo da, chức năng bảo vệ và cơ chế điều hòa thân nhiệt.',35,'Nêu được các lớp của da; giải thích tiết mồ hôi, co giãn mạch; đề xuất cách chăm sóc da và phòng nóng lạnh.','<h2>Da và thân nhiệt</h2>
<p>Da gồm biểu bì, lớp bì và lớp mỡ dưới da. Da bảo vệ cơ thể, tiếp nhận kích thích, tiết mồ hôi và góp phần điều hòa nhiệt độ.</p>
<p>Khi nóng, mạch máu dưới da giãn và tuyến mồ hôi tăng hoạt động, giúp tỏa nhiệt. Khi lạnh, mạch co, cơ run và cơ thể giảm mất nhiệt. Nhiệt độ cơ thể còn được điều khiển bởi trung tâm ở não.</p>
<h3>Chăm sóc da</h3><p>Tắm rửa, giữ da khô sạch, che nắng, uống đủ nước và xử lí vết thương đúng cách. Khi ở môi trường nóng, nghỉ nơi thoáng và bù nước; khi có dấu hiệu say nóng cần gọi trợ giúp.</p>'),
('Chương 7. Sinh học cơ thể người','Bài 40 · Sinh sản ở người','bai-40-sinh-san-o-nguoi','Tìm hiểu cơ quan sinh sản, dậy thì, thụ tinh và chăm sóc sức khỏe sinh sản.',45,'Mô tả khái quát cơ quan sinh sản nam, nữ; hiểu thụ tinh và phát triển phôi; thực hành kiến thức bảo vệ bản thân, tôn trọng và tìm trợ giúp.','<h2>Sinh sản ở người</h2>
<p>Cơ quan sinh sản tạo giao tử và hormone sinh dục. Ở tuổi dậy thì, cơ thể thay đổi về thể chất, cảm xúc và khả năng sinh sản; mỗi người phát triển với tốc độ khác nhau.</p>
<p>Thụ tinh là sự kết hợp giữa tinh trùng và trứng, thường xảy ra ở ống dẫn trứng. Hợp tử phát triển thành phôi trong tử cung. Sức khỏe của cha mẹ, dinh dưỡng và chăm sóc y tế ảnh hưởng đến thai nhi.</p>
<h3>Bảo vệ bản thân</h3><p>Giữ vệ sinh, tôn trọng ranh giới cơ thể, tìm người lớn tin cậy khi bị ép buộc hoặc gặp nguy cơ. Kiến thức tránh thai, phòng bệnh lây truyền qua đường tình dục và tư vấn y tế cần được tiếp cận từ nguồn chuyên môn.</p>'),
('Chương 8. Sinh vật và môi trường','Bài 41 · Môi trường và các nhân tố sinh thái','bai-41-moi-truong-nhan-to-sinh-thai','Phân biệt môi trường sống, nhân tố vô sinh và hữu sinh; hiểu giới hạn sinh thái.',35,'Nêu được khái niệm môi trường và nhân tố sinh thái; phân tích tác động của một số nhân tố; liên hệ thích nghi của sinh vật.','<h2>Môi trường và nhân tố sinh thái</h2>
<p>Môi trường sống gồm các yếu tố bao quanh và tác động đến sinh vật. Nhân tố vô sinh gồm ánh sáng, nhiệt độ, nước, đất; nhân tố hữu sinh gồm sinh vật cùng loài, khác loài và con người.</p>
<p>Mỗi loài có giới hạn chịu đựng đối với từng nhân tố. Khoảng thuận lợi giúp sinh trưởng tốt; ngoài giới hạn, sinh vật bị ức chế hoặc chết.</p>
<p>Các nhân tố tác động đồng thời, vì vậy thay đổi một yếu tố có thể kéo theo thay đổi khác. Bảo vệ nơi sống và sử dụng tài nguyên hợp lí giúp duy trì đa dạng sinh học.</p>'),
('Chương 8. Sinh vật và môi trường','Bài 42 · Quần thể sinh vật','bai-42-quan-the-sinh-vat','Nhận biết quần thể và các đặc trưng về mật độ, giới tính, tuổi, phân bố.',40,'Nêu được khái niệm quần thể; phân tích một số đặc trưng; giải thích tăng trưởng và đề xuất biện pháp bảo vệ quần thể.','<h2>Quần thể sinh vật</h2>
<p>Quần thể là tập hợp cá thể cùng loài, sống trong một không gian và thời gian xác định, có khả năng sinh sản tạo thế hệ mới.</p>
<h3>Đặc trưng</h3><ul><li>Kích thước và mật độ cá thể.</li><li>Tỉ lệ giới tính, thành phần nhóm tuổi.</li><li>Kiểu phân bố: theo nhóm, đồng đều hoặc ngẫu nhiên.</li></ul>
<p>Số lượng quần thể thay đổi do sinh, tử, nhập cư và xuất cư. Khai thác quá mức hoặc phá hủy nơi sống làm quần thể suy giảm; bảo vệ sinh cảnh và khai thác theo hạn ngạch giúp phục hồi.</p>'),
('Chương 8. Sinh vật và môi trường','Bài 43 · Quần xã sinh vật','bai-43-quan-xa-sinh-vat','Tìm hiểu thành phần loài, quan hệ giữa các loài và cấu trúc quần xã.',40,'Nêu được khái niệm quần xã; mô tả độ đa dạng, loài ưu thế, tầng phân bố; nhận biết quan hệ hỗ trợ và đối kháng.','<h2>Quần xã sinh vật</h2>
<p>Quần xã là tập hợp nhiều quần thể thuộc các loài khác nhau cùng sống trong một khu vực và thời gian xác định, có quan hệ qua lại.</p>
<p>Quần xã được mô tả bằng số loài, độ đa dạng, loài ưu thế và sự phân tầng. Các quan hệ gồm hỗ trợ như cộng sinh, hợp tác; đối kháng như cạnh tranh, kí sinh, ăn thịt.</p>
<p>Đa dạng loài cao thường giúp quần xã ổn định hơn trước biến động. Bảo vệ loài bản địa, hạn chế loài xâm lấn và duy trì sinh cảnh là cách giữ quần xã bền vững.</p>'),
('Chương 8. Sinh vật và môi trường','Bài 44 · Hệ sinh thái','bai-44-he-sinh-thai','Mô tả thành phần hệ sinh thái, chuỗi thức ăn, lưới thức ăn và dòng năng lượng.',45,'Nêu được khái niệm hệ sinh thái; phân biệt sinh vật sản xuất, tiêu thụ, phân giải; lập chuỗi thức ăn và giải thích dòng năng lượng.','<h2>Hệ sinh thái</h2>
<p>Hệ sinh thái gồm quần xã sinh vật và môi trường vô sinh, giữa chúng có trao đổi vật chất và năng lượng. Sinh vật sản xuất tạo chất hữu cơ; sinh vật tiêu thụ ăn sinh vật khác; sinh vật phân giải phân hủy xác và chất thải.</p>
<p>Chuỗi thức ăn biểu diễn quan hệ dinh dưỡng, nhiều chuỗi liên kết thành lưới thức ăn. Năng lượng truyền một chiều và giảm dần qua các bậc dinh dưỡng; vật chất được tái sử dụng qua các chu trình.</p>
<p>Sử dụng tài nguyên trong giới hạn và bảo vệ sinh cảnh giúp hệ sinh thái duy trì chức năng.</p>'),
('Chương 8. Sinh vật và môi trường','Bài 45 · Sinh quyển','bai-45-sinh-quyen','Nhận biết sinh quyển và mối liên hệ giữa các hệ sinh thái trên Trái Đất.',35,'Nêu được khái niệm sinh quyển; mô tả phạm vi và các khu sinh học; hiểu sự phụ thuộc lẫn nhau của các hệ sinh thái.','<h2>Sinh quyển</h2>
<p>Sinh quyển là phần Trái Đất có sự sống, gồm một phần khí quyển, thủy quyển và thạch quyển nơi sinh vật tồn tại. Sinh quyển bao gồm nhiều hệ sinh thái liên kết qua dòng vật chất, năng lượng và sự phát tán của sinh vật.</p>
<p>Các khu sinh học lớn có khí hậu, thảm thực vật và động vật đặc trưng như rừng mưa, sa mạc, đồng cỏ, biển và vùng cực. Biến đổi ở một khu vực có thể ảnh hưởng đến khu vực khác.</p>
<p>Bảo vệ đa dạng sinh học, phục hồi sinh cảnh và sử dụng tài nguyên bền vững là trách nhiệm chung.</p>'),
('Chương 8. Sinh vật và môi trường','Bài 46 · Cân bằng tự nhiên','bai-46-can-bang-tu-nhien','Giải thích cân bằng tự nhiên, nguyên nhân mất cân bằng và cách phục hồi.',35,'Nêu được khái niệm cân bằng tự nhiên; phân tích tác động của con người; đề xuất biện pháp duy trì, phục hồi hệ sinh thái.','<h2>Cân bằng tự nhiên</h2>
<p>Cân bằng tự nhiên là trạng thái các thành phần của hệ sinh thái được điều chỉnh tương đối ổn định qua các mối quan hệ và chu trình vật chất.</p>
<p>Phá rừng, khai thác quá mức, ô nhiễm, đưa loài ngoại lai và biến đổi khí hậu có thể làm mất cân bằng, dẫn đến bùng nổ sâu bệnh, xói mòn, suy giảm đa dạng sinh học hoặc thiếu nước.</p>
<h3>Giữ cân bằng</h3><p>Bảo vệ sinh cảnh, phục hồi rừng và đất ngập nước, kiểm soát loài xâm lấn, xử lí chất thải, khai thác theo sức tải và theo dõi chỉ số sinh thái. Giải pháp cần dựa trên dữ liệu và sự tham gia của cộng đồng.</p>'),
('Chương 8. Sinh vật và môi trường','Bài 47 · Bảo vệ môi trường','bai-47-bao-ve-moi-truong','Nhận diện ô nhiễm, biến đổi khí hậu và đề xuất hành động bảo vệ môi trường tại địa phương.',45,'Trình bày tác động của con người; nêu nguyên nhân, hậu quả ô nhiễm; hiểu biến đổi khí hậu và sự cần thiết bảo vệ động vật hoang dã.','<h2>Bảo vệ môi trường</h2>
<p>Hoạt động của con người có thể cung cấp lương thực, năng lượng và dịch vụ nhưng cũng gây ô nhiễm, suy thoái đất, mất rừng và suy giảm đa dạng sinh học.</p>
<h3>Ô nhiễm và khí hậu</h3><p>Ô nhiễm có thể do chất thải sinh hoạt, công nghiệp, hóa chất bảo vệ thực vật, phóng xạ hoặc sinh vật gây bệnh. Biến đổi khí hậu làm nhiệt độ, mực nước biển và thời tiết cực đoan thay đổi; cần giảm phát thải và thích ứng với rủi ro.</p>
<h3>Hành động</h3><p>Phân loại, giảm và tái sử dụng rác; tiết kiệm điện nước; trồng, bảo vệ cây; không mua bán động vật hoang dã; điều tra một vấn đề ở địa phương, ghi bằng chứng và đề xuất giải pháp khả thi.</p>');


INSERT INTO lessons(topic_id,title,slug,summary,duration_minutes,status,created_by)
SELECT t.id,s.title,s.slug,s.summary,s.duration_minutes,'PUBLISHED',@khtn8_admin_id
FROM khtn8_lesson_seed s
JOIN topics t ON t.course_id=@khtn8_course_id AND t.name=s.chapter_name
WHERE NOT EXISTS (SELECT 1 FROM lessons l WHERE l.slug=s.slug);

-- Replace the three original demo versions with complete textbook-aligned lessons.
UPDATE lesson_versions v
JOIN lessons l ON l.id=v.lesson_id
JOIN khtn8_lesson_seed s ON s.slug=l.slug
SET v.objectives=s.objectives, v.content_html=s.content_html, v.status='PUBLISHED'
WHERE l.slug IN ('bai-2-phan-ung-hoa-hoc','bai-18-tac-dung-lam-quay-cua-luc','bai-32-dinh-duong-tieu-hoa-o-nguoi')
  AND v.version_number=1;

INSERT INTO lesson_versions(lesson_id,version_number,objectives,content_html,status,created_by)
SELECT l.id,1,s.objectives,s.content_html,'PUBLISHED',@khtn8_admin_id
FROM lessons l
JOIN khtn8_lesson_seed s ON s.slug=l.slug
WHERE NOT EXISTS (SELECT 1 FROM lesson_versions v WHERE v.lesson_id=l.id);

DROP TEMPORARY TABLE khtn8_lesson_seed;

-- Mark the course as a full textbook-aligned course rather than a demo.
UPDATE courses
SET title='Khoa học tự nhiên 8 · Kết nối tri thức',
    summary='Đầy đủ bài giảng Khoa học tự nhiên 8 theo mục lục Kết nối tri thức: Hóa học, Vật lí, Sinh học và Môi trường.',
    theme='blue'
WHERE id=@khtn8_course_id;
