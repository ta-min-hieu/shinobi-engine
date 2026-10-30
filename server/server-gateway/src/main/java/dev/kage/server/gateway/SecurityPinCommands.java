package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import dev.kage.account.api.SecurityPinFacade;
import dev.kage.protocol.GetSecurityPinStatusResponse;
import dev.kage.protocol.SecurityPinOutcome;
import dev.kage.protocol.SetSecurityPinCommand;
import dev.kage.protocol.SetSecurityPinResponse;
import dev.kage.protocol.VerifySecurityPinResponse;

/**
 * Ba lệnh khoá an toàn (ADR-115) — chạy trên Virtual Thread (băm PBKDF2 + DB). {@code accountId} từ session; trạng thái
 * mở/khoá của phiên nằm ở {@link SecurityPinGate} của chính kết nối.
 */
final class SecurityPinCommands {

    private static final Map<SecurityPinFacade.Outcome, SecurityPinOutcome> OUTCOMES =
            new EnumMap<>(SecurityPinFacade.Outcome.class);

    static {
        OUTCOMES.put(SecurityPinFacade.Outcome.OK, SecurityPinOutcome.SECURITY_PIN_OUTCOME_OK);
        OUTCOMES.put(SecurityPinFacade.Outcome.INVALID_FORMAT, SecurityPinOutcome.SECURITY_PIN_OUTCOME_INVALID_FORMAT);
        OUTCOMES.put(SecurityPinFacade.Outcome.WRONG_PIN, SecurityPinOutcome.SECURITY_PIN_OUTCOME_WRONG_PIN);
        OUTCOMES.put(SecurityPinFacade.Outcome.LOCKED, SecurityPinOutcome.SECURITY_PIN_OUTCOME_LOCKED);
        OUTCOMES.put(SecurityPinFacade.Outcome.NO_PIN, SecurityPinOutcome.SECURITY_PIN_OUTCOME_NO_PIN);
    }

    private final SecurityPinFacade pins;
    private final Clock clock;

    SecurityPinCommands(SecurityPinFacade pins, Clock clock) {
        this.pins = pins;
        this.clock = clock;
    }

    /** Tài khoản có PIN chưa — gọi lúc đăng nhập để nạp {@link SecurityPinGate}. */
    boolean hasPin(long accountId) {
        return pins.status(accountId, clock.instant()).hasPin();
    }

    GetSecurityPinStatusResponse status(long accountId, SecurityPinGate gate) {
        SecurityPinFacade.Status status = pins.status(accountId, clock.instant());
        return GetSecurityPinStatusResponse.newBuilder()
                .setHasPin(status.hasPin())
                .setUnlocked(!status.hasPin() || gate.isOpen())
                .setLockedUntilEpochMs(epochMillis(status.lockedUntil()))
                .build();
    }

    SetSecurityPinResponse set(long accountId, SetSecurityPinCommand command, SecurityPinGate gate) {
        SecurityPinFacade.Result result =
                pins.set(accountId, command.getCurrentPin(), command.getNewPin(), clock.instant());
        if (result.outcome() == SecurityPinFacade.Outcome.OK) {
            gate.unlock();
        }
        return SetSecurityPinResponse.newBuilder()
                .setOutcome(OUTCOMES.get(result.outcome()))
                .setAttemptsLeft(result.attemptsLeft())
                .setLockedUntilEpochMs(epochMillis(result.lockedUntil()))
                .build();
    }

    VerifySecurityPinResponse verify(long accountId, String pin, SecurityPinGate gate) {
        SecurityPinFacade.Result result = pins.verify(accountId, pin, clock.instant());
        if (result.outcome() == SecurityPinFacade.Outcome.OK) {
            gate.unlock();
        }
        return VerifySecurityPinResponse.newBuilder()
                .setOutcome(OUTCOMES.get(result.outcome()))
                .setAttemptsLeft(result.attemptsLeft())
                .setLockedUntilEpochMs(epochMillis(result.lockedUntil()))
                .build();
    }

    private static long epochMillis(Instant instant) {
        return Instant.EPOCH.equals(instant) ? 0L : instant.toEpochMilli();
    }
}
