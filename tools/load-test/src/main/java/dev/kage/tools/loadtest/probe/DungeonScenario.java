package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.CreatePartyOutcome;
import dev.kage.protocol.CreatePartyResponse;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.Opcode;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Lập nhóm MỘT người rồi vào hầm ngục — đường headless để kiểm ADR-051 (gục trong hầm ngục ⇒ về
 * world chính) mà không cần mở client đồ hoạ.
 *
 * <p>Hầm ngục đòi đang ở trong nhóm ({@code ENTER_DUNGEON_OUTCOME_NOT_IN_PARTY}), nên probe tự tạo
 * nhóm trước. {@code ALREADY_IN_PARTY} KHÔNG phải lỗi: nhân vật vẫn đang ở nhóm của lượt chạy trước
 * (nhóm sống theo DB, không theo kết nối) — đi tiếp vào hầm ngục là đúng.
 *
 * <p>Vị trí + máu ở World mới tới bằng {@code WorldChangedEvent} (308), thứ {@link WorldObservation}
 * đã đọc qua {@link ProbeSession} — kịch bản này chỉ ghi outcome.
 */
final class DungeonScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;

    DungeonScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    /** @param dungeonCode mã hầm ngục, ví dụ {@code sample_dungeon} */
    void run(String dungeonCode) throws IOException {
        connection.send(
                Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE,
                CreatePartyCommand.getDefaultInstance().toByteArray());
        CreatePartyResponse party = CreatePartyResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_CREATE_PARTY_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        if (party.getOutcome() != CreatePartyOutcome.CREATE_PARTY_OUTCOME_OK
                && party.getOutcome() != CreatePartyOutcome.CREATE_PARTY_OUTCOME_ALREADY_IN_PARTY) {
            session.note("KHÔNG lập được nhóm: " + party.getOutcome().name() + " — bỏ qua bước vào hầm ngục");
            return;
        }

        connection.send(
                Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE,
                EnterDungeonCommand.newBuilder()
                        .setDungeonCode(dungeonCode)
                        .build()
                        .toByteArray());
        EnterDungeonResponse response = EnterDungeonResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("vào hầm ngục " + dungeonCode + ": nhóm="
                + party.getOutcome().name() + " kếtQuả=" + response.getOutcome().name());
    }
}
