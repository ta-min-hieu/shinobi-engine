package dev.kage.client.core.ui.theme;

/**
 * Đọc/ghi lựa chọn ngôn ngữ của người chơi qua một {@link LanguageStore}.
 *
 * <p>Luật duy nhất đáng test nằm ở {@link #load}: giá trị lưu là thứ người chơi (hoặc một bản game
 * khác) có thể sửa tay, nên mọi thứ không nhận ra đều rơi về {@link ClientLanguage#DEFAULT} chứ
 * không làm hỏng màn hình đăng nhập — màn hình duy nhất có nút đổi lại ngôn ngữ.
 *
 * <p>Hàm tĩnh nhận nơi lưu làm tham số thay vì giữ nó trong field: bên gọi ({@code LoginScreen}) đã tự
 * sở hữu nơi lưu, không cần thêm một lớp bọc có trạng thái.
 */
public final class LanguageSettings {

    private LanguageSettings() {}

    /** Ngôn ngữ đã lưu, hoặc tiếng Việt khi chưa lưu/giá trị lưu không hợp lệ. */
    public static ClientLanguage load(LanguageStore store) {
        return store.readLanguageCode().flatMap(ClientLanguage::fromCode).orElse(ClientLanguage.DEFAULT);
    }

    /** Ghi lựa chọn để lần mở game sau dùng lại. */
    public static void save(LanguageStore store, ClientLanguage language) {
        store.writeLanguageCode(language.code());
    }
}
