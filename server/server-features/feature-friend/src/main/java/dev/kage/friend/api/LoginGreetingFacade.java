package dev.kage.friend.api;

import java.util.Optional;

import dev.kage.friend.domain.LoginGreetingPort;

/** API câu chào khi vào game (ADR-425): ≤ {@value #MAX_LENGTH} ký tự. Chạm DB — Virtual Thread. */
public final class LoginGreetingFacade {

    /** Độ dài tối đa. */
    public static final int MAX_LENGTH = 60;

    /** Không lưu gì (composition test). */
    public static final LoginGreetingFacade DISABLED = new LoginGreetingFacade(new LoginGreetingPort() {
        @Override
        public Optional<String> greeting(long characterId) {
            return Optional.empty();
        }

        @Override
        public void setGreeting(long characterId, String message) {
            // không lưu
        }
    });

    private final LoginGreetingPort port;

    /** Dựng ở composition root. */
    public LoginGreetingFacade(LoginGreetingPort port) {
        this.port = port;
    }

    /** Câu chào ("" nếu chưa đặt). */
    public String greetingOf(long characterId) {
        return port.greeting(characterId).orElse("");
    }

    /** Đặt câu chào đã làm sạch (người gọi kiểm nội dung); rỗng = xoá. */
    public void set(long characterId, String message) {
        port.setGreeting(characterId, message);
    }
}
