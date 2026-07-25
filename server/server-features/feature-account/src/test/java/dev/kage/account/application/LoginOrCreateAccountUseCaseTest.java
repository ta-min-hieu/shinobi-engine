package dev.kage.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kage.account.domain.Account;
import dev.kage.account.domain.AccountPort;
import dev.kage.account.domain.SessionPort;
import dev.kage.account.domain.Transaction;
import dev.kage.account.domain.TransactionRunner;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

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
}
