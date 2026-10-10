package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.protocol.ChatChannel;

/** {@link ChatRateLimits} với đồng hồ giả — không phụ thuộc thời gian thật trôi. */
class ChatRateLimitsTest {

    private static final long ACCOUNT = 7L;
    private static final long OTHER_ACCOUNT = 8L;
    private static final int WORLD_LIMIT = 5;
    private static final int AREA_LIMIT = 15;
    private static final Duration WINDOW = Duration.ofSeconds(10);

    private final AtomicLong nowNanos = new AtomicLong(1_000L);
    private ChatRateLimits limits;

    @BeforeEach
    void newLimits() {
        limits = new ChatRateLimits(nowNanos::get);
    }

    @Test
    void shouldRejectPastTheWorldLimit_andAllowAgainInTheNextWindow() {
        assertThat(countAllowed(ACCOUNT, ChatChannel.CHAT_CHANNEL_WORLD, WORLD_LIMIT + 3))
                .isEqualTo(WORLD_LIMIT);

        advance(WINDOW);

        assertThat(limits.acquire(ACCOUNT, ChatChannel.CHAT_CHANNEL_WORLD)).isEqualTo(ChatRateLimits.Verdict.ALLOWED);
    }

    @Test
    void shouldKeepEachChannelAndEachAccountOnItsOwnBudget() {
        countAllowed(ACCOUNT, ChatChannel.CHAT_CHANNEL_WORLD, WORLD_LIMIT + 1);

        assertThat(countAllowed(ACCOUNT, ChatChannel.CHAT_CHANNEL_AREA, AREA_LIMIT + 1))
                .as("hết lượt Thế giới không ăn vào lượt Khu vực")
                .isEqualTo(AREA_LIMIT);
        assertThat(limits.acquire(OTHER_ACCOUNT, ChatChannel.CHAT_CHANNEL_WORLD))
                .as("tài khoản khác có sổ riêng")
                .isEqualTo(ChatRateLimits.Verdict.ALLOWED);
    }

    // Gói mang kênh lạ/không xác định bị từ chối ở bước sau (error.invalid_chat_channel) nhưng vẫn
    // phải TỐN lượt của kênh nghiêm ngặt nhất — bằng không nó là đường spam lệnh miễn phí.
    @Test
    void shouldChargeUnknownChannelsToTheWorldBudget() {
        countAllowed(ACCOUNT, ChatChannel.UNRECOGNIZED, 2);
        countAllowed(ACCOUNT, ChatChannel.CHAT_CHANNEL_UNSPECIFIED, 2);

        assertThat(countAllowed(ACCOUNT, ChatChannel.CHAT_CHANNEL_WORLD, WORLD_LIMIT))
                .isEqualTo(WORLD_LIMIT - 4);
    }

    @Test
    void shouldAskToDisconnect_onlyAfterViolatingSeveralWindowsInARow() {
        List<ChatRateLimits.Verdict> lastVerdictPerWindow = new ArrayList<ChatRateLimits.Verdict>();
        for (int window = 0; window < 4; window++) {
            ChatRateLimits.Verdict last = ChatRateLimits.Verdict.ALLOWED;
            for (int i = 0; i <= WORLD_LIMIT; i++) {
                last = limits.acquire(ACCOUNT, ChatChannel.CHAT_CHANNEL_WORLD);
            }
            lastVerdictPerWindow.add(last);
            advance(WINDOW);
        }

        assertThat(lastVerdictPerWindow)
                .containsExactly(
                        ChatRateLimits.Verdict.REJECTED,
                        ChatRateLimits.Verdict.REJECTED,
                        ChatRateLimits.Verdict.REJECTED,
                        ChatRateLimits.Verdict.REJECTED_AND_DISCONNECT);
    }

    // Sổ không được lớn mãi theo số tài khoản TỪNG chat — và việc quên không được đổi phán quyết.
    @Test
    void shouldForgetAccountsThatWentQuiet_withoutChangingTheirVerdict() {
        for (long account = 100; account < 200; account++) {
            limits.acquire(account, ChatChannel.CHAT_CHANNEL_WORLD);
        }
        assertThat(limits.trackedAccountCount()).isEqualTo(100);

        advance(Duration.ofMinutes(2));
        limits.acquire(ACCOUNT, ChatChannel.CHAT_CHANNEL_WORLD);

        assertThat(limits.trackedAccountCount()).isEqualTo(1);
        assertThat(countAllowed(150L, ChatChannel.CHAT_CHANNEL_WORLD, WORLD_LIMIT + 1))
                .isEqualTo(WORLD_LIMIT);
    }

    // Hai kết nối của cùng tài khoản nằm trên hai EventLoop khác nhau: tổng số tin được qua vẫn
    // phải ĐÚNG bằng trần, không hơn (đếm trùng) không kém.
    @Test
    @Timeout(60)
    void shouldAllowExactlyTheLimit_whenManyThreadsSendForTheSameAccount() throws Exception {
        int threads = 8;
        int attemptsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CyclicBarrier barrier = new CyclicBarrier(threads);
            List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
            for (int thread = 0; thread < threads; thread++) {
                futures.add(executor.submit(() -> {
                    barrier.await(30, TimeUnit.SECONDS);
                    return countAllowed(ACCOUNT, ChatChannel.CHAT_CHANNEL_AREA, attemptsPerThread);
                }));
            }
            int allowed = 0;
            for (Future<Integer> future : futures) {
                allowed += future.get(30, TimeUnit.SECONDS);
            }

            assertThat(allowed).isEqualTo(AREA_LIMIT);
        } finally {
            executor.shutdownNow();
        }
    }

    private int countAllowed(long accountId, ChatChannel channel, int attempts) {
        int allowed = 0;
        for (int i = 0; i < attempts; i++) {
            if (limits.acquire(accountId, channel) == ChatRateLimits.Verdict.ALLOWED) {
                allowed++;
            }
        }
        return allowed;
    }

    private void advance(Duration duration) {
        nowNanos.addAndGet(duration.toNanos());
    }
}
