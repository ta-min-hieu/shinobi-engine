package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.server.world.WorldOutboundSink;

/**
 * {@code lastKnownPosition} chỉ được chứa nhân vật ĐANG đăng ký.
 *
 * <p>Tái hiện race thật: {@code GatewayChannelHandler.releaseCharacter} gọi {@code unregister()}
 * trên EventLoop rồi mới xếp {@code detachCharacter} vào inbox của world — lệnh di chuyển nằm trong
 * inbox trước đó vẫn được world tick xử lý và gọi {@code onMoveAccepted} SAU {@code unregister()}.
 * Trước bản vá, lần gọi muộn đó đẻ lại một dòng vị trí cho nhân vật đã thoát: không bao giờ bị xoá
 * nữa, và {@code CharacterPositionPersister} ghi nó xuống DB ở mọi lượt định kỳ.
 *
 * <p>Test gọi tuần tự đúng thứ tự mà tick thread quan sát được (unregister xong rồi mới tới
 * onMoveAccepted) — không cần dựng đa luồng để thấy lỗi, vì lỗi nằm ở chỗ {@code put} không hề hỏi
 * nhân vật còn đăng ký hay không.
 */
class ConnectedCharacterRegistryPositionTest {

    private static final long CHARACTER_ID = 7L;

    private final ConnectedCharacterRegistry registry =
            new ConnectedCharacterRegistry(null, null, null, new GatewayNetworkMetrics(new SimpleMeterRegistry()));

    @AfterEach
    void stopRegistry() {
        registry.stop();
    }

    @Test
    void shouldNotResurrectPosition_whenMoveAcceptedArrivesAfterUnregister() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(CHARACTER_ID, channel.pipeline().firstContext());
        registry.onMoveAccepted(moveAccepted(1, 10f, 20f));

        registry.unregister(CHARACTER_ID);
        registry.onMoveAccepted(moveAccepted(2, 11f, 20f));

        assertThat(registry.snapshotPositions()).doesNotContainKey(CHARACTER_ID);
        assertThat(registry.lastKnownPosition(CHARACTER_ID)).isNull();
    }

    @Test
    void shouldNotTrackPosition_whenCharacterWasNeverRegistered() {
        registry.onMoveAccepted(moveAccepted(1, 10f, 20f));

        assertThat(registry.snapshotPositions()).isEmpty();
    }

    @Test
    void shouldTrackPositionOfNewSession_whenCharacterRelogs() {
        EmbeddedChannel firstSession = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(CHARACTER_ID, firstSession.pipeline().firstContext());
        registry.onMoveAccepted(moveAccepted(1, 10f, 20f));
        registry.unregister(CHARACTER_ID);

        EmbeddedChannel secondSession = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(CHARACTER_ID, secondSession.pipeline().firstContext());
        registry.onMoveAccepted(moveAccepted(1, 30f, 40f));

        assertThat(registry.snapshotPositions()).containsEntry(CHARACTER_ID, new PositionSnapshot(30f, 40f));
    }

    /**
     * Lần dọn của kết nối CŨ không được xoá nhân vật mà kết nối MỚI đã đăng ký (quyết định một-phiên
     * 2026-09-25) — kể cả vị trí mới nhất của phiên mới.
     */
    @Test
    void shouldKeepNewSession_whenOldConnectionUnregistersAfterIt() {
        EmbeddedChannel oldSession = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        EmbeddedChannel newSession = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(CHARACTER_ID, oldSession.pipeline().firstContext());
        registry.register(CHARACTER_ID, newSession.pipeline().firstContext());
        registry.onMoveAccepted(moveAccepted(1, 30f, 40f));

        boolean removed =
                registry.unregister(CHARACTER_ID, oldSession.pipeline().firstContext());

        assertThat(removed).isFalse();
        assertThat(registry.isOnline(CHARACTER_ID)).isTrue();
        assertThat(registry.lastKnownPosition(CHARACTER_ID)).isEqualTo(new PositionSnapshot(30f, 40f));
        assertThat(registry.unregister(CHARACTER_ID, newSession.pipeline().firstContext()))
                .isTrue();
        assertThat(registry.isOnline(CHARACTER_ID)).isFalse();
    }

    private static WorldOutboundSink.MoveAcceptedSnapshot moveAccepted(int sequenceId, float x, float y) {
        return new WorldOutboundSink.MoveAcceptedSnapshot(CHARACTER_ID, sequenceId, x, y, 0f, true);
    }
}
