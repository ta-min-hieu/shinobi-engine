package dev.kage.tools.assetpipeline;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Đếm số tam giác thật trong một file {@code .glb} (glTF 2.0 dạng nhị phân tự chứa —
 * ART-PIPELINE.md §8.6 giải thích vì sao là {@code .glb} chứ không phải bộ ba {@code .gltf}).
 *
 * <p>Chỉ đọc <b>chunk JSON</b> (chunk đầu tiên, spec bắt buộc) rồi cộng dồn theo từng primitive —
 * không giải mã buffer nhị phân, nên không phụ thuộc thư viện glTF nào và không nạp 3,7 MB hình học
 * vào bộ nhớ chỉ để lấy một con số.
 *
 * <p>Số tam giác của một primitive suy ra từ <b>số đỉnh</b> (accessor {@code indices} nếu có, không
 * thì accessor {@code POSITION}) và {@code mode}: {@code TRIANGLES} chia 3, còn
 * {@code TRIANGLE_STRIP}/{@code TRIANGLE_FAN} là {@code n - 2} vì mọi đỉnh sau đỉnh thứ ba đều tái
 * dùng hai đỉnh trước. Mode điểm/đường không sinh tam giác nào. Tổng của một mesh rồi nhân với số
 * node trỏ tới nó — xem {@link #drawsPerMesh}.
 */
final class GlbTriangleCounter {

    /** {@code 'g','l','T','F'} đọc little-endian — 12 byte header theo spec glTF 2.0 §4.4.1. */
    private static final int GLB_MAGIC = 0x46546C67;

    /** {@code 'J','S','O','N'} — loại chunk đầu tiên, spec bắt buộc phải có và phải đứng trước BIN. */
    private static final int CHUNK_TYPE_JSON = 0x4E4F534A;

    private static final int HEADER_BYTES = 12;
    private static final int CHUNK_HEADER_BYTES = 8;

    // glTF 2.0 §3.7.2 "primitive.mode" — chỉ 3 mode dưới đây sinh tam giác.
    private static final int MODE_TRIANGLES = 4;
    private static final int MODE_TRIANGLE_STRIP = 5;
    private static final int MODE_TRIANGLE_FAN = 6;

    private static final int VERTICES_PER_TRIANGLE = 3;

    /** Strip/fan: hai đỉnh đầu chỉ "mồi", từ đỉnh thứ ba trở đi mỗi đỉnh thêm đúng 1 tam giác. */
    private static final int STRIP_PRIMING_VERTICES = 2;

    /** {@code indices} là index vào mảng accessor nên luôn {@code >= 0}; -1 = primitive không có. */
    private static final int NO_ACCESSOR = -1;

    /** {@code accessor.count} theo spec luôn {@code >= 1}; -1 = field bắt buộc bị thiếu. */
    private static final int MISSING_COUNT = -1;

    private static final String EXT_GPU_INSTANCING = "EXT_mesh_gpu_instancing";

    private GlbTriangleCounter() {}

    static int countTriangles(Path glbFile) {
        return countTrianglesInJson(readJsonChunk(glbFile), glbFile);
    }

    private static String readJsonChunk(Path glbFile) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(glbFile))) {
            ByteBuffer header = littleEndian(readExactly(input, HEADER_BYTES, glbFile, "header GLB"));
            if (header.getInt() != GLB_MAGIC) {
                throw new AssetPipelineException(
                        "File '" + glbFile + "' không phải glTF nhị phân (thiếu magic 'glTF')");
            }
            header.getInt(); // version — không dùng: mọi thứ đọc ở đây giống hệt giữa glTF 2.x
            header.getInt(); // tổng độ dài file — đã có Files.size() nếu cần, không kiểm chéo ở đây

            ByteBuffer chunkHeader = littleEndian(readExactly(input, CHUNK_HEADER_BYTES, glbFile, "header chunk đầu"));
            int chunkLength = chunkHeader.getInt();
            if (chunkHeader.getInt() != CHUNK_TYPE_JSON) {
                throw new AssetPipelineException(
                        "File '" + glbFile + "' có chunk đầu không phải JSON — sai spec glTF 2.0 §4.4.3");
            }
            byte[] jsonBytes = readExactly(input, chunkLength, glbFile, "chunk JSON");
            return new String(jsonBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được model: " + glbFile, e);
        }
    }

    private static byte[] readExactly(InputStream input, int length, Path glbFile, String part) throws IOException {
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new AssetPipelineException(
                    "File '" + glbFile + "' cụt ở " + part + " (đọc được " + bytes.length + "/" + length + " byte)");
        }
        return bytes;
    }

    private static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int countTrianglesInJson(String gltfJson, Path glbFile) {
        JsonValue root = new JsonReader().parse(gltfJson);
        JsonValue meshes = root.get("meshes");
        if (meshes == null) {
            return 0;
        }
        JsonValue accessors = root.get("accessors");
        int[] drawsPerMesh = drawsPerMesh(root.get("nodes"), meshes.size, glbFile);
        long total = 0;
        int meshIndex = 0;
        for (JsonValue mesh = meshes.child; mesh != null; mesh = mesh.next, meshIndex++) {
            JsonValue primitives = mesh.get("primitives");
            if (primitives == null) {
                continue;
            }
            long meshTriangles = 0;
            for (JsonValue primitive = primitives.child; primitive != null; primitive = primitive.next) {
                meshTriangles += trianglesOf(primitive, accessors, glbFile);
            }
            total += meshTriangles * drawsPerMesh[meshIndex];
        }
        return Math.toIntExact(total);
    }

    /**
     * Số lần GPU vẽ mỗi mesh = số node trỏ tới nó (glTF 2.0 §3.5: mesh là hình học, node là chỗ
     * đặt). Blender xuất bản sao liên kết (Alt+D) thành MỘT mesh và NHIỀU node — chỉ cộng theo
     * {@code meshes} thì đếm hụt. Mesh không node nào trỏ tới vẫn tính MỘT lần: thà đếm dư phần
     * không được vẽ còn hơn đếm hụt phần được vẽ, vì đây là cổng trần ngân sách.
     */
    private static int[] drawsPerMesh(JsonValue nodes, int meshCount, Path glbFile) {
        int[] nodeRefs = new int[meshCount];
        if (nodes != null) {
            for (JsonValue node = nodes.child; node != null; node = node.next) {
                int mesh = meshOf(node, meshCount, glbFile);
                if (mesh != NO_ACCESSOR) {
                    nodeRefs[mesh]++;
                }
            }
        }
        for (int i = 0; i < meshCount; i++) {
            nodeRefs[i] = Math.max(1, nodeRefs[i]);
        }
        return nodeRefs;
    }

    /** Mesh mà node trỏ tới, hoặc {@link #NO_ACCESSOR} nếu node không mang mesh (khớp xương, rỗng). */
    private static int meshOf(JsonValue node, int meshCount, Path glbFile) {
        int mesh = node.getInt("mesh", NO_ACCESSOR);
        if (mesh == NO_ACCESSOR) {
            return NO_ACCESSOR;
        }
        if (mesh < 0 || mesh >= meshCount) {
            throw new AssetPipelineException("File '" + glbFile + "' có node trỏ tới mesh #" + mesh + " không tồn tại");
        }
        JsonValue extensions = node.get("extensions");
        if (extensions != null && extensions.has(EXT_GPU_INSTANCING)) {
            // Số bản sao nằm trong buffer nhị phân, counter chỉ đọc chunk JSON — đếm 1 lần là đếm
            // hụt im lặng, nên từ chối thẳng.
            throw new AssetPipelineException("File '" + glbFile + "' dùng " + EXT_GPU_INSTANCING
                    + " — cổng ngân sách tam giác chưa đếm được instancing GPU");
        }
        return mesh;
    }

    private static int trianglesOf(JsonValue primitive, JsonValue accessors, Path glbFile) {
        int vertexCount = vertexCountOf(primitive, accessors, glbFile);
        return switch (primitive.getInt("mode", MODE_TRIANGLES)) {
            case MODE_TRIANGLES -> vertexCount / VERTICES_PER_TRIANGLE;
            case MODE_TRIANGLE_STRIP, MODE_TRIANGLE_FAN -> Math.max(0, vertexCount - STRIP_PRIMING_VERTICES);
            default -> 0; // POINTS/LINES/LINE_LOOP/LINE_STRIP — không có tam giác nào
        };
    }

    private static int vertexCountOf(JsonValue primitive, JsonValue accessors, Path glbFile) {
        int indicesAccessor = primitive.getInt("indices", NO_ACCESSOR);
        if (indicesAccessor != NO_ACCESSOR) {
            return accessorCount(accessors, indicesAccessor, glbFile);
        }
        // Không có indices: hình học vẽ tuần tự theo POSITION (glTF 2.0 §3.7.2.1) — POSITION là
        // attribute DUY NHẤT spec bắt buộc, thiếu nó thì file hỏng chứ không phải "0 tam giác".
        JsonValue attributes = primitive.get("attributes");
        int positionAccessor = attributes == null ? NO_ACCESSOR : attributes.getInt("POSITION", NO_ACCESSOR);
        if (positionAccessor == NO_ACCESSOR) {
            throw new AssetPipelineException(
                    "File '" + glbFile + "' có primitive không indices và cũng không có attribute POSITION");
        }
        return accessorCount(accessors, positionAccessor, glbFile);
    }

    private static int accessorCount(JsonValue accessors, int index, Path glbFile) {
        JsonValue accessor = accessors == null ? null : accessors.get(index);
        if (accessor == null) {
            throw new AssetPipelineException("File '" + glbFile + "' trỏ tới accessor #" + index + " không tồn tại");
        }
        int count = accessor.getInt("count", MISSING_COUNT);
        if (count == MISSING_COUNT) {
            throw new AssetPipelineException(
                    "File '" + glbFile + "' có accessor #" + index + " thiếu field bắt buộc 'count'");
        }
        return count;
    }
}
