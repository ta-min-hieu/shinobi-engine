package dev.kage.account.application;

import java.util.concurrent.atomic.AtomicReference;

import dev.kage.account.domain.Account;
import dev.kage.account.domain.AccountPort;
import dev.kage.account.domain.SessionPort;
import dev.kage.account.domain.TransactionRunner;

/**
 * Đăng nhập bằng username — dự án cá nhân, chưa có mật khẩu ở bước này (không phải đăng nhập
 * "đầy đủ" của SECURITY.md §3; nâng cấp sau không đổi kiến trúc session — ADR-017 không đổi).
 * Tự tạo account nếu username chưa tồn tại.
 */
public final class LoginOrCreateAccountUseCase {

    private final AccountPort accountPort;
    private final SessionPort sessionPort;
    private final TransactionRunner transactionManager;

    public LoginOrCreateAccountUseCase(
            AccountPort accountPort, SessionPort sessionPort, TransactionRunner transactionManager) {
        this.accountPort = accountPort;
        this.sessionPort = sessionPort;
        this.transactionManager = transactionManager;
    }

    public LoginResult execute(String username) {
        // Kiểm TRƯỚC khi chạm DB — xem javadoc Account#validateUsername.
        Account.validateUsername(username);
        AtomicReference<Account> accountHolder = new AtomicReference<Account>();
        transactionManager.inTransaction(tx -> accountHolder.set(accountPort.findOrCreate(tx, username)));

        Account account = accountHolder.get();
        String sessionToken = sessionPort.issue(account.id());
        return new LoginResult(account.id(), account.username(), sessionToken);
    }

    /**
     * Kết thúc phiên do {@link #execute} phát ra — thu hồi token ngay (quyết định chủ dự án 2026-10-11).
     *
     * <p>Trước đó token chỉ hết theo TTL 24 giờ: mỗi lượt đăng nhập để lại một khoá trong Redis suốt một
     * ngày dù kết nối đã đóng từ lâu, nên spam đăng nhập làm Redis phình không giới hạn. Nằm ở lớp này
     * vì nó là nơi DUY NHẤT phát token; ai phát thì người đó thu.
     *
     * <p>Token rỗng (kết nối chưa từng đăng nhập) được bỏ qua.
     */
    public void endSession(String sessionToken) {
        if (sessionToken != null && !sessionToken.isEmpty()) {
            sessionPort.revoke(sessionToken);
        }
    }
}
