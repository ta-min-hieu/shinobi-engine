package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Nội dung {@code <asset_key>.model-meta.json} do {@code export_character_lods.py} ghi cạnh thư mục
 * model (ART-PIPELINE.md §8.6) — bản đối xứng của {@link RenderMeta} cho nhánh 3D.
 *
 * <p>Chỉ đọc đúng field mà bảng {@code art_asset} cần và <b>không</b> đo lại được từ chính file
 * {@code .glb}: {@code blend_file}. Số tam giác thì ngược lại — {@link ScannedModel} đếm thẳng
 * trong file thật, không chép {@code tri_counts} của sidecar, vì cái phải ghi vào DB là con số của
 * file sắp ship chứ không phải con số Blender tự khai lúc xuất.
 *
 * <p><b>Vì sao không suy {@code blend_path} bằng {@link BlendPathResolver} như nhánh 2D:</b> quy ước
 * "{@code asset_key} bỏ prefix = tên file {@code .blend}" đúng với 14/15 model hiện có nhưng SAI với
 * {@code char_hoa-b1} — bậc B1 của Hỏa xuất từ {@code hoa-text3d-pilot.blend} (ASSET-LOG.md), trong
 * khi {@code hoa-b1.blend} cũng tồn tại nhưng là file khác. Cột {@code blend_path} sinh ra để
 * "render lại được" (§8.5), nên ghi sai nguồn là hỏng đúng việc cột đó tồn tại để làm.
 */
record ModelSidecar(String blendPath) {

    /**
     * Mốc cắt đường dẫn tuyệt đối về tương đối gốc repo. Dùng {@code assets/raw/} chứ không phải
     * {@code assets/} cho hẹp nhất có thể — mọi file {@code .blend} đều nằm dưới mốc này
     * (ART-PIPELINE.md §7).
     */
    private static final String RAW_ROOT_MARKER = "assets/raw/";

    private static final String SIDECAR_SUFFIX = ".model-meta.json";

    static ModelSidecar readFrom(Path modelsRoot, String assetKey) {
        Path sidecar = modelsRoot.resolve(assetKey + SIDECAR_SUFFIX);
        if (!Files.isRegularFile(sidecar)) {
            throw new AssetPipelineException("Model '" + assetKey + "' thiếu sidecar " + sidecar
                    + " — export_character_lods.py phải ghi file này cạnh thư mục model"
                    + " (ART-PIPELINE.md §8.6)");
        }
        JsonValue root = parse(sidecar);
        if (!root.has("blend_file")) {
            throw new AssetPipelineException(
                    "Sidecar " + sidecar + " thiếu field 'blend_file' — không suy ra được art_asset.blend_path");
        }
        return new ModelSidecar(toRepoRelative(root.getString("blend_file"), sidecar));
    }

    private static JsonValue parse(Path sidecar) {
        try {
            return new JsonReader().parse(Files.readString(sidecar));
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được sidecar model: " + sidecar, e);
        }
    }

    // Sidecar ghi đường dẫn TUYỆT ĐỐI của máy đã xuất ("E:\\study\\...\\assets\\raw\\blender\\...").
    // art_asset chỉ chứa đường dẫn tương đối (§8.4 — không nhúng hostname hay ổ đĩa của một máy cụ
    // thể, để đổi origin/đổi máy không phải sửa DB), nên cắt từ mốc gốc repo.
    private static String toRepoRelative(String blendFile, Path sidecar) {
        String normalized = blendFile.replace('\\', '/');
        int cut = normalized.lastIndexOf(RAW_ROOT_MARKER);
        if (cut < 0) {
            throw new AssetPipelineException("blend_file trong " + sidecar + " không nằm dưới '"
                    + RAW_ROOT_MARKER + "': " + blendFile
                    + " — art_asset.blend_path phải là đường dẫn tương đối gốc repo (ART-PIPELINE.md §8.4)");
        }
        return normalized.substring(cut);
    }
}
