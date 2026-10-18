package dev.kage.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

/**
 * Cổng build cho bảng opcode ({@code opcode_registry.proto}) — các bất biến mà protoc KHÔNG kiểm.
 *
 * <p>protoc chỉ chặn trùng SỐ trong enum. Còn lại đều là quy ước sống trong đầu người viết: opcode
 * {@code OPCODE_FOO_BAR} mang payload là message {@code FooBar}; một {@code *_COMMAND}/{@code
 * *_REQUEST} có {@code *_RESPONSE} cùng gốc tên; message nào kết bằng Command/Request/Response/Event
 * thì phải có opcode để đi trên dây. Lệch một trong ba thứ đó thì cả hai phía vẫn biên dịch được,
 * chỉ là gói không bao giờ được hiểu đúng.
 *
 * <p>Những lệnh cố ý KHÔNG theo khuôn tên (đánh thường, nhảy, hội thoại...) được khai báo TƯỜNG MINH
 * ở {@link #REPLIES_OUTSIDE_NAMING} — thêm một opcode lệch khuôn mà không ghi vào đây là đỏ build.
 */
class OpcodeRegistryTest {

    private static final Path PROTO_DIR = Path.of("src", "main", "proto");

    /** Dải 1800+ là "dự trữ" (NETWORK.md §4) — dùng khi chưa cập nhật bảng dải là sai luật. */
    private static final int FIRST_RESERVED_OPCODE = 1800;

    /** Giữ chỗ cho {@code ITEM_REMOVED_EVENT} (ADR-035 Revisit Trigger #3) — opcode_registry.proto. */
    private static final int HELD_FOR_ITEM_REMOVED_EVENT = 511;

    /** Ba message hệ thống không theo khuôn tên opcode (NETWORK.md §2, system.proto). */
    private static final Map<Opcode, String> MESSAGE_NAME_EXCEPTIONS = Map.of(
            Opcode.OPCODE_PING, "PingCommand",
            Opcode.OPCODE_PONG, "PongResponse",
            Opcode.OPCODE_ERROR, "ErrorResponse");

    /**
     * Lệnh client gửi mà KHÔNG có {@code *_RESPONSE} cùng gốc tên, kèm các opcode server dùng để trả
     * lời nó. Tập rỗng = lệnh im lặng, kết quả về qua event (combat.proto: "sai điều kiện thì bỏ qua
     * ÂM THẦM").
     */
    private static final Map<Opcode, Set<Opcode>> REPLIES_OUTSIDE_NAMING = Map.of(
            Opcode.OPCODE_PING, EnumSet.of(Opcode.OPCODE_PONG),
            Opcode.OPCODE_SET_TARGET_COMMAND, EnumSet.noneOf(Opcode.class),
            Opcode.OPCODE_ATTACK_COMMAND, EnumSet.noneOf(Opcode.class),
            Opcode.OPCODE_USE_SKILL_COMMAND, EnumSet.noneOf(Opcode.class),
            // ADR-033: JumpCommand được ack bằng chính MoveResponse (movement.proto).
            Opcode.OPCODE_JUMP_COMMAND, EnumSet.of(Opcode.OPCODE_MOVE_RESPONSE),
            Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST, EnumSet.of(Opcode.OPCODE_ITEM_CATALOG_RESPONSE),
            Opcode.OPCODE_TALK_TO_NPC_COMMAND, EnumSet.of(Opcode.OPCODE_DIALOGUE_NODE_RESPONSE),
            // dialogue.proto + quest.proto: chọn một lựa chọn có thể sang node kế, kết thúc, nhận
            // quest ("accept_quest:<code>") hoặc mở cửa hàng ("open_shop").
            Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND,
                    EnumSet.of(
                            Opcode.OPCODE_DIALOGUE_NODE_RESPONSE,
                            Opcode.OPCODE_DIALOGUE_ENDED_RESPONSE,
                            Opcode.OPCODE_ACCEPT_QUEST_RESPONSE,
                            Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE));

