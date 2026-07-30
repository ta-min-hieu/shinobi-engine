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
        AtomicReference<Account> accountHolder = new AtomicReference<Account>();
        transactionManager.inTransaction(tx -> accountHolder.set(accountPort.findOrCreate(tx, username)));

        Account account = accountHolder.get();
        String sessionToken = sessionPort.issue(account.id());
        return new LoginResult(account.id(), account.username(), sessionToken);
    }
}
