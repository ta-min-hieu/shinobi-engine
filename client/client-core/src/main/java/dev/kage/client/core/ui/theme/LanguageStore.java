package dev.kage.client.core.ui.theme;

import java.util.Optional;

/**
 * Port lưu mã ngôn ngữ người chơi đã chọn trên MÁY CỦA HỌ (không phải trên server — đây là sở thích
 * hiển thị, không phải dữ liệu gameplay).
 *
 * <p>Tách thành port để {@link LanguageSettings} test được không cần libGDX khởi tạo; bản thật là
 * {@link GdxPreferencesLanguageStore}.
 */
public interface LanguageStore {

    /** Mã đã lưu, hoặc rỗng nếu chưa lưu bao giờ. Có thể là rác — bên gọi tự kiểm. */
    Optional<String> readLanguageCode();

    /** Ghi đè mã đã lưu và đẩy xuống nơi lưu ngay (người chơi có thể tắt game ngay sau khi chọn). */
    void writeLanguageCode(String code);
}
