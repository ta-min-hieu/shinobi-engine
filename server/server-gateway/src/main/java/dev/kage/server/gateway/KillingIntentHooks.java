package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.server.world.PlayerVersusPlayerRule;
import dev.kage.world.api.KillingIntentFacade;
import dev.kage.world.domain.KillingIntentRules;
import dev.kage.world.domain.PvpZone;

/**
 * Sát khí (ADR-142). {@link #onCharacterDefeated} đến từ tick thread ⇒ chỉ lọc bằng RAM (vị trí cuối của người bị hạ,
 * dải PvP, ảnh chụp Ma Đầu) rồi đẩy việc DB sang Virtual Thread:
 * <ul>
 *   <li>người chơi hạ người chơi KHÔNG phải Ma Đầu, người bị hạ đứng trong dải PvP tự do, không phải bang chiến ⇒
 *       người hạ +1 sát khí;
 *   <li>Ma Đầu chết (bởi bất kỳ ai) ⇒ rơi vàng, về tay người hạ nếu là người chơi.
 * </ul>
 * Ảnh chụp Ma Đầu (mảng id đã sắp xếp) làm mới mỗi {@value #REFRESH_SECONDS} giây và ngay khi ai đó vừa thành Ma Đầu;
 * ai đổi trạng thái thì World đang chứa họ phát lại gói ngoại hình 307 (nhãn đỏ trên đầu).
 */
final class KillingIntentHooks {

    private static final Logger LOG = LoggerFactory.getLogger(KillingIntentHooks.class);

    private static final long REFRESH_SECONDS = 30L;

    /** Thứ việc này cần. */
    record Deps(
            KillingIntentFacade facade,
            CharacterFacade characters,
            List<PvpZone> pvpZones,
            ConnectedCharacterRegistry registry,
            WorldRouteLookup routes,
            Clock clock) {}

