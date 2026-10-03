package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dựng file {@code .glb} tối thiểu ngay trong test (header + chunk JSON thật, đúng little-endian)
 * thay vì commit model mẫu: model thật nhẹ nhất cũng 0,6 MB, mà thứ cần kiểm là <b>công thức đếm
 * theo mode</b> chứ không phải hình học. Chunk BIN cố tình bỏ trống — counter chỉ đọc metadata
 * accessor, không giải mã buffer, nên test này cũng chốt luôn cam kết đó.
 */
final class GlbTriangleCounterTest {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_TYPE_JSON = 0x4E4F534A;
    private static final int GLB_VERSION = 2;
    private static final int HEADER_BYTES = 12;
    private static final int CHUNK_HEADER_BYTES = 8;

    @Test
    void shouldCountIndexedTriangles_whenModeIsTriangles(@TempDir Path folder) throws IOException {
        // mode bỏ trống = TRIANGLES (mặc định spec), 36 index -> 12 tam giác (một khối hộp).
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 36 }, { "count": 24 } ],
                  "meshes": [ { "primitives": [ { "attributes": { "POSITION": 1 }, "indices": 0 } ] } ] }
                """);

        assertThat(GlbTriangleCounter.countTriangles(glb)).isEqualTo(12);
    }

    @Test
    void shouldCountStripAsVertexCountMinusTwo_whenModeIsTriangleStrip(@TempDir Path folder) throws IOException {
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 10 } ],
                  "meshes": [ { "primitives": [
                      { "attributes": { "POSITION": 0 }, "indices": 0, "mode": 5 } ] } ] }
                """);

        assertThat(GlbTriangleCounter.countTriangles(glb)).isEqualTo(8);
    }

    @Test
    void shouldCountFanAsVertexCountMinusTwo_whenModeIsTriangleFan(@TempDir Path folder) throws IOException {
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 7 } ],
                  "meshes": [ { "primitives": [
                      { "attributes": { "POSITION": 0 }, "indices": 0, "mode": 6 } ] } ] }
                """);

        assertThat(GlbTriangleCounter.countTriangles(glb)).isEqualTo(5);
    }

    @Test
    void shouldFallBackToPositionAccessor_whenPrimitiveHasNoIndices(@TempDir Path folder) throws IOException {
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 9 } ],
                  "meshes": [ { "primitives": [ { "attributes": { "POSITION": 0 } } ] } ] }
                """);

        assertThat(GlbTriangleCounter.countTriangles(glb)).isEqualTo(3);
    }

    @Test
    void shouldIgnoreLineAndPointPrimitives_whenModeDrawsNoTriangles(@TempDir Path folder) throws IOException {
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 300 }, { "count": 30 } ],
                  "meshes": [ { "primitives": [
                      { "attributes": { "POSITION": 0 }, "indices": 0, "mode": 1 },
                      { "attributes": { "POSITION": 1 }, "indices": 1, "mode": 4 } ] } ] }
                """);

        assertThat(GlbTriangleCounter.countTriangles(glb)).isEqualTo(10);
    }

    @Test
    void shouldSumEveryMeshAndPrimitive_whenModelHasSeveralParts(@TempDir Path folder) throws IOException {
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 30 }, { "count": 12 }, { "count": 6 } ],
                  "meshes": [
                      { "primitives": [ { "attributes": { "POSITION": 0 }, "indices": 0 },
                                        { "attributes": { "POSITION": 1 }, "indices": 1, "mode": 5 } ] },
                      { "primitives": [ { "attributes": { "POSITION": 2 }, "indices": 2 } ] } ] }
                """);

        assertThat(GlbTriangleCounter.countTriangles(glb)).isEqualTo(10 + 10 + 2);
    }

    /**
     * glTF tách "hình học" ({@code meshes}) khỏi "chỗ đặt" ({@code nodes}): Blender xuất bản sao
     * liên kết (Alt+D — VD hai chiếc giày, hai tay của mesh kit) thành MỘT mesh và NHIỀU node trỏ vào
     * nó, và GPU vẽ mesh đó một lần cho mỗi node. Bản cũ chỉ cộng theo {@code meshes} nên đếm hụt —
     * model vượt trần vẫn qua cổng §4.4.
     */
    @Test
    void shouldCountMeshOncePerNode_whenSeveralNodesInstanceSameMesh(@TempDir Path folder) throws IOException {
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 36 }, { "count": 30 } ],
                  "meshes": [ { "primitives": [ { "attributes": { "POSITION": 0 }, "indices": 0 } ] },
                              { "primitives": [ { "attributes": { "POSITION": 1 }, "indices": 1 } ] } ],
                  "nodes": [ { "mesh": 0 }, { "mesh": 0 }, { "mesh": 0 }, { "mesh": 1 }, { "name": "rig" } ] }
                """);

        assertThat(GlbTriangleCounter.countTriangles(glb)).isEqualTo(3 * 12 + 10);
    }

    /**
     * {@code EXT_mesh_gpu_instancing} nhân bản một node theo số dòng trong buffer nhị phân — counter
     * chỉ đọc chunk JSON nên không đếm được. Im lặng đếm 1 lần là đếm hụt, nên phải từ chối rõ ràng.
     */
    @Test
    void shouldReject_whenModelUsesGpuInstancingExtension(@TempDir Path folder) throws IOException {
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 36 }, { "count": 100 } ],
                  "meshes": [ { "primitives": [ { "attributes": { "POSITION": 0 }, "indices": 0 } ] } ],
                  "nodes": [ { "mesh": 0, "extensions": { "EXT_mesh_gpu_instancing":
                      { "attributes": { "TRANSLATION": 1 } } } } ] }
                """);

        assertThatThrownBy(() -> GlbTriangleCounter.countTriangles(glb))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("EXT_mesh_gpu_instancing");
    }

    @Test
    void shouldReject_whenFileIsNotBinaryGltf(@TempDir Path folder) throws IOException {
        Path notGlb = folder.resolve("broken.glb");
        Files.write(notGlb, new byte[HEADER_BYTES + CHUNK_HEADER_BYTES]);

        assertThatThrownBy(() -> GlbTriangleCounter.countTriangles(notGlb))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("không phải glTF nhị phân");
    }

    @Test
    void shouldReject_whenPrimitiveReferencesMissingAccessor(@TempDir Path folder) throws IOException {
        Path glb = writeGlb(folder, """
                { "accessors": [ { "count": 3 } ],
                  "meshes": [ { "primitives": [ { "attributes": { "POSITION": 0 }, "indices": 7 } ] } ] }
                """);

        assertThatThrownBy(() -> GlbTriangleCounter.countTriangles(glb))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("accessor #7");
    }

    private static Path writeGlb(Path folder, String gltfJson) throws IOException {
        byte[] json = gltfJson.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + CHUNK_HEADER_BYTES + json.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(GLB_MAGIC);
        buffer.putInt(GLB_VERSION);
        buffer.putInt(buffer.capacity());
        buffer.putInt(json.length);
        buffer.putInt(CHUNK_TYPE_JSON);
        buffer.put(json);

        Path glb = folder.resolve("model.glb");
        Files.write(glb, buffer.array());
        return glb;
    }
}
