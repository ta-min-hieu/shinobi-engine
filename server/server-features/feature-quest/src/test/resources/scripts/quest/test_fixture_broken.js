// test_fixture_broken.js — script CỐ Ý HỎNG, chỉ dùng cho *UseCaseTest. KHÔNG phải nội dung sản phẩm.
//
// Gác hai nhánh `scriptFailed` của AcceptQuestUseCase/ClaimQuestRewardUseCase (2026-09-12).
//
// ⚠️ Cả hai hook đều gọi vài callback THẬT rồi MỚI ném lỗi, và đó là toàn bộ giá trị của fixture
// này: một script ném lỗi ngay dòng đầu sẽ xanh kể cả khi Java vẫn đọc hiệu ứng dở dang — nó không
// gác được gì cả. Có `save_progress`/`give_exp`/`give_item` chạy trước chỗ lỗi thì test mới phân
// biệt được "bỏ hết" với "dùng những gì kịp chạy".
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ counter: 0 }));
        throw new Error("on_accept hỏng giữa chừng — lần save_progress phía trên phải bị bỏ");
    },

    // Không nhánh nào của test chạy hook này; giữ cho script đủ hình dạng một quest thật.
    on_event(ctx) {
        ctx.save_progress(ctx.progress_json);
    },

    on_claim(ctx) {
        ctx.give_exp(100);
        ctx.give_item("test_item", 3);
        throw new Error("on_claim hỏng giữa chừng — phần thưởng phía trên phải bị bỏ");
    }
})
