package dev.kage.server.gateway;

import java.util.Optional;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.WorldKind;

/**
 * Dời một nhân vật tới chỗ một nhân vật khác đang đứng, TRONG world chính — thân của lệnh GM
 * {@code goto} (người dời = GM) và {@code summon} (người dời = mục tiêu), ADR-052 D4/D6.
 *
 * <p>World chính chia nhiều khu vực, nên "tới chỗ người kia" có thể là đổi khu vực: đi đúng đường
 * đổi World của ADR-051 (gỡ → chờ World cũ im → {@code WorldChangedEvent} → gắn), lý do
 * {@code GM_MOVED}. Chạy trên Virtual Thread (đọc DB + chờ mốc), không bao giờ trên EventLoop.
 *
 * <p>Đích là vị trí registry đang theo dõi của người neo (mẫu 1 Hz + mỗi lần di chuyển được nhận) —
 * luôn là chỗ một nhân vật có thật đứng được, không phải toạ độ tay (D4: không có {@code tp x y}).
 */
final class MainWorldRelocation {

    private static final Logger LOG = LoggerFactory.getLogger(MainWorldRelocation.class);

    /** Kết quả một lượt dời. */
    enum Outcome {
        OK,
        /** Một trong hai người không ở world chính (hầm ngục, đấu trường, offline). */
        NOT_IN_MAIN_WORLD,
        /** Chưa biết người neo đứng đâu (vừa vào world, chưa có mẫu vị trí nào). */
        NO_POSITION
    }

    private final MainWorldChannelRegistry channels;
    private final ConnectedCharacterRegistry registry;
    private final CharacterFacade characterFacade;
    private final WorldTransfer transfer;

    MainWorldRelocation(
            MainWorldChannelRegistry channels, ConnectedCharacterRegistry registry, CharacterFacade characterFacade) {
        this.channels = channels;
        this.registry = registry;
        this.characterFacade = characterFacade;
        this.transfer = new WorldTransfer(registry);
    }

    /** Dời {@code moverId} tới chỗ {@code anchorId} đang đứng. Chạm DB và chờ — chỉ gọi trên Virtual Thread. */
    Outcome move(long moverId, long anchorId) {
        PositionSnapshot destination = registry.lastKnownPosition(anchorId);
        if (destination == null) {
            return Outcome.NO_POSITION;
        }
        // Đọc DB TRƯỚC khi gỡ khỏi World — gỡ rồi mà đọc lỗi thì người dời kẹt ngoài mọi World.
        CharacterLoadout loadout = CharacterLoadout.load(characterFacade, registry, moverId);
        Optional<MainWorldChannelRegistry.PendingRelocation> pending = channels.beginRelocation(moverId, anchorId);
        if (pending.isEmpty()) {
            return Outcome.NOT_IN_MAIN_WORLD;
        }
        awaitOldWorldSilent(pending.get(), moverId);
        boolean moved = channels.finishRelocation(
                moverId,
                pending.get(),
                loadout.entryAt(moverId, destination),
                attach -> transfer.move(
                        moverId,
                        () -> {},
                        WorldTransfer.event(
                                WorldKind.WORLD_KIND_MAIN,
                                WorldChangeReason.WORLD_CHANGE_REASON_GM_MOVED,
                                destination,
                                loadout.maxHp()),
                        attach));
        if (!moved) {
            return Outcome.NOT_IN_MAIN_WORLD;
        }
        // Bẫy D6: vị trí lưu lúc thoát lấy từ đây — không cập nhật thì thoát ngay sau khi bị kéo lưu chỗ CŨ.
        registry.onCharacterPositionSampled(moverId, destination.x(), destination.y());
        channels.worldFacadeFor(moverId)
                .ifPresent(world -> CharacterLevelResync.afterMoveInto(world, characterFacade, moverId));
        return Outcome.OK;
    }

    /**
     * Mốc chỉ hỏng khi World cũ đang dừng (tắt server) — khi đó vẫn phải chạy nửa sau, nếu không người
     * dời kẹt với định tuyến trỏ vào World đã gỡ họ; {@code finishRelocation} tự bỏ qua nếu đã muộn.
     */
    private static void awaitOldWorldSilent(MainWorldChannelRegistry.PendingRelocation pending, long moverId) {
        try {
            pending.oldWorldSilent().join();
        } catch (CompletionException e) {
            LOG.warn("Chờ World cũ im khi dời nhân vật lỗi: characterId={}", moverId, e);
        }
    }
}
