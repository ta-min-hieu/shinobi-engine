package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.kage.protocol.WorldKind;

/**
 * Bộ bệ phía client phải khớp World server đang chạy — server chỉ nạp {@code DEMO_PLATFORMS} cho
 * world chính ({@code WorldFacade}, {@code spawnOverworldContent}).
 *
 * <p>Trước 2026-09-30 client nạp ba bệ ở MỌI World: trong bí cảnh nó dự đoán đứng trên bệ cao 0,8m
 * còn server nói {@code z=0}, lệch dưới ngưỡng snap 1,0 nên không bao giờ được sửa.
 */
class DemoPlatformsTest {

    @Test
    void mainWorldHasTheThreeDemoPlatforms() {
        assertThat(DemoPlatforms.physics(WorldKind.WORLD_KIND_MAIN)).hasSize(3);
    }

    @Test
    void theNoArgumentFormIsTheMainWorld() {
        // Hai công cụ debug (CharacterOrientationDebugTool, WorldModelPreviewTool) vẫn gọi bản không
        // tham số — chúng vẽ world chính, nên bản đó phải đúng bằng bản MAIN.
        assertThat(DemoPlatforms.physics()).isEqualTo(DemoPlatforms.physics(WorldKind.WORLD_KIND_MAIN));
    }

    @ParameterizedTest
    @EnumSource(
            value = WorldKind.class,
            names = {"WORLD_KIND_DUNGEON", "WORLD_KIND_ARENA"})
    void instancedWorldsHaveNoPlatforms(WorldKind kind) {
        assertThat(DemoPlatforms.physics(kind)).isEmpty();
    }
}
