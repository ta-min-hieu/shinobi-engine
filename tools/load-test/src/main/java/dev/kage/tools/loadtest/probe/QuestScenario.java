package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.kage.protocol.AcceptQuestResponse;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.DialogueNodeResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.ListQuestsRequest;
import dev.kage.protocol.ListQuestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectDialogueOptionCommand;
import dev.kage.protocol.TalkToNpcCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * VÒNG ĐỜI NHIỆM VỤ đọc thành số, không cần giao diện: chụp nhật ký + túi đồ → nói chuyện NPC → đi
 * theo cây hội thoại → nhận nhiệm vụ → (tuỳ chọn) lĩnh thưởng → chụp lại → in CHÊNH LỆCH.
 *
 * <p><b>Vì sao tồn tại</b> (ROADMAP mục 55/57/58 + 61): tới hôm nay vòng đời quest chỉ kiểm được
 * bằng tay qua giao diện, và chính đường quan sát đó đã ba lần cho ra kết luận sai trong một ngày.
 * Probe nói ĐÚNG giao thức dây thật ({@link GameConnection}) nên mỗi bước đều để lại một con số tái
 * lập được.
 *
 * <p><b>Chỉ dùng opcode ĐÃ CÓ</b> — 602/603/604/605/606 (hội thoại + nhận quest), 610/611 (nhật ký),
 * 608/609 (lĩnh thưởng), 507/508 (túi đồ). Không thêm opcode, không đổi message, không đụng DB.
 *
 * <p><b>Không kiểm tra khoảng cách tới NPC</b>: server ở lát cắt này cũng không kiểm
 * ({@code dialogue.proto} ghi rõ lý do), nên probe không giả vờ có ràng buộc mà server không có —
 * muốn đứng đúng chỗ thì dùng {@code --moveToX}.
 */
