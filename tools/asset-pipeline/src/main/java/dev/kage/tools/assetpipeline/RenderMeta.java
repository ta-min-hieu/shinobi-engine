package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Nội dung {@code render-meta.json} do {@code kage_sprite_render.py}/{@code build_skill_vfx.py}
 * sinh ra (ART-PIPELINE.md §8.3) — chỉ đọc 2 field pipeline này thật sự cần: {@code actions} (tên
 * action -> số frame đã render, dùng để validate đủ file) và {@code masterPx} (để tính kích thước
 * tier 1x/0.5x). Icon tĩnh không có file này — xem {@link AssetFolder#renderMeta()} = null.
 */
record RenderMeta(Map<String, Integer> actions, int masterPx) {

    static RenderMeta readFrom(Path renderMetaJson) throws IOException {
        String content = Files.readString(renderMetaJson);
        JsonValue root = new JsonReader().parse(content);
        Map<String, Integer> actions = new LinkedHashMap<>();
        for (JsonValue entry = root.get("actions").child; entry != null; entry = entry.next) {
            actions.put(entry.name(), entry.asInt());
        }
        int masterPx = root.getInt("master_px");
        return new RenderMeta(actions, masterPx);
    }
}
