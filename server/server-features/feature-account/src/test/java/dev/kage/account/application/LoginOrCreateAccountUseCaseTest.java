package dev.kage.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.kage.account.domain.Account;
import dev.kage.account.domain.AccountBlockedException;
import dev.kage.account.domain.AccountPort;
import dev.kage.account.domain.AccountStatus;
import dev.kage.account.domain.SessionPort;
import dev.kage.account.domain.Transaction;
import dev.kage.account.domain.TransactionRunner;

class LoginOrCreateAccountUseCaseTest {

    /** Fake = implementation in-memory THẬT, phát hiện lỗi tương tác thật hơn Mockito (TESTING §4). */
    static final class FakeAccountPort implements AccountPort {
        final Map<String, Account> byUsername = new HashMap<>();
        final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Account findOrCreate(Transaction tx, String username) {
            return byUsername.computeIfAbsent(username, u -> new Account(nextId.getAndIncrement(), u, Instant.now()));
        }
    }

    static final class FakeSessionPort implements SessionPort {
        final Map<String, Long> tokens = new HashMap<>();

        @Override
        public String issue(long accountId) {
            String token = "fake-token-" + accountId;
            tokens.put(token, accountId);
            return token;
        }

        @Override
        public Optional<Long> validateAndRenew(String token) {
            return Optional.ofNullable(tokens.get(token));
        }

        @Override
        public void revoke(String token) {
            tokens.remove(token);
        }
    }

    static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldCreateNewAccount_whenUsernameNotSeenBefore() {
        FakeAccountPort accountPort = new FakeAccountPort();
        FakeSessionPort sessionPort = new FakeSessionPort();
        LoginOrCreateAccountUseCase useCase = new LoginOrCreateAccountUseCase(accountPort, sessionPort, DIRECT_TX);

        LoginResult result = useCase.execute("kagehero");

        assertThat(result.username()).isEqualTo("kagehero");
        assertThat(result.accountId()).isPositive();
        assertThat(result.sessionToken()).isNotBlank();
        assertThat(sessionPort.validateAndRenew(result.sessionToken())).contains(result.accountId());
    }

    /** README dòng 68(e): tài khoản bị khoá/tạm khoá không đăng nhập được, và không để lại token nào. */
    @ParameterizedTest
    @EnumSource(
            value = AccountStatus.class,
            names = {"BANNED", "SUSPENDED"})
    void shouldRefuseLoginAndIssueNoToken_whenAccountIsBlocked(AccountStatus status) {
        FakeAccountPort accountPort = new FakeAccountPort();
        accountPort.byUsername.put("banned_one", new Account(7, "banned_one", Instant.now(), status));
        FakeSessionPort sessionPort = new FakeSessionPort();
        LoginOrCreateAccountUseCase useCase = new LoginOrCreateAccountUseCase(accountPort, sessionPort, DIRECT_TX);

        assertThatThrownBy(() -> useCase.execute("banned_one"))
                .isInstanceOf(AccountBlockedException.class)
                .extracting(e -> ((AccountBlockedException) e).status())
                .isEqualTo(status);
        assertThat(sessionPort.tokens).isEmpty();
    }

    @Test
    void shouldTreatAnUnknownStatusValueAsBlocked() {
        assertThat(AccountStatus.fromColumn("ACTIVE").allowsLogin()).isTrue();
        assertThat(AccountStatus.fromColumn("BANED").allowsLogin()).isFalse();
        assertThat(AccountStatus.fromColumn(null).allowsLogin()).isFalse();
    }

    @Test
    void shouldReuseExistingAccount_whenUsernameAlreadyExists() {
        FakeAccountPort accountPort = new FakeAccountPort();
        FakeSessionPort sessionPort = new FakeSessionPort();
        LoginOrCreateAccountUseCase useCase = new LoginOrCreateAccountUseCase(accountPort, sessionPort, DIRECT_TX);

        LoginResult first = useCase.execute("kagehero");
        LoginResult second = useCase.execute("kagehero");

        assertThat(second.accountId()).isEqualTo(first.accountId());
        assertThat(accountPort.byUsername).hasSize(1);
        // Mỗi lần login là 1 session mới — token không nhất thiết trùng nhau (sliding TTL độc lập).
        assertThat(sessionPort.validateAndRenew(second.sessionToken())).contains(first.accountId());
    }