final class QuestScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code GameProbe}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    /** Ba kết cục có thể của một lần chọn lựa chọn hội thoại (xem {@code GatewayChannelHandler}). */
    private static final Set<Integer> DIALOGUE_OUTCOME_OPCODES = Set.of(
            Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE,
            Opcode.OPCODE_DIALOGUE_ENDED_RESPONSE_VALUE,
            Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE,
            // action="open_shop" tái dùng thẳng gói cửa hàng, không có response riêng (dialogue.proto).
            Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE,
            Opcode.OPCODE_ERROR_VALUE);

    /** Ảnh chụp trạng thái nhân vật ở MỘT thời điểm — hai cái tạo thành một phép đo. */
    private record Snapshot(
            List<dev.kage.protocol.QuestLogEntry> quests,
            List<dev.kage.protocol.InventoryItemView> items,
            Optional<WorldObservation.ExperienceSnapshot> experience) {}

    private final GameConnection connection;
    private final ProbeSession session;
    private final WorldObservation observation;

    QuestScenario(GameConnection connection, ProbeSession session, WorldObservation observation) {
        this.connection = connection;
        this.session = session;
        this.observation = observation;
    }

    /**
     * Chạy trọn kịch bản và in báo cáo chênh lệch.
     *
     * @throws IOException nếu server từ chối hoặc không trả lời — probe không đoán, nó dừng
     */
    void run(ProbeConfig.QuestPlan plan) throws IOException {
        Snapshot before = snapshot();
        session.print(renderSnapshot("TRƯỚC", before));

        if (!plan.npcCode().isEmpty()) {
            talk(plan.npcCode(), plan.dialogueChoices());
        }
        if (!plan.claimQuestCode().isEmpty()) {
            claim(plan.claimQuestCode());
        }

        Snapshot after = snapshot();
        session.print(renderSnapshot("SAU", after));
        session.print(renderDifference(before, after));
    }

    // --- hội thoại -------------------------------------------------------------------------------

    private void talk(String npcCode, List<String> choices) throws IOException {
        connection.send(
                Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE,
                TalkToNpcCommand.newBuilder().setNpcCode(npcCode).build().toByteArray());
        DialogueNodeResponse node = DialogueNodeResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("hội thoại mở: npc=" + npcCode + " node=" + node.getNodeId() + " text=" + node.getTextKey()
                + " lựa chọn: " + DialogueChoice.describe(node.getOptionsList()));

        DialogueNodeResponse current = node;
        for (String choice : choices) {
            int optionIndex = DialogueChoice.resolve(choice, current.getOptionsList());
            session.note("chọn [" + optionIndex + "] '" + choice + "' tại node " + current.getNodeId());
            connection.send(
                    Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND_VALUE,
                    SelectDialogueOptionCommand.newBuilder()
                            .setNpcCode(npcCode)
                            .setCurrentNodeId(current.getNodeId())
                            .setOptionIndex(optionIndex)
                            .build()
                            .toByteArray());
            Optional<DialogueNodeResponse> next = readDialogueOutcome(npcCode);
            if (next.isEmpty()) {
                return; // hội thoại đã kết thúc / đã nhận quest / đã mở cửa hàng — không còn node nào.
            }
            current = next.get();
        }
    }

    /**
     * @return node kế tiếp, rỗng nếu hội thoại đã đóng ở bước này (kết thúc, nhận quest, mở shop)
     */
    private Optional<DialogueNodeResponse> readDialogueOutcome(String npcCode) throws IOException {
        PacketCodec.Frame frame = readUntilAnyOf(DIALOGUE_OUTCOME_OPCODES);
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE) {
            DialogueNodeResponse node = DialogueNodeResponse.parseFrom(frame.payload());
            session.note("node kế: " + node.getNodeId() + " text=" + node.getTextKey() + " lựa chọn: "
                    + DialogueChoice.describe(node.getOptionsList()));
            return Optional.of(node);
        }
        if (opcode == Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE) {
            AcceptQuestResponse response = AcceptQuestResponse.parseFrom(frame.payload());
            session.note("nhận nhiệm vụ: outcome=" + response.getOutcome().name());
            return Optional.empty();
        }
        if (opcode == Opcode.OPCODE_ERROR_VALUE) {
            throw new IOException("Server từ chối lựa chọn hội thoại với npc '" + npcCode + "': "
                    + ProbeEventLine.name(opcode) + " — xem dòng TSV ngay trên để biết mã lỗi");
        }
        session.note("hội thoại đóng: " + ProbeEventLine.name(opcode));
        return Optional.empty();
    }

    // --- lĩnh thưởng -----------------------------------------------------------------------------

    private void claim(String questCode) throws IOException {
        // idempotency_key sinh mới mỗi lần chạy (ADR-025): đây là MỘT ý định lĩnh thưởng mới, không
        // phải retry của lần trước — dùng lại khoá cũ sẽ nhận DUPLICATE_REQUEST và che mất kết quả thật.
        connection.send(
                Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE,
                ClaimQuestRewardCommand.newBuilder()
                        .setQuestCode(questCode)
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build()
                        .toByteArray());
        ClaimQuestRewardResponse response = ClaimQuestRewardResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        StringBuilder items = new StringBuilder();
        for (ItemRewardView reward : response.getItemsGrantedList()) {
            items.append(' ').append(reward.getItemTemplateCode()).append('x').append(reward.getQuantity());
        }
        session.note("lĩnh thưởng " + questCode + ": outcome="
                + response.getOutcome().name() + " expGranted=" + response.getExpGranted() + " vậtPhẩm="
                + response.getItemsGrantedCount() + items);
    }

    // --- ảnh chụp + báo cáo ----------------------------------------------------------------------

    private Snapshot snapshot() throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_QUESTS_REQUEST_VALUE,
                ListQuestsRequest.getDefaultInstance().toByteArray());
        ListQuestsResponse quests = ListQuestsResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_QUESTS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        connection.send(
                Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE,
                ListInventoryRequest.getDefaultInstance().toByteArray());
        ListInventoryResponse inventory = ListInventoryResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        return new Snapshot(quests.getQuestsList(), inventory.getItemsList(), observation.latestExperience());
    }

    private static String renderSnapshot(String label, Snapshot snapshot) {
        return "=== NHẬT KÝ NHIỆM VỤ " + label + " ===" + System.lineSeparator()
                + QuestJournal.renderTable(snapshot.quests()) + System.lineSeparator()
                + "Túi đồ " + label + ": " + snapshot.items().size() + " ô có vật phẩm";
    }

    private static String renderDifference(Snapshot before, Snapshot after) {
        return "=== CHÊNH LỆCH ===" + System.lineSeparator()
                + "Nhiệm vụ đổi trạng thái:" + System.lineSeparator()
                + QuestJournal.renderChanges(QuestJournal.between(before.quests(), after.quests()))
                + System.lineSeparator()
                + "Túi đồ:" + System.lineSeparator()
                + InventoryDelta.renderTable(InventoryDelta.between(before.items(), after.items()))
                + System.lineSeparator()
                + "EXP: " + renderExperienceDifference(before.experience(), after.experience());
    }

    private static String renderExperienceDifference(
            Optional<WorldObservation.ExperienceSnapshot> before, Optional<WorldObservation.ExperienceSnapshot> after) {
        if (after.isEmpty()) {
            // Server chỉ đẩy EXPERIENCE_CHANGED khi EXP ĐỔI — im lặng nghĩa là "không có gì cộng
            // vào trong lượt này", nói thẳng thế chứ không in "+0" như thể đã đo được.
            return "không có EXPERIENCE_CHANGED nào — EXP không đổi trong lượt này";
        }
        long totalAfter = after.get().totalExp();
        long totalBefore =
                before.map(WorldObservation.ExperienceSnapshot::totalExp).orElse(0L);
        String baseline = before.isPresent() ? "" : " (mốc trước chưa đo được, coi như 0)";
        return String.format(
                Locale.ROOT,
                "tổng %d -> %d (%+d), cấp %d -> %d%s",
                totalBefore,
                totalAfter,
                totalAfter - totalBefore,
                before.map(WorldObservation.ExperienceSnapshot::level).orElse(0),
                after.get().level(),
                baseline);
    }

    /**
     * Đọc tới khi gặp MỘT TRONG các opcode mong đợi — bản nhiều-đích của
     * {@code GameConnection#readUntilOpcode}, cần vì một lựa chọn hội thoại có tới bốn kết cục hợp lệ.
     */
    private PacketCodec.Frame readUntilAnyOf(Set<Integer> expectedOpcodes) throws IOException {
        for (int attempt = 0; attempt < MAX_FRAMES_TO_SKIP; attempt++) {
            PacketCodec.Frame frame = connection.readFrame();
            if (expectedOpcodes.contains(frame.header().opcode())) {
                return frame;
            }
        }
        throw new IOException(
                "Không thấy phản hồi hội thoại nào trong " + MAX_FRAMES_TO_SKIP + " frame — server có quá tải không?");
    }
}