    private static final Pattern MESSAGE_DECLARATION = Pattern.compile("(?m)^message\\s+(\\w+)\\s*\\{");

    @Test
    void everyOpcodeShouldNameAGeneratedProtobufMessage() {
        List<String> missing = new ArrayList<>();
        for (Opcode opcode : wireOpcodes()) {
            String messageName = messageNameOf(opcode);
            try {
                Class<?> type = Class.forName("dev.kage.protocol." + messageName);
                if (!Message.class.isAssignableFrom(type)) {
                    missing.add(opcode + " -> " + messageName + " (không phải message protobuf)");
                }
            } catch (ClassNotFoundException e) {
                missing.add(opcode + " -> " + messageName);
            }
        }
        assertThat(missing).as("opcode không có message payload cùng tên").isEmpty();
    }

    @Test
    void everyWireMessageShouldHaveAnOpcode() throws IOException {
        Set<String> carried = new TreeSet<>();
        for (Opcode opcode : wireOpcodes()) {
            carried.add(messageNameOf(opcode));
        }
        Set<String> orphans = new TreeSet<>();
        for (String message : declaredMessages()) {
            if (message.matches(".*(Command|Request|Response|Event)") && !carried.contains(message)) {
                orphans.add(message);
            }
        }
        assertThat(orphans)
                .as("message mang hậu tố gói tin mà không opcode nào chở")
                .isEmpty();
    }

    @Test
    void everyOpcodeShouldHaveAKnownDirection() {
        List<String> unknown = new ArrayList<>();
        for (Opcode opcode : wireOpcodes()) {
            if (!isClientToServer(opcode) && !isServerToClient(opcode)) {
                unknown.add(opcode.name());
            }
        }
        assertThat(unknown)
                .as("opcode không kết bằng _COMMAND/_REQUEST/_RESPONSE/_EVENT")
                .isEmpty();
    }

    @Test
    void everyClientCommandShouldHaveAKnownReply() {
        List<String> unpaired = new ArrayList<>();
        List<String> staleExceptions = new ArrayList<>();
        for (Opcode opcode : wireOpcodes()) {
            if (!isClientToServer(opcode)) {
                continue;
            }
            boolean pairedByName = namePairedResponse(opcode) != null;
            boolean declared = REPLIES_OUTSIDE_NAMING.containsKey(opcode);
            if (!pairedByName && !declared) {
                unpaired.add(opcode.name());
            }
            if (pairedByName && declared) {
                staleExceptions.add(opcode.name());
            }
        }
        assertThat(unpaired)
                .as("lệnh không có *_RESPONSE cùng gốc tên và cũng không khai trong REPLIES_OUTSIDE_NAMING")
                .isEmpty();
        assertThat(staleExceptions)
                .as("ngoại lệ thừa: lệnh đã có response cùng tên")
                .isEmpty();
    }

    @Test
    void everyResponseShouldAnswerSomeClientCommand() {
        Set<Opcode> answered = EnumSet.noneOf(Opcode.class);
        for (Opcode opcode : wireOpcodes()) {
            Opcode paired = namePairedResponse(opcode);
            if (isClientToServer(opcode) && paired != null) {
                answered.add(paired);
            }
        }
        REPLIES_OUTSIDE_NAMING.values().forEach(answered::addAll);

        List<String> orphanResponses = new ArrayList<>();
        for (Opcode opcode : wireOpcodes()) {
            if (isResponse(opcode) && !answered.contains(opcode)) {
                orphanResponses.add(opcode.name());
            }
        }
        assertThat(orphanResponses).as("response không trả lời lệnh nào").isEmpty();
    }

