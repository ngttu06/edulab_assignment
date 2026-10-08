# Cộng đồng giao lưu (tách khỏi Hỏi đáp/Quiz)

- `/community` và `/api/v1/community/**` dành cho bài viết, ảnh, lượt thích, bình luận và báo cáo. `/forum` và `/quizzes` giữ nguyên dữ liệu, API và mục đích học tập.
- Migration additive `V19__social_community.sql` tạo bảng riêng, không chuyển hoặc xóa câu hỏi cũ. Bảng tin và ảnh chỉ đọc bởi thành viên đã đăng nhập; CSRF bắt buộc với mọi request ghi.
- Ảnh JPEG/PNG/WebP dưới 20 MiB được trình duyệt giải mã, vẽ lại thành JPEG không kèm EXIF/GPS, cạnh dài không quá 1600 px, giảm chất lượng/kích thước xuống mục tiêu 300 KiB và giới hạn 500 KiB. Tối đa 5 ảnh/bài. Bản JPEG nén được gửi thẳng lên R2 bằng presigned URL đang có; server kiểm tra CLEAN, uploader, tên file tạo bởi client, MIME, SHA-256, kích thước, đọc giải mã và metadata trước khi gắn vào bài. PDF và ảnh bài tập diễn đàn không đi qua luồng này. Chất lượng/dung lượng chính xác tùy hình ảnh và trình duyệt; nén không đảm bảo một số byte tuyệt đối tối thiểu.
- Nếu R2 chưa bật hoặc ClamAV chưa sẵn sàng, ảnh không thể được đăng ngay. File bị cách ly không được hiển thị; cần cấu hình scanner để sử dụng ảnh trên môi trường thật. Không bật chế độ bỏ qua scan ngoài môi trường thử nghiệm.
- Tác giả có thể sửa nội dung/xóa (ẩn) bài, thành viên có thể bình luận/thích/báo cáo. Admin xem và xử lý báo cáo tại `/admin/community/reports`, ẩn hoặc mở lại bài. Ảnh bài đã ẩn/xóa mất quyền tải của thành viên khác. Object R2 của bài đã xóa và file upload bỏ dở **chưa có cơ chế dọn sau cùng**: cần quy trình retention/lifecycle và đối soát tham chiếu trước khi triển khai thật; không xóa vật lý trong transaction để tránh lệch database/object store.
- Deployment cloud đã sao lưu/phục hồi diễn tập và áp dụng V19 ngày 08/10/2026 qua migration job riêng, sau đó trả `EDULAB_MIGRATE_ON_DEPLOY=false`; chi tiết ở `/Users/ngttu/Workspace/project_asm_prj/docs/docker-cloud.md`. Web app không tự sửa schema.
- Cloud hiện chưa cấu hình `EDULAB_CLAMAV_HOST`: bài viết chữ hoạt động, còn ảnh mới sẽ chờ quét và không đăng được cho đến khi có scanner thật. Không bỏ qua bước quét chỉ để upload chạy.

Kiểm thử local (chỉ MySQL dùng một lần tại `127.0.0.1:3307/edulab_test`):

```bash
EDULAB_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/edulab_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC' mvn -B '-Dtest=!MysqlIntegrationTest' test
EDULAB_SMOKE_URL='http://127.0.0.1:8092/edulab' EDULAB_DISPOSABLE_TEST_DB=true node src/test/browser/social-community.cjs
```

Browser smoke chỉ chạy với app đang chạy trên loopback và DB thử nghiệm; sẽ đăng bài thử.

## Giao diện bảng tin và kiểm thử không cần database

- Đã bỏ banner `social-hero`; khung đăng bài nằm ngay dưới tab Trang chủ/Cộng đồng. Giữ cột tiện ích học tập dùng chung với Trang chủ.
- Khung đăng bài gọn với avatar, ô “Bạn đang nghĩ gì?”, chọn ảnh và nút đăng. Bài viết dùng avatar tròn, menu ba chấm, ảnh sát mép thẻ, hàng Thích/Bình luận/Chia sẻ và ô bình luận bo tròn. Chia sẻ hiện là sao chép liên kết, không đăng lại bài.
- Menu ba chấm dùng `details/summary`: mở bằng Enter, chuyển thao tác bằng Tab, đóng bằng Escape hoặc bấm ra ngoài. Trạng thái mở bình luận có `aria-expanded`/`aria-controls`. Ảnh chưa CLEAN vẫn không hiển thị.
- `SocialCommunityViewTest` khởi động Tomcat chỉ trên loopback để render JSP thật với dữ liệu giả; tắt Spring initializer, không nạp datasource hay secrets. Tạo HTML cho bảng tin, trang chi tiết, trạng thái trống và admin trong `/Users/ngttu/Workspace/project_asm_prj/target/community-ui-fixtures/`.
- `social-community-ui.cjs` dùng các HTML đó và CSS/JS thật; chặn toàn bộ mạng bằng Playwright, giả lập API. Kiểm tra tương tác và 7 độ rộng 320–1440 px, không cần MySQL hoặc ứng dụng cloud. Ảnh chụp ở `/Users/ngttu/Workspace/project_asm_prj/target/community-ui-screenshots/`.

