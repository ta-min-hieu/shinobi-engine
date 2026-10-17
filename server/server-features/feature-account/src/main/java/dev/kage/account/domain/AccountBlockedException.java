package dev.kage.account.domain;

/** Tài khoản không ở trạng thái {@link AccountStatus#ACTIVE} — từ chối đăng nhập, không phát token. */
public final class AccountBlockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient AccountStatus status;

    public AccountBlockedException(long accountId, AccountStatus status) {
        super("Tài khoản bị chặn đăng nhập: accountId=" + accountId + " status=" + status);
        this.status = status;
    }

    /** Trạng thái làm tài khoản bị chặn. */
    public AccountStatus status() {
        return status;
    }
}
