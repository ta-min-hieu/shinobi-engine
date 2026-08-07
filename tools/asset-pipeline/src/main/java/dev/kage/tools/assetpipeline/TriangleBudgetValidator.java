package dev.kage.tools.assetpipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Gác ngân sách tam giác từng tier (ART-PIPELINE.md §4.4, ngưỡng nằm ở {@link GraphicsTier}) — bằng
 * máy, đúng tinh thần {@link NamingValidator}: model vượt ngân sách làm <b>fail build</b>, không
 * phải warning trôi qua rồi phát hiện lúc frame rate tụt trên máy người chơi.
 *
 * <p>Vì sao gác trên file thật thay vì tin bước xuất: {@code export_character_lods.py} gọi decimate
 * theo <i>tỉ lệ</i>, mà decimate không bảo đảm chạm đúng con số mong muốn. Cái phải nằm trong ngân
 * sách là file sẽ ship cho người chơi, không phải con số ghi trong sidecar {@code model-meta.json}.
 *
 * <p>Báo <b>gộp</b> mọi vi phạm trong một lần ném, không dừng ở model đầu tiên: khi cả một mẻ xuất
 * bị lệch ngưỡng thì biết ngay là lệch cả mẻ (chỉnh tham số decimate) hay lẻ một file (chỉnh riêng
 * file đó) — dừng ở file đầu thì phải chạy lại 45 lần mới thấy hết.
 */
final class TriangleBudgetValidator {

    private TriangleBudgetValidator() {}

    static void validateAll(List<ScannedModel> models) {
        List<String> violations = new ArrayList<>();
        for (ScannedModel model : models) {
            if (model.triangleCount() > model.tier().maxTriangles) {
                violations.add(describe(model));
            }
        }
        if (!violations.isEmpty()) {
            throw new AssetPipelineException(violations.size() + "/" + models.size()
                    + " model vượt ngân sách tam giác (ART-PIPELINE.md §4.4) — decimate lại, đừng nới ngưỡng:\n"
                    + String.join("\n", violations));
        }
    }

    private static String describe(ScannedModel model) {
        return "  - " + model.file() + ": tier " + model.tier().folderName + " đo được "
                + model.triangleCount() + " tam giác, trần " + model.tier().maxTriangles + " (vượt "
                + (model.triangleCount() - model.tier().maxTriangles) + ")";
    }
}