```bash
mvn -f /Users/ngttu/Workspace/project_asm_prj/pom.xml -B -Dtest=SocialCommunityViewTest,SocialCommunitySecurityTest test
EDULAB_PLAYWRIGHT_PATH=/Users/ngttu/Workspace/project_asm_prj/.tools/browser/node_modules/playwright \
  node /Users/ngttu/Workspace/project_asm_prj/src/test/browser/social-community-ui.cjs
```

Có thể đặt `EDULAB_CHROME_PATH` nếu Chrome không nằm ở vị trí mặc định trên macOS. Kiểm thử giả lập không thay thế kiểm thử tích hợp upload/ClamAV trên môi trường thử riêng.

## Bình luận và cảm xúc — V20

- 7 cảm xúc **Thích / Yêu thích / Thương thương / Haha / Wow / Buồn / Phẫn nộ** cho bài viết; SVG tự vẽ dùng chung sprite, không tải emoji/thư viện bên ngoài. Di chuột lên nút Thích hoặc bấm mũi tên để chọn; dùng Tab/Enter, phím mũi tên, Home/End và Escape. Trên màn hình nhỏ bộ chọn chia hai hàng. Chọn lại cùng cảm xúc hoặc bấm nút cảm xúc đang chọn để gỡ; đổi loại không cộng thêm người phản ứng.
- `PUT /api/v1/community/posts/{id}/reaction` nhận `{"reaction":"LOVE"}` (enum viết hoa); `DELETE` cùng URL gỡ cảm xúc. Trả `{reaction,count,counts}`. Bảng tin trả thêm `reaction`, `reaction_label`, `reaction_count`, `reaction_counts`. API `/like` cũ vẫn hoạt động; `like_count`/`liked` biểu thị tổng phản ứng/có phản ứng để tương thích client cũ.
- Migration `/Users/ngttu/Workspace/project_asm_prj/src/main/resources/db/migration/V20__community_reactions.sql` thêm `reaction_type` có default `LIKE` và CHECK constraint vào `community_likes`; giữ nguyên khóa duy nhất `(post_id,user_id)` và toàn bộ dữ liệu cũ. Các cập nhật được serialize bằng khóa bài viết trong transaction.
- Bình luận một cấp, cũ nhất trước: avatar nhỏ, bong bóng nội dung, thời gian bên ngoài, sửa inline chỉ dành cho tác giả, xóa dành cho tác giả/admin. Chưa có trả lời phân nhánh hoặc reaction trên từng bình luận.
- Ô nhập nhiều dòng: Enter gửi, Shift+Enter xuống dòng; không gửi trong khi IME đang ghép chữ; giới hạn 1000 ký tự. Lỗi mạng giữ nguyên bản nháp và số đếm; khóa gửi khi request đang chạy. Gửi thành công chèn bình luận mới theo ID, giữ cursor hiện tại và loại trùng khi tải tiếp. `POST .../comments` trả toàn bộ bình luận thay vì chỉ ID (vẫn giữ trường `id`); `PUT .../comments/{commentId}` sửa nội dung.
- Kiểm thử: `SocialCommunitySecurityTest`, `SocialCommunityViewTest`, `SocialCommunityMysqlTest` trên DB dùng một lần; browser offline kiểm tra cả 7 cảm xúc/đổi/gỡ, bàn phím, 7 viewport, sửa/xóa/phân trang/escaping và lỗi mạng. Smoke loopback xác nhận LOVE và bình luận đã sửa còn nguyên sau tải lại bằng JSP thật, rồi xóa bình luận. Đã diễn tập nâng schema V19 có 2 lượt thích lên V20: cả hai giữ nguyên với loại LIKE. Không upload ảnh thử hoặc bật bypass scan.

**Triển khai:** Không chỉ thay CSS/JS như bản feed-v19. Cần sao lưu DB, chạy migration job V20 được phê duyệt, rồi triển khai backend/JSP/assets đồng bộ; không bật Flyway trong web app hoặc migrate-on-deploy thường trực. Quy trình phát hành và rollback nằm tại `/Users/ngttu/Workspace/project_asm_prj/docs/docker-cloud.md`; trạng thái thực tế được ghi trong report riêng của lần triển khai. Khi rollback ứng dụng về v19, giữ cột additive để không mất loại cảm xúc, nhưng client v19 chỉ hiển thị chúng như lượt thích. Không chạy bản ứng dụng mới trên schema V19.