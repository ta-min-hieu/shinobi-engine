package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ModelSidecar} chưa có test nào trước file này dù logic cắt {@code blend_file} tuyệt đối
 * về tương đối gốc repo ({@code toRepoRelative}) là đúng loại rủi ro theo-quy-ước đã gây lỗi thật ở
 * {@code AssetCategoryResolver} (NPC thứ hai/ba lọt danh mục sai, không đỏ gì cả).
 *
 * <p>Dữ liệu thật trên đĩa hôm nay (2026-09-19, 31 file {@code .model-meta.json}) xác nhận có ĐÚNG
 * hai hình dạng {@code blend_file} khác {@code assets/raw/blender/...} thẳng: một là đường dẫn sinh
 * ra TRONG một git worktree khác ({@code mob_archive-warden} —
 * {@code E:\...\.claude\worktrees\agent-xxx\assets\raw\...}, mốc {@code assets/raw/} vẫn xuất hiện
 * ĐÚNG MỘT LẦN ở cuối đường dẫn nên {@code lastIndexOf} vẫn cắt đúng — khoá lại bằng
 * {@code shouldCutAtLastOccurrence_whenBlendFileWasExportedInsideAWorktree}), hai là đường dẫn
 * KHÔNG chứa mốc đó chút nào ({@code char_hoa-b1} — trỏ vào một thư mục scratchpad đã xoá, lỗi
 * dữ liệu THẬT đang treo, xem ADR/bàn giao liên quan — KHÔNG được sửa ở đây, chỉ khoá lại rằng
 * {@code ModelSidecar} tự nó phải ném lỗi rõ ràng chứ không âm thầm trả về rác).
 */
final class ModelSidecarTest {

    @Test
    void shouldCutToRepoRelativePath_whenBlendFileIsAbsoluteWindowsPath(@TempDir Path modelsRoot) throws IOException {
        writeSidecar(
                modelsRoot,
                "char_hoa-b2",
                "E:\\\\study\\\\libgdx\\\\shinobi-engine\\\\assets\\\\raw\\\\blender\\\\characters\\\\hoa-b2.blend");

        ModelSidecar sidecar = ModelSidecar.readFrom(modelsRoot, "char_hoa-b2");

        assertThat(sidecar.blendPath()).isEqualTo("assets/raw/blender/characters/hoa-b2.blend");
    }

    // Dữ liệu thật của mob_archive-warden hôm nay: sidecar được sinh ra TRONG một worktree
    // (E:\...\.claude\worktrees\agent-xxx\assets\raw\blender\monsters\archive-warden.blend) —
    // đường dẫn tuyệt đối dài hơn nhưng mốc "assets/raw/" vẫn chỉ xuất hiện một lần ở cuối, nên
    // lastIndexOf vẫn phải cắt ra đúng phần tương đối gốc repo, không lẫn phần đường dẫn worktree.
    @Test
    void shouldCutAtLastOccurrence_whenBlendFileWasExportedInsideAWorktree(@TempDir Path modelsRoot)
            throws IOException {
        writeSidecar(
                modelsRoot,
                "mob_archive-warden",
                "E:\\\\study\\\\libgdx\\\\shinobi-engine\\\\.claude\\\\worktrees\\\\agent-a71da15313eba5d2f"
                        + "\\\\assets\\\\raw\\\\blender\\\\monsters\\\\archive-warden.blend");

        ModelSidecar sidecar = ModelSidecar.readFrom(modelsRoot, "mob_archive-warden");

        assertThat(sidecar.blendPath()).isEqualTo("assets/raw/blender/monsters/archive-warden.blend");
    }

    @Test
    void shouldThrow_whenSidecarFileMissing(@TempDir Path modelsRoot) {
        assertThatThrownBy(() -> ModelSidecar.readFrom(modelsRoot, "char_missing"))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("thiếu sidecar");
    }

    @Test
    void shouldThrow_whenBlendFileFieldMissing(@TempDir Path modelsRoot) throws IOException {
        Files.writeString(
                modelsRoot.resolve("char_no-blend-field.model-meta.json"), "{\"asset_key\":\"char_no-blend-field\"}");

        assertThatThrownBy(() -> ModelSidecar.readFrom(modelsRoot, "char_no-blend-field"))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("blend_file");
    }

    // Hình dạng lỗi THẬT của char_hoa-b1 hôm nay (không dùng lại chính file đó — đường dẫn scratchpad
    // đã xoá là lỗi dữ liệu đang treo, chờ chủ dự án dựng lại nguồn): blend_file trỏ ra ngoài
    // assets/raw/ hoàn toàn thì ModelSidecar phải ném lỗi rõ ràng, không được âm thầm trả rác vào
    // art_asset.blend_path.
    @Test
    void shouldThrow_whenBlendFileHasNoRawRootMarker(@TempDir Path modelsRoot) throws IOException {
        writeSidecar(
                modelsRoot,
                "char_outside-repo",
                "C:\\\\Users\\\\someone\\\\AppData\\\\Local\\\\Temp\\\\scratchpad\\\\final_outside-repo.blend");

        assertThatThrownBy(() -> ModelSidecar.readFrom(modelsRoot, "char_outside-repo"))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("assets/raw/");
    }

    private static void writeSidecar(Path modelsRoot, String assetKey, String blendFileJsonEscaped) throws IOException {
        Files.writeString(
                modelsRoot.resolve(assetKey + ".model-meta.json"),
                "{\"asset_key\":\"%s\",\"blend_file\":\"%s\"}".formatted(assetKey, blendFileJsonEscaped));
    }
}
