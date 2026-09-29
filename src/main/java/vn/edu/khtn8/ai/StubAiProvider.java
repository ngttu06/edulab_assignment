package vn.edu.khtn8.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!ai-real")
public class StubAiProvider implements AiProvider {
    @Override
    public AiAnswer answer(AiQuestion question) {
        return new AiAnswer(
                "Đây là chế độ AI mẫu. Hãy đọc lại mục kiến thức liên quan, xác định dữ kiện đã biết và thử giải từng bước. Khi kết nối nhà cung cấp AI thật, câu trả lời sẽ được tạo từ nội dung bài học đã được giáo viên duyệt.",
                new String[]{"Bài học hiện tại"},
                false
        );
    }

    @Override
    public String providerName() {
        return "stub-local";
    }
}
