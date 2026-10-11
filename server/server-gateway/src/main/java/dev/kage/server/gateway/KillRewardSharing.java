package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

import dev.kage.party.api.PartyFacade;
import dev.kage.server.world.WorldFacade;

/**
 * Ai, NGOÀI người ra đòn cuối, được hưởng một cú hạ quái (quyết định chủ dự án 2026-10-11): mọi thành
 * viên cùng nhóm đang ở CÙNG World với người hạ.
 *
 * <p>Trước đó chỉ đòn cuối được tính: cả nhóm đánh một con boss hầm ngục thì đúng một người có EXP và
 * tiến độ nhiệm vụ, những người còn lại phải tranh nhau nhát chốt.
 *
 * <ul>
 *   <li><b>Được chia:</b> EXP (mỗi người nhận ĐỦ con số của con quái, không chia đều) và tiến độ
 *       nhiệm vụ "hạ quái".
 *   <li><b>Không chia:</b> đồ rơi — vẫn chỉ của người ra đòn cuối.
 *   <li><b>"Cùng World"</b> là cùng một {@link WorldFacade}: cùng lượt hầm ngục, hoặc cùng kênh của
 *       cùng khu vực world chính. Đồng đội ở kênh khác, đang trong trận đấu, hay đã thoát game thì
 *       không được gì. Không xét khoảng cách và không xét còn sống hay đã gục.
 * </ul>
 *
 * <p>Đọc danh sách nhóm là chạm DB: chỉ gọi trên Virtual Thread, không bao giờ trên tick thread.
 */
final class KillRewardSharing {

    private final LongFunction<List<Long>> partyMemberIdsOf;
    private final LongFunction<Optional<WorldFacade>> worldOf;

    /**
     * @param partyMemberIdsOf mọi thành viên nhóm của một nhân vật (kể cả chính họ); rỗng nếu không có nhóm
     * @param worldOf World nhân vật đang ở; rỗng nếu không ở World nào
     */
    KillRewardSharing(LongFunction<List<Long>> partyMemberIdsOf, LongFunction<Optional<WorldFacade>> worldOf) {
        this.partyMemberIdsOf = partyMemberIdsOf;
        this.worldOf = worldOf;
    }

    /** Nối production: nhóm đọc từ {@link PartyFacade}, World tra qua cả ba bảng định tuyến. */
    static KillRewardSharing of(PartyFacade partyFacade, WorldGateway worldGateway) {
        WorldRouteLookup routes = WorldRouteLookup.of(worldGateway);
        return new KillRewardSharing(
                characterId -> partyFacade.listMembers(characterId).stream()
                        .map(PartyFacade.MemberView::characterId)
                        .toList(),
                routes::find);
    }

    /**
     * Bản không chia cho ai — trạng thái của {@link ConnectedCharacterRegistry} trước khi
     * {@code GatewayBootstrap} nối bản thật, và của test không dựng nhóm.
     */
    static KillRewardSharing none() {
        return new KillRewardSharing(characterId -> List.of(), characterId -> Optional.empty());
    }

    /** Đồng đội được chia phần — KHÔNG gồm {@code killerCharacterId}. Rỗng nếu đánh một mình. */
    List<Long> teammatesSharingWith(long killerCharacterId) {
        List<Long> memberIds = partyMemberIdsOf.apply(killerCharacterId);
        if (memberIds.isEmpty()) {
            return List.of();
        }
        Optional<WorldFacade> killerWorld = worldOf.apply(killerCharacterId);
        if (killerWorld.isEmpty()) {
            return List.of(); // người hạ đã rời World giữa chừng — không còn "cùng World" để so.
        }
        List<Long> sharing = new ArrayList<>(memberIds.size());
        for (long memberId : memberIds) {
            if (memberId != killerCharacterId && isInWorld(memberId, killerWorld.get())) {
                sharing.add(memberId);
            }
        }
        return sharing;
    }

    /**
     * Trao phần của từng đồng đội: tiến độ nhiệm vụ rồi EXP. Chạy NGAY trên luồng gọi (không xếp thêm
     * việc mới) để {@code ConnectedCharacterRegistry.stop()} chờ được hết; từng phần vẫn cô lập lỗi,
     * ghi log theo id của người ĐƯỢC chia. Lớp gọi đưa hai việc vào vì chúng cầm cổng DB và kết nối.
     */
    void shareWithTeammates(
            long killerCharacterId, String monsterCode, LongConsumer questProgress, LongConsumer experience) {
        for (long teammateId : teammatesSharingWith(killerCharacterId)) {
            KillRewardDispatcher.runIsolated(
                    KillRewardDispatcher.Step.QUEST_PROGRESS,
                    teammateId,
                    monsterCode,
                    () -> questProgress.accept(teammateId));
            KillRewardDispatcher.runIsolated(
                    KillRewardDispatcher.Step.EXPERIENCE, teammateId, monsterCode, () -> experience.accept(teammateId));
        }
    }

    // So bằng ĐỊNH DANH có chủ đích: hai World khác nhau không bao giờ "bằng nhau" theo nghĩa nào khác.
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private boolean isInWorld(long characterId, WorldFacade world) {
        Optional<WorldFacade> current = worldOf.apply(characterId);
        return current.isPresent() && current.get() == world;
    }
}