    /** Một lần hạ, chụp trên tick thread. */
    private record Kill(long victimId, long killerId, boolean counts, boolean drops) {}

    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn GuildWarHooks.
    private volatile Optional<Deps> deps = Optional.empty();
    private volatile float[] pvpRanges = new float[0];
    private volatile long[] outlaws = new long[0];

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        float[] ranges = new float[realDeps.pvpZones().size() * 2];
        for (int i = 0; i < realDeps.pvpZones().size(); i++) {
            ranges[i * 2] = realDeps.pvpZones().get(i).minX();
            ranges[i * 2 + 1] = realDeps.pvpZones().get(i).maxX();
        }
        this.pvpRanges = ranges;
        this.deps = Optional.of(realDeps);
    }

    /** Nhịp làm mới ảnh chụp Ma Đầu (phân rã theo thời gian). */
    ExpansionJobs.Job job() {
        return new ExpansionJobs.Job("sát khí", REFRESH_SECONDS, () -> deps.ifPresent(this::refresh));
    }

    /**
     * Luật PvP của world chính: cặp đánh nhau theo bang chiến ({@code guildWar}) + Ma Đầu bị truy trong dải PvP kể cả ở
     * trạm an toàn ({@link PlayerVersusPlayerRule#isHunted}).
     */
    PlayerVersusPlayerRule ruleWith(PlayerVersusPlayerRule guildWar) {
        return new PlayerVersusPlayerRule() {
            @Override
            public boolean allows(long attackerCharacterId, long targetCharacterId) {
                return guildWar.allows(attackerCharacterId, targetCharacterId);
            }

            @Override
            public boolean isHunted(long targetCharacterId) {
                return isOutlaw(targetCharacterId);
            }
        };
    }

    /** Ma Đầu lúc lần làm mới gần nhất — đọc RAM, gọi được trên tick thread. */
    boolean isOutlaw(long characterId) {
        return Arrays.binarySearch(outlaws, characterId) >= 0;
    }

    /**
     * Gọi trên tick thread — không chạm DB ở đây.
     *
     * @param victimAt vị trí cuối ở world chính của người bị hạ (rỗng nếu không có)
     * @param wars ai đang trong giờ bang chiến — hai người cùng đang chiến thì là lần hạ bang chiến, không sinh sát khí
     */
    void onCharacterDefeated(
            long victimId, long killerEntityId, Optional<PositionSnapshot> victimAt, GuildWarRoster wars) {
        boolean drops = isOutlaw(victimId);
        boolean counts = killerEntityId > 0
                && !drops
                && !(wars.isAtWar(victimId) && wars.isAtWar(killerEntityId))
                && victimAt.isPresent()
                && inPvpRange(victimAt.get().x());
        if (deps.isPresent() && (counts || drops)) {
            Deps d = deps.get();
            Kill kill = new Kill(victimId, Math.max(0L, killerEntityId), counts, drops);
            writers.execute(() -> settle(d, kill));
        }
    }

    /** ADR-194 — vị trí (nếu có) nằm trong dải PvP tự do; đọc RAM, gọi được trên tick thread. */
    boolean inPvpZone(Optional<PositionSnapshot> at) {
        return at.isPresent() && inPvpRange(at.get().x());
    }

    private boolean inPvpRange(float x) {
        float[] ranges = pvpRanges;
        for (int i = 0; i < ranges.length; i += 2) {
            if (x >= ranges[i] && x <= ranges[i + 1]) {
                return true;
            }
        }
        return false;
    }

    // PMD AvoidCatchingGenericException: việc phụ chạy trên Virtual Thread riêng — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void settle(Deps d, Kill kill) {
        try {
            Instant now = d.clock().instant();
            if (kill.drops()) {
                dropGold(d, kill, now);
            }
            if (kill.counts()) {
                addPoint(d, kill.killerId(), now);
            }
        } catch (RuntimeException e) {
            LOG.warn("Ghi sát khí lỗi: victim={}, killer={}", kill.victimId(), kill.killerId(), e);
        }
    }

    private void dropGold(Deps d, Kill kill, Instant now) {
        long amount = d.facade().dropGold(kill.victimId(), kill.killerId(), now);
        if (amount <= 0L) {
            return;
        }
        line(
                d,
                kill.victimId(),
                "Ma Đầu tử trận: rơi " + amount + " linh thạch. / Outlaw slain: dropped " + amount + " spirit stones.");
        if (kill.killerId() > 0L) {
            line(
                    d,
                    kill.killerId(),
                    "Trừ ma vệ đạo: nhận " + amount + " linh thạch của Ma Đầu. / Outlaw purged: " + amount
                            + " spirit stones claimed.");
        }
    }

    private void addPoint(Deps d, long killerId, Instant now) {
        int points = d.facade().recordKill(killerId, now);
        line(
                d,
                killerId,
                "Sát khí +1 (" + points + "/" + KillingIntentRules.MAX_POINTS + "). / Killing intent +1 (" + points
                        + "/" + KillingIntentRules.MAX_POINTS + ").");
        if (points == KillingIntentRules.OUTLAW_POINTS) {
            String name = d.characters().findById(killerId).map(Character::name).orElse("?");
            broadcast(
                    d,
                    name + " sát khí ngút trời, thành MA ĐẦU — trạm Hoang Vực không còn che chở! / " + name
                            + " has become an OUTLAW — the Wasteland posts no longer shelter them!");
            refresh(d);
        }
    }

    /** Đọc lại Ma Đầu; ai đổi trạng thái thì phát lại ngoại hình (nhãn trên đầu). */
    private void refresh(Deps d) {
        Set<Long> next = d.facade().outlaws(d.clock().instant());
        long[] sorted = next.stream().mapToLong(Long::longValue).sorted().toArray();
        Set<Long> changed = new HashSet<>(next);
        for (long id : outlaws) {
            if (!changed.remove(id)) {
                changed.add(id);
            }
        }
        outlaws = sorted;
        for (long id : changed) {
            d.routes().find(id).ifPresent(world -> world.enqueueAppearanceChanged(id));
        }
    }

    private static void line(Deps d, long characterId, String body) {
        d.registry().sendToCharacterIfOnline(characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, chat(body));
    }

    private static void broadcast(Deps d, String body) {
        d.registry().broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, chat(body));
    }

    private static byte[] chat(String body) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build()
                .toByteArray();
    }

    /** Tắt server có trật tự — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        writers.shutdown();
    }
}
