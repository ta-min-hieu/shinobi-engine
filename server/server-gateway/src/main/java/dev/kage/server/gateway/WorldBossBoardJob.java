package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldBossBoardEntry;
import dev.kage.protocol.WorldBossBoardEvent;
import dev.kage.server.world.WorldBossStanding;
import dev.kage.server.world.WorldFacade;

/**
 * Bảng sát thương boss thế giới trực tiếp (ADR-157). Mỗi {@value #PERIOD_SECONDS} giây xin ảnh chụp sổ ở khu vực
 * đầu tiên (nơi boss xuất hiện — ADR-081); tick thread trả ảnh chụp, việc dựng gói chạy trên Virtual Thread. Mỗi người
 * có tên trong sổ nhận top {@value #TOP} + hạng/sát thương của mình. Boss biến khỏi sổ (chết) ⇒ gửi một gói
 * {@code ended} cho những người đã từng thấy bảng của nó. Chỉ hiển thị — thưởng vẫn theo ADR-081.
 */
final class WorldBossBoardJob {

    static final long PERIOD_SECONDS = 2L;
    static final int TOP = 5;

    private static final Executor PUBLISH = Executors.newVirtualThreadPerTaskExecutor();

    private final WorldFacade firstChannel;
    private final ConnectedCharacterRegistry registry;
    private final CharacterFacade characters;
    /** Mã boss → người đã nhận bảng của nó ở lượt trước (để báo ended). Chỉ chạm trong {@link #publish}. */
    private final Map<String, Set<Long>> lastViewers = new HashMap<>();

    WorldBossBoardJob(WorldFacade firstChannel, ConnectedCharacterRegistry registry, CharacterFacade characters) {
        this.firstChannel = firstChannel;
        this.registry = registry;
        this.characters = characters;
    }

    ExpansionJobs.Job job() {
        return new ExpansionJobs.Job(
                "world-boss-board",
                PERIOD_SECONDS,
                () -> firstChannel.requestWorldBossStandings(standings -> PUBLISH.execute(() -> publish(standings))));
    }

    /** Virtual Thread; {@code synchronized} vì hai lượt có thể chồng nhau khi DB chậm. */
    synchronized void publish(List<WorldBossStanding> standings) {
        Set<String> alive = new HashSet<>();
        for (WorldBossStanding standing : standings) {
            alive.add(standing.bossCode());
            if (!standing.damageByCharacter().isEmpty()) {
                lastViewers.put(standing.bossCode(), sendBoard(standing));
            }
        }
        List<String> gone = new ArrayList<>(lastViewers.keySet());
        gone.removeAll(alive);
        for (String bossCode : gone) {
            byte[] ended = WorldBossBoardEvent.newBuilder()
                    .setBossCode(bossCode)
                    .setEnded(true)
                    .build()
                    .toByteArray();
            lastViewers
                    .remove(bossCode)
                    .forEach(id ->
                            registry.sendToCharacterIfOnline(id, Opcode.OPCODE_WORLD_BOSS_BOARD_EVENT_VALUE, 0, ended));
        }
    }

    private Set<Long> sendBoard(WorldBossStanding standing) {
        List<Map.Entry<Long, Long>> ranked =
                new ArrayList<>(standing.damageByCharacter().entrySet());
        ranked.sort(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        WorldBossBoardEvent.Builder base = WorldBossBoardEvent.newBuilder()
                .setBossCode(standing.bossCode())
                .setParticipants(ranked.size());
        for (int i = 0; i < Math.min(TOP, ranked.size()); i++) {
            long id = ranked.get(i).getKey();
            base.addTop(WorldBossBoardEntry.newBuilder()
                    .setName(characters.findById(id).map(Character::name).orElse("#" + id))
                    .setDamage(ranked.get(i).getValue()));
        }
        Set<Long> viewers = new HashSet<>();
        for (int i = 0; i < ranked.size(); i++) {
            long id = ranked.get(i).getKey();
            if (registry.isOnline(id)) {
                registry.sendToCharacterIfOnline(
                        id,
                        Opcode.OPCODE_WORLD_BOSS_BOARD_EVENT_VALUE,
                        0,
                        base.clone()
                                .setMyRank(i + 1)
                                .setMyDamage(ranked.get(i).getValue())
                                .build()
                                .toByteArray());
                viewers.add(id);
            }
        }
        return viewers;
    }
}
