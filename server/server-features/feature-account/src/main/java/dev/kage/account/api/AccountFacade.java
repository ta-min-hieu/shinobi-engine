package dev.kage.account.api;

import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.account.application.LoginResult;

/**
 * API công khai của {@code feature-account} — module khác (server-gateway) CHỈ gọi qua đây,
 * không bao giờ đọc bảng {@code account} trực tiếp (Điều 24).
 */
public final class AccountFacade {

    private final LoginOrCreateAccountUseCase loginOrCreateAccountUseCase;

    /** Dựng ở composition root ({@code ServerDatabaseBootstrap}). */
    public AccountFacade(LoginOrCreateAccountUseCase loginOrCreateAccountUseCase) {
        this.loginOrCreateAccountUseCase = loginOrCreateAccountUseCase;
    }

    /**
     * Đăng nhập bằng username, tự tạo account nếu chưa có. Trả về session token
     * (ADR-017) để gọi các Command tiếp theo.
     */
    public LoginResult login(String username) {
        return loginOrCreateAccountUseCase.execute(username);
    }

    /**
     * Thu hồi session token của một lượt {@link #login} — gọi khi kết nối mang nó đóng, hoặc khi nó
     * đăng nhập lại. Chạm Redis (blocking): KHÔNG gọi trên Netty EventLoop.
     */
    public void logout(String sessionToken) {
        loginOrCreateAccountUseCase.endSession(sessionToken);
    }
}
