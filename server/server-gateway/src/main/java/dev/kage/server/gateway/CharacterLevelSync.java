package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

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
 * <p><b>World nào nhận.</b> World chính và hầm ngục: cả hai đều là nơi EXP hạ quái sinh ra, cùng một
 * {@code SkillSystem}. <b>Đấu trường thì KHÔNG</b>: cấp trong trận giữ nguyên như lúc vào trận (hành
 * vi trước bản sửa này), vì chưa tài liệu nào chốt việc mở khoá Tuyệt Kỹ GIỮA một trận đấu người với
 * người — đường về world chính sau trận đọc lại cấp từ DB ({@link CharacterLoadout#load}) nên cấp mới
 * có hiệu lực ngay khi trận xong, không cần đăng nhập lại.
 *
 * <p><b>Giới hạn đã biết.</b> Nhân vật lên cấp ĐÚNG lúc đang được dời World (vào/ra hầm ngục): lệnh
 * tới World cũ và bị bỏ qua khi họ đã rời, còn lệnh attach vào World mới có thể mang cấp đọc từ DB
 * TRƯỚC khi cú lên cấp commit. Khi đó World mới giữ cấp cũ tới lần lên cấp/dời World/đăng nhập kế.
 * Khe này rộng cỡ một lượt đọc DB cộng một tick; cấp không bao giờ lùi vì world chỉ nâng.
 *
 * <p>Thread-safe: bảng định tuyến là các registry tự khoá, {@link WorldFacade#enqueueCharacterLevel}
 * chỉ xếp lệnh vào hàng đợi của tick thread. Gọi từ Virtual Thread, không bao giờ từ tick thread.
 */
final class CharacterLevelSync {

    private final LongFunction<Optional<WorldFacade>> duelWorldOf;
    private final WorldRouteLookup worldsFollowingLevel;

    /**
     * @param duelWorldOf World đấu trường nhân vật đang ở, rỗng nếu không trong trận
     * @param worldsFollowingLevel các World có cấp đi theo cú lên cấp giữa phiên
     */
    CharacterLevelSync(LongFunction<Optional<WorldFacade>> duelWorldOf, WorldRouteLookup worldsFollowingLevel) {
        this.duelWorldOf = duelWorldOf;
        this.worldsFollowingLevel = worldsFollowingLevel;
    }

    /** Định tuyến production — xem "World nào nhận" ở javadoc lớp. */
    static CharacterLevelSync of(WorldGateway worldGateway) {
        return new CharacterLevelSync(
                worldGateway.arenaInstanceRegistry()::worldFacadeFor,
                new WorldRouteLookup(List.of(
                        worldGateway.dungeonInstanceRegistry()::worldFacadeFor,
                        worldGateway.mainWorldChannels()::worldFacadeFor)));
    }

    /**
     * Bản không báo World nào — trạng thái của {@link ConnectedCharacterRegistry} trước khi
     * {@code GatewayBootstrap} nối bản thật, và của test không dựng World.
     */
    static CharacterLevelSync unbound() {
        return new CharacterLevelSync(CharacterLevelSync::noWorld, new WorldRouteLookup(List.of()));
    }

    private static Optional<WorldFacade> noWorld(long characterId) {
        return Optional.empty();
    }

    /**
     * Báo cấp mới cho World nhân vật đang ở nếu {@code result} là một cú lên cấp; không thì không làm
     * gì. Nhân vật không ở World nào (đã ngắt kết nối) được bỏ qua êm — cấp đã nằm trong DB và lần
     * attach kế đọc lại.
     */
    void onExperienceGranted(long characterId, GrantExperienceUseCase.Result result) {
        if (!result.leveledUp() || duelWorldOf.apply(characterId).isPresent()) {
            return;
        }
        int level = result.progress().level();
        worldsFollowingLevel.find(characterId).ifPresent(world -> world.enqueueCharacterLevel(characterId, level));
    }
}
