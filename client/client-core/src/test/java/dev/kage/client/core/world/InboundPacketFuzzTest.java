package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import com.badlogic.gdx.Input;
import com.google.protobuf.Descriptors.Descriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;

/**
 * FUZZ đường NHẬN gói của client: mọi opcode server→client ({@link ServerOpcodes}) đi qua bộ định
 * tuyến THẬT ({@code WorldScreen#dispatch} → {@link PanelHub} → coordinator/controller), trên một
 * {@link WorldScreen} dựng thật trong {@link HeadlessGdx}.
 *
 * <p>Bất biến (vi phạm = bug, không phải nhiễu):
 *
 * <ol>
 *   <li>không exception nào lọt ra khỏi một khung — client không được chết vì một gói;
 *   <li>trạng thái hiển thị sạch sau MỌI gói — {@link DisplayInvariants} (không NaN/vô cực, máu/Khí
 *       trong {@code [0, max]}, entity ECS không rò);
 *   <li>bất biến theo gói — {@link PacketInvariants} (despawn là mất hẳn; rác không kéo theo lệnh
 *       nào; gói hợp lệ chỉ kéo theo tra cứu và tự nhắm có chủ đích);
 *   <li>repeated rất dài không làm treo/hết bộ nhớ — {@link #hugeRepeatedFieldsDoNotHang()}.
 * </ol>
 *
 * <p>Ngẫu nhiên có HẠT GIỐNG CỐ ĐỊNH ({@link #BASE_SEED} + số chuỗi): đỏ là đỏ lại được. Thông báo
 * lỗi in hạt giống + bước + opcode + payload hex để dựng lại đúng ca. Ca xác định có tên nằm ở
 * {@link InboundPacketScenarioTest}.
 */
class InboundPacketFuzzTest {

    private static final long BASE_SEED = 0x4B41_4745_5A5AL;
    private static final int SEQUENCES = 40;
    private static final int HUGE_REPEATED = 100_000;
    /** Một gói 100k phần tử phải xử lý xong dưới mức này — trên thì người chơi thấy client đơ. */
    private static final long HUGE_PACKET_BUDGET_MS = 1_500L;

    private static final long NANOS_PER_MILLI = 1_000_000L;

    /** Phím mở từng cửa sổ — mở là hỏi server danh sách, tức là tạo ra lệnh ĐANG CHỜ trả lời. */
    private static final int[] PANEL_KEYS = {
        Input.Keys.K,
        Input.Keys.H,
        Input.Keys.F,
        Input.Keys.G,
        Input.Keys.I,
        Input.Keys.M,
        Input.Keys.P,
        Input.Keys.J,
        Input.Keys.B,
        Input.Keys.N,
    };

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install();
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void everyServerToClientOpcodeHasAMessageType() {
        assertThat(ServerOpcodes.DESCRIPTORS.keySet())
                .containsExactlyInAnyOrderElementsOf(ServerOpcodes.SERVER_TO_CLIENT);
    }

    @Test
    void randomPacketsNeverBreakTheClient() {
        Map<String, String> failures = new LinkedHashMap<>();
        for (int sequence = 0; sequence < SEQUENCES; sequence++) {
            new FuzzSequence(BASE_SEED + sequence, failures).run();
        }
        assertThat(failures.values())
                .as("vi phạm bất biến nhận gói (mỗi dòng một kiểu lỗi, kèm hạt giống để dựng lại)")
                .isEmpty();
    }

    /**
     * Mỗi opcode có field repeated nhận MỘT gói mà mọi repeated tầng ngoài có 100k phần tử (vẫn dưới
     * trần khung 1MB của {@code GameConnection} khi phần tử rỗng) — trả lời đúng {@code sequenceId}
     * của lệnh đang chờ, để gói thật sự đi tới panel. Trần chấp nhận được: dựng tối đa
     * {@code ScrollingList.MAX_BUILT_ROWS} dòng, xử lý dưới {@link #HUGE_PACKET_BUDGET_MS}.
     */
    @Test
    void hugeRepeatedFieldsDoNotHang() {
        Map<String, String> slow = new LinkedHashMap<>();
        PoisonMessageFactory factory = new PoisonMessageFactory(new Random(BASE_SEED));
        FuzzRig rig = new FuzzRig();
        try {
            for (Opcode opcode : ServerOpcodes.SERVER_TO_CLIENT) {
                Descriptor descriptor = ServerOpcodes.DESCRIPTORS.get(opcode);
                if (!ServerOpcodes.hasRepeated(descriptor)) {
                    continue;
                }
                openEveryPanel(rig);
                byte[] payload =
                        factory.messageWithRepeated(descriptor, HUGE_REPEATED).toByteArray();
                long started = System.nanoTime();
                rig.deliver(opcode.getNumber(), rig.matchingSequenceId(opcode.getNumber(), 1), payload);
                assertThatCode(rig::frame).as("%s x%d", opcode, HUGE_REPEATED).doesNotThrowAnyException();
                long elapsedMs = (System.nanoTime() - started) / NANOS_PER_MILLI;
                if (elapsedMs > HUGE_PACKET_BUDGET_MS) {
                    slow.put(opcode.name(), elapsedMs + "ms");
                }
                if (rig.sessionEndedKey != null) {
                    rig.dispose();
                    rig = new FuzzRig();
                }
            }
        } finally {
            rig.dispose();
        }
        assertThat(slow)
                .as("gói repeated %d phần tử xử lý quá lâu", HUGE_REPEATED)
                .isEmpty();
    }

    /** Mở rồi đóng từng cửa sổ — mỗi lần mở là một lượt hỏi server, để gói trả lời có chỗ khớp. */
    private static void openEveryPanel(FuzzRig rig) {
        for (int key : PANEL_KEYS) {
            rig.press(key);
            rig.frame();
            rig.press(Input.Keys.ESCAPE);
            rig.frame();
        }
        rig.link.sent.clear();
    }
}