    @Test
    void declaredRepliesShouldAllFlowFromServerToClient() {
        for (Map.Entry<Opcode, Set<Opcode>> entry : REPLIES_OUTSIDE_NAMING.entrySet()) {
            assertThat(isClientToServer(entry.getKey()))
                    .as(entry.getKey().name())
                    .isTrue();
            assertThat(entry.getValue()).allMatch(OpcodeRegistryTest::isServerToClient);
        }
    }

    @Test
    void opcodesShouldStayOutOfReservedNumbers() {
        for (Opcode opcode : wireOpcodes()) {
            assertThat(opcode.getNumber()).as(opcode.name()).isLessThan(FIRST_RESERVED_OPCODE);
            assertThat(opcode.getNumber()).as(opcode.name()).isNotEqualTo(HELD_FOR_ITEM_REMOVED_EVENT);
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Mọi opcode thật — bỏ {@code UNSPECIFIED} (= 0) và {@code UNRECOGNIZED} (protobuf tự thêm). */
    private static List<Opcode> wireOpcodes() {
        List<Opcode> opcodes = new ArrayList<>();
        for (Opcode opcode : Opcode.values()) {
            if (opcode != Opcode.UNRECOGNIZED && opcode != Opcode.OPCODE_UNSPECIFIED) {
                opcodes.add(opcode);
            }
        }
        return opcodes;
    }

    private static String baseName(Opcode opcode) {
        return opcode.name().substring("OPCODE_".length());
    }

    private static String messageNameOf(Opcode opcode) {
        String exception = MESSAGE_NAME_EXCEPTIONS.get(opcode);
        if (exception != null) {
            return exception;
        }
        // OPCODE_FOO_BAR -> FooBar: chữ đầu mỗi từ giữ hoa, phần còn lại hạ thường, bỏ dấu gạch.
        String upper = baseName(opcode);
        StringBuilder name = new StringBuilder(upper.length());
        boolean wordStart = true;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '_') {
                wordStart = true;
            } else {
                // Ghi đủ java.lang: lớp sinh từ character.proto cùng package che mất tên Character.
                name.append(wordStart ? c : java.lang.Character.toLowerCase(c));
                wordStart = false;
            }
        }
        return name.toString();
    }

    private static boolean isClientToServer(Opcode opcode) {
        String name = baseName(opcode);
        return opcode == Opcode.OPCODE_PING || name.endsWith("_COMMAND") || name.endsWith("_REQUEST");
    }

    private static boolean isServerToClient(Opcode opcode) {
        return opcode == Opcode.OPCODE_PONG
                || opcode == Opcode.OPCODE_ERROR
                || isResponse(opcode)
                || baseName(opcode).endsWith("_EVENT");
    }

    private static boolean isResponse(Opcode opcode) {
        return opcode == Opcode.OPCODE_PONG || baseName(opcode).endsWith("_RESPONSE");
    }

    /** {@code OPCODE_X_COMMAND}/{@code OPCODE_X_REQUEST} → {@code OPCODE_X_RESPONSE} nếu có; ngược lại {@code null}. */
    private static Opcode namePairedResponse(Opcode opcode) {
        String name = opcode.name();
        String stem;
        if (name.endsWith("_COMMAND")) {
            stem = name.substring(0, name.length() - "_COMMAND".length());
        } else if (name.endsWith("_REQUEST")) {
            stem = name.substring(0, name.length() - "_REQUEST".length());
        } else {
            return null;
        }
        for (Opcode candidate : wireOpcodes()) {
            if (candidate.name().equals(stem + "_RESPONSE")) {
                return candidate;
            }
        }
        return null;
    }

    private static Set<String> declaredMessages() throws IOException {
        Set<String> messages = new TreeSet<>();
        try (Stream<Path> files = Files.list(PROTO_DIR)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".proto")).toList()) {
                Matcher matcher = MESSAGE_DECLARATION.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    messages.add(matcher.group(1));
                }
            }
        }
        assertThat(messages)
                .as("không đọc được message nào từ " + PROTO_DIR.toAbsolutePath())
                .isNotEmpty();
        return messages;
    }
}