    @Test
    void shouldRejectInvalidUsername_viaAccountCompactConstructor() {
        FakeAccountPort accountPort = new FakeAccountPort();
        FakeSessionPort sessionPort = new FakeSessionPort();
        LoginOrCreateAccountUseCase useCase = new LoginOrCreateAccountUseCase(accountPort, sessionPort, DIRECT_TX);

        assertThatThrownBy(() -> useCase.execute("a")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Bug 2026-10-02: tên dài hơn 20 ký tự tới được câu INSERT, cột {@code account.username
     * VARCHAR(20)} từ chối nó TRƯỚC khi {@code Account} kịp kiểm, và người chơi nhận 500
     * {@code error.login_failed} thay vì {@code error.invalid_username}. Transaction runner ở đây ném
     * nếu bị gọi — nó đứng thay cho cái DB sẽ từ chối dữ liệu bẩn.
     */
    @Test
    void shouldRejectInvalidUsername_beforeTouchingTheDatabase() {
        TransactionRunner failIfOpened = action -> {
            throw new AssertionError("không được mở transaction cho username không hợp lệ");
        };
        LoginOrCreateAccountUseCase useCase =
                new LoginOrCreateAccountUseCase(new FakeAccountPort(), new FakeSessionPort(), failIfOpened);

        assertThatThrownBy(() -> useCase.execute("a".repeat(21))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute("ab")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute("kage\u0000hero")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Phiên phải thu hồi được ngay khi kết nối đóng (quyết định chủ dự án 2026-10-11) — trước đó token
     * chỉ hết theo TTL 24 giờ, nên mỗi lượt đăng nhập để lại một khoá Redis suốt một ngày.
     */
    @Test
    void endSessionShouldRevokeTheIssuedToken() {
        FakeSessionPort sessionPort = new FakeSessionPort();
        LoginOrCreateAccountUseCase useCase =
                new LoginOrCreateAccountUseCase(new FakeAccountPort(), sessionPort, action -> action.accept(null));
        LoginResult result = useCase.execute("kagehero");
        assertThat(sessionPort.validateAndRenew(result.sessionToken())).isPresent();

        useCase.endSession(result.sessionToken());

        assertThat(sessionPort.validateAndRenew(result.sessionToken())).isEmpty();
    }

    /** Kết nối chưa từng đăng nhập không có token — không được chạm tới cổng phiên. */
    @Test
    void endSessionShouldIgnoreAMissingToken() {
        FakeSessionPort sessionPort = new FakeSessionPort();
        LoginOrCreateAccountUseCase useCase =
                new LoginOrCreateAccountUseCase(new FakeAccountPort(), sessionPort, action -> action.accept(null));

        useCase.endSession(null);
        useCase.endSession("");

        assertThat(sessionPort.validateAndRenew("")).isEmpty();
    }

    /**
     * Kho session (Redis) sập thì VẪN đăng nhập được, với token rỗng (quyết định chủ dự án 2026-10-11).
     * Trước đó exception của Redis nổi thẳng lên và cả server không ai vào được, vì một token chưa lệnh
     * nào đọc.
     */
    @Test
    void shouldStillLogIn_withAnEmptyToken_whenTheSessionStoreIsDown() {
        FakeAccountPort accountPort = new FakeAccountPort();
        SessionPort brokenStore = new SessionPort() {
            @Override
            public String issue(long accountId) {
                throw new dev.kage.account.domain.SessionUnavailableException("Redis sập (giả lập)", null);
            }

            @Override
            public Optional<Long> validateAndRenew(String token) {
                return Optional.empty();
            }

            @Override
            public void revoke(String token) {
                throw new dev.kage.account.domain.SessionUnavailableException("Redis sập (giả lập)", null);
            }
        };
        LoginOrCreateAccountUseCase useCase = new LoginOrCreateAccountUseCase(accountPort, brokenStore, DIRECT_TX);

        LoginResult result = useCase.execute("kagehero");

        assertThat(result.accountId()).isPositive();
        assertThat(result.sessionToken()).isEmpty();
        assertThat(accountPort.byUsername).containsKey("kagehero");
        // Token rỗng không được chạm tới kho phiên lúc kết thúc phiên (kho đang hỏng sẽ ném).
        useCase.endSession(result.sessionToken());
    }
}
