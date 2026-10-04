package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import com.badlogic.gdx.Input;
import com.google.protobuf.Descriptors.Descriptor;

import dev.kage.protocol.Opcode;

/**
 * MỘT chuỗi fuzz có hạt giống: xen kẽ gói độc (rác hoặc đúng kiểu mang giá trị độc) với thao tác
 * người chơi ngẫu nhiên (mở cửa sổ, gõ phím, click) — thao tác tạo ra lệnh ĐANG CHỜ để gói trả lời
 * có chỗ khớp {@code sequenceId}, tức là đi được sâu vào các luồng coordinator.
 *
 * <p>Exception lọt ra khỏi một khung làm test đỏ NGAY, kèm hạt giống/bước/opcode/payload. Vi phạm
 * bất biến thì gom lại (mỗi kiểu một dòng) rồi dựng màn mới — lỗi dính lại sẽ bị báo lại ở mọi bước
 * sau và che mất lỗi KHÁC.
 */
final class FuzzSequence {

    private static final int STEPS = 250;
    private static final int UI_ONE_IN = 10;
    private static final int GARBAGE_ONE_IN = 4;
    private static final int MAX_GARBAGE_BYTES = 40;
    private static final int MAX_REPORTED = 40;
    private static final int PAYLOAD_HEX_LIMIT = 96;
    private static final int LETTERS = 26;

    /** Cửa sổ gửi lại của một cú bấm Đánh + biên, tính bằng khung 60Hz. */
    private static final int UI_SETTLE_FRAMES =
            (int) Math.ceil(CombatConstants.ATTACK_CONFIRM_WINDOW_SECONDS / FuzzRig.FIXED_DT) + 15;

    private static final int[] UI_KEYS = {
        Input.Keys.T, Input.Keys.K, Input.Keys.H, Input.Keys.F, Input.Keys.G, Input.Keys.I, Input.Keys.M,
        Input.Keys.P, Input.Keys.J, Input.Keys.B, Input.Keys.N, Input.Keys.ESCAPE, Input.Keys.ENTER, Input.Keys.A,
    };

    private final long seed;
    private final Random random;
    private final PoisonMessageFactory factory;
    private final Map<String, String> failures;
    private FuzzRig rig;

    FuzzSequence(long seed, Map<String, String> failures) {
        this.seed = seed;
        this.random = new Random(seed);
        this.factory = new PoisonMessageFactory(random);
        this.failures = failures;
    }

    void run() {
        rig = new FuzzRig();
        try {
            for (int step = 0; step < STEPS && failures.size() < MAX_REPORTED; step++) {
                boolean broken = random.nextInt(UI_ONE_IN) == 0 ? uiStep(step) : packetStep(step);
                if (broken || rig.sessionEndedKey != null) {
                    rig.dispose();
                    rig = new FuzzRig();
                }
            }
        } finally {
            rig.dispose();
        }
    }

    /** Thao tác người chơi. Lệnh nó gây ra là của NGƯỜI CHƠI — bỏ qua, không thuộc bất biến. */
    private boolean uiStep(int step) {
        uiAction();
        // Bấm nút Đánh mở một cửa sổ gửi lại (AttackInputController) — chạy hết cửa sổ đó để những
        // lần gửi lại ấy không bị đổ cho gói tới sau.
        for (int frame = 0; frame < UI_SETTLE_FRAMES && rig.sessionEndedKey == null; frame++) {
            frame(step, "UI", new byte[0]);
        }
        rig.link.sent.clear();
        return false;
    }

    private boolean packetStep(int step) {
        Opcode opcode = ServerOpcodes.SERVER_TO_CLIENT.get(random.nextInt(ServerOpcodes.SERVER_TO_CLIENT.size()));
        Descriptor descriptor = ServerOpcodes.DESCRIPTORS.get(opcode);
        byte[] payload = random.nextInt(GARBAGE_ONE_IN) == 0
                ? factory.randomBytes(random.nextInt(MAX_GARBAGE_BYTES))
                : factory.message(descriptor).toByteArray();
        int sequenceId = random.nextBoolean()
                ? rig.matchingSequenceId(opcode.getNumber(), factory.poisonInt())
                : factory.poisonInt();
        rig.deliver(opcode.getNumber(), sequenceId, payload);
        frame(step, opcode.name(), payload);
        if (rig.sessionEndedKey != null) {
            return false; // phiên đã kết thúc (bị đá/server tắt) — màn này không còn hiển thị gì.
        }
        List<String> found = PacketInvariants.check(rig, opcode, payload);
        rig.link.sent.clear();
        for (String violation : found) {
            record(step, opcode.name(), payload, violation);
        }
        return !found.isEmpty();
    }

    private void frame(int step, String what, byte[] payload) {
        assertThatCode(rig::frame)
                .as(() -> describe(step, what, payload) + " → exception lọt ra khỏi khung")
                .doesNotThrowAnyException();
    }

    private void uiAction() {
        if (random.nextBoolean()) {
            int key = UI_KEYS[random.nextInt(UI_KEYS.length)];
            rig.input.keyDown(key);
            rig.input.keyTyped((char) ('a' + random.nextInt(LETTERS)));
            rig.input.keyUp(key);
        } else {
            int x = random.nextInt(HeadlessGdx.SCREEN_WIDTH);
            int y = random.nextInt(HeadlessGdx.SCREEN_HEIGHT);
            rig.input.touchDown(x, y, 0, Input.Buttons.LEFT);
            rig.input.touchUp(x, y, 0, Input.Buttons.LEFT);
        }
    }

    /** Mỗi KIỂU vi phạm (opcode + mô tả, bỏ số) chỉ báo một lần — đủ để dựng lại, không ngập. */
    private void record(int step, String opcode, byte[] payload, String violation) {
        String key = (opcode + violation).replaceAll("\\d+", "#");
        failures.computeIfAbsent(key, ignored -> describe(step, opcode, payload) + " → " + violation);
    }

    private String describe(int step, String opcode, byte[] payload) {
        String hex = HexFormat.of().formatHex(payload, 0, Math.min(payload.length, PAYLOAD_HEX_LIMIT));
        return String.format(
                Locale.ROOT,
                "seed=0x%X step=%d opcode=%s payload=%s%s",
                seed,
                step,
                opcode,
                hex,
                payload.length > PAYLOAD_HEX_LIMIT ? "…" : "");
    }
}
