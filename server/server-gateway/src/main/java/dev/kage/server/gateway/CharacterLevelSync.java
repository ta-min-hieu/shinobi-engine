package dev.kage.server.gateway;

import java.util.List;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.server.world.WorldFacade;

/**
 * Đưa cú LÊN CẤP giữa phiên vào World nhân vật đang đứng — nửa "luật trong world" của việc lên cấp;
 * nửa "người chơi nhìn thấy" là {@code ExperienceChangedEvent} của
 * {@link ConnectedCharacterRegistry#sendExperience}.
 *
 * <p><b>Vì sao có lớp này (2026-10-10).</b> {@code CharacterLevelComponent} của server-world chỉ được
 * gắn lúc attach. Lên Lv50 giữa phiên thì HUD báo Lv50 nhưng cổng Tuyệt Kỹ vẫn thấy cấp lúc đăng
 * nhập, tới khi đăng nhập lại. Mọi kết quả cộng EXP — hạ quái ({@code lootExecutor}) lẫn đồng bộ sau
 * thưởng quest — đều đi qua {@code sendExperience}, nên đó là ĐIỂM DUY NHẤT gọi vào đây; không đường
 * cộng EXP nào phải tự nhớ mà báo world.
 *
 * <p><b>World nào nhận.</b> World nhân vật ĐANG ĐỨNG — đấu trường, hầm ngục hay world chính, tra theo
 * thứ tự đó (định tuyến ưu tiên giống {@code GatewayChannelHandler.currentWorldFacade}). Đấu trường
 * từng bị loại trừ (cấp trong trận đứng yên tới hết trận); từ 2026-10-17 chủ dự án chốt lên cấp giữa
 * trận Arena có hiệu lực NGAY như ở hầm ngục (README dòng 79(a)).
 *
 * <p><b>Lên cấp ĐÚNG lúc đang được dời World</b> (vào/ra hầm ngục, về từ đấu trường): lệnh của lớp
 * này có thể tới World cũ hoặc tới World mới khi thực thể chưa được gắn, và bị bỏ qua. Từ 2026-10-11
 * khe đó do {@link CharacterLevelResync} bịt: mỗi lượt dời World đọc lại cấp từ DB sau khi dời và báo
 * cho World mới. Cấp không bao giờ lùi vì world chỉ nâng.
 *
 * <p>Thread-safe: bảng định tuyến là các registry tự khoá, {@link WorldFacade#enqueueCharacterLevel}
 * chỉ xếp lệnh vào hàng đợi của tick thread. Gọi từ Virtual Thread, không bao giờ từ tick thread.
 */
final class CharacterLevelSync {

    private final WorldRouteLookup worldsFollowingLevel;

    /** @param worldsFollowingLevel các World có cấp đi theo cú lên cấp giữa phiên, theo thứ tự ưu tiên */
    CharacterLevelSync(WorldRouteLookup worldsFollowingLevel) {
        this.worldsFollowingLevel = worldsFollowingLevel;
    }

    /** Định tuyến production — xem "World nào nhận" ở javadoc lớp. */
    static CharacterLevelSync of(WorldGateway worldGateway) {
        return new CharacterLevelSync(new WorldRouteLookup(List.of(
                worldGateway.arenaInstanceRegistry()::worldFacadeFor,
                worldGateway.dungeonInstanceRegistry()::worldFacadeFor,
                worldGateway.mainWorldChannels()::worldFacadeFor)));
    }

    /**
     * Bản không báo World nào — trạng thái của {@link ConnectedCharacterRegistry} trước khi
     * {@code GatewayBootstrap} nối bản thật, và của test không dựng World.
     */
    static CharacterLevelSync unbound() {
        return new CharacterLevelSync(new WorldRouteLookup(List.of()));
    }

    /**
     * Báo cấp mới cho World nhân vật đang ở nếu {@code result} là một cú lên cấp; không thì không làm
     * gì. Nhân vật không ở World nào (đã ngắt kết nối) được bỏ qua êm — cấp đã nằm trong DB và lần
     * attach kế đọc lại.
     */
    void onExperienceGranted(long characterId, GrantExperienceUseCase.Result result) {
        if (!result.leveledUp()) {
            return;
        }
        int level = result.progress().level();
        worldsFollowingLevel.find(characterId).ifPresent(world -> world.enqueueCharacterLevel(characterId, level));
    }
}
