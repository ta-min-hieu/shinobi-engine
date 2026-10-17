package dev.kage.client.core.world;

import java.util.BitSet;
import java.util.Set;

import com.badlogic.gdx.utils.IntIntMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.Opcode;

/**
 * Sổ các lệnh ĐANG CHỜ trả lời — một gói trả lời ({@code *_RESPONSE}) chỉ được đi tiếp vào màn chơi
 * nếu nó khớp {@code sequenceId} của một lệnh client thật sự đã gửi và đang chờ (dòng 67(a), chủ dự án
 * duyệt 2026-10-17).
 *
 * <p><b>Vì sao.</b> Fuzz client (2026-10-04) thấy khoảng mười coordinator nhận MỌI gói trả lời đúng
 * opcode, không kể {@code sequenceId}: một gói không ai hỏi (server lỗi, gói lạc từ phiên trước) vẫn
 * hiện câu báo và kéo theo tới ba lệnh tra cứu "hỏi lại danh sách". Sửa ở từng coordinator là ~65 chỗ
 * nhận gói; sổ này làm đúng một việc ở đúng một chỗ — {@link WorldCommandSender} ghi lúc gửi, và lọc
 * lúc đọc gói ra ({@link WorldCommandSender#pollInbound()}).
 *
 * <p><b>Gói nào bị lọc.</b> Chỉ những opcode trong {@link #REPLIES_BY_COMMAND} — trả lời của lệnh mà
 * client gửi qua {@link WorldCommandSender}. Sự kiện ({@code *_EVENT}), lỗi chung ({@code ErrorResponse})
 * và Pong không bao giờ bị lọc. {@code MoveResponse} cũng không: nó là luồng hoà giải dự đoán, gửi
 * nhiều lần mỗi giây, và chính nó đã mang {@code sequenceId} để client tự đối chiếu. Ba gói của luồng
 * đăng nhập/chọn nhân vật cũng không — màn khác gửi chúng bằng đường riêng.
 *
 * <p><b>Bộ nhớ.</b> Lệnh bị từ chối bằng {@code ErrorResponse} không bao giờ có trả lời — {@link #forget}
 * gỡ nó khi lỗi tới; sổ cũng có trần {@link #MAX_PENDING} (bỏ lệnh cũ nhất) để không phình nếu một lỗi
 * lạc mất. Chạy trên đúng một luồng (luồng render). Đường đọc gói ({@link #admit}) chạy cho MỌI gói
 * tới nên không boxing, không cấp phát — bảng tra là mảng/{@link BitSet}/{@link IntIntMap}.
 */
final class ReplyLedger {

    private static final Logger LOG = LoggerFactory.getLogger(ReplyLedger.class);

    /** Trần số lệnh đang chờ — người chơi không thể có nhiều lệnh treo cùng lúc tới thế. */
    static final int MAX_PENDING = 64;

    private static final int NOT_PENDING = -1;

    /** Trả lời của luồng thoại: một lựa chọn có thể mở nút thoại mới, kết thúc, nhận quest hay mở shop. */
    private static final int[] DIALOGUE_REPLIES = {
        Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE,
        Opcode.OPCODE_DIALOGUE_ENDED_RESPONSE_VALUE,
        Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE,
        Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE
    };

    /** Trả lời không lọc — xem javadoc lớp. */
    private static final Set<Integer> UNFILTERED_REPLIES = Set.of(
            Opcode.OPCODE_MOVE_RESPONSE_VALUE,
            Opcode.OPCODE_CONNECT_RESPONSE_VALUE,
            Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE,
            Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE,
            Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE);

    /** Opcode lệnh ⇒ những opcode trả lời được cho nó ({@code null} = lệnh không có trả lời bị lọc). */
    private static final int[][] REPLIES_BY_COMMAND = buildReplies();

    /** Mọi opcode trả lời bị lọc. */
    private static final BitSet FILTERED_REPLIES = filteredReplies();

    /** {@code sequenceId} ⇒ opcode lệnh đang chờ. */
    private final IntIntMap pending = new IntIntMap(MAX_PENDING);

    /** {@code sequenceId} theo thứ tự gửi, vòng tròn — để bỏ cái cũ nhất khi chạm trần. */
    private final int[] sentOrder = new int[MAX_PENDING];

    private int nextSlot;

    private int recorded;

    /** Lệnh vừa gửi — ghi lại nếu nó có trả lời bị lọc. */
    void expect(int commandOpcode, int sequenceId) {
        if (repliesOf(commandOpcode) == null) {
            return;
        }
        if (recorded == MAX_PENDING) {
            pending.remove(sentOrder[nextSlot], NOT_PENDING);
        } else {
            recorded++;
        }
        sentOrder[nextSlot] = sequenceId;
        nextSlot = (nextSlot + 1) % MAX_PENDING;
        pending.put(sequenceId, commandOpcode);
    }

    /**
     * Gói tới có được đi tiếp không. Trả lời khớp một lệnh đang chờ thì gạch lệnh đó khỏi sổ — mỗi lệnh
     * một trả lời.
     */
    boolean admit(int opcode, int sequenceId) {
        if (opcode < 0 || !FILTERED_REPLIES.get(opcode)) {
            return true;
        }
        int command = pending.get(sequenceId, NOT_PENDING);
        if (command != NOT_PENDING && contains(repliesOf(command), opcode)) {
            pending.remove(sequenceId, NOT_PENDING);
            return true;
        }
        LOG.debug("Bỏ gói trả lời không khớp lệnh nào đang chờ: opcode={}, sequenceId={}", opcode, sequenceId);
        return false;
    }

    /** Lệnh {@code sequenceId} đã bị từ chối bằng {@code ErrorResponse} — sẽ không có trả lời nữa. */
    void forget(int sequenceId) {
        pending.remove(sequenceId, NOT_PENDING);
    }

    /** Số lệnh đang chờ — cho test. */
    int pendingCount() {
        return pending.size;
    }

    private static int[] repliesOf(int commandOpcode) {
        return commandOpcode >= 0 && commandOpcode < REPLIES_BY_COMMAND.length
                ? REPLIES_BY_COMMAND[commandOpcode]
                : null;
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Quy ước của bảng opcode: lệnh {@code N} ({@code *_COMMAND}/{@code *_REQUEST}) được trả lời bằng
     * {@code N+1} ({@code *_RESPONSE}). Ngoại lệ duy nhất là luồng thoại ({@link #DIALOGUE_REPLIES}).
     */
    private static int[][] buildReplies() {
        int maxOpcode = 0;
        for (Opcode opcode : Opcode.values()) {
            if (opcode != Opcode.UNRECOGNIZED) {
                maxOpcode = Math.max(maxOpcode, opcode.getNumber());
            }
        }
        int[][] replies = new int[maxOpcode + 1][];
        for (Opcode command : Opcode.values()) {
            if (command == Opcode.UNRECOGNIZED || !isCommand(command.name())) {
                continue;
            }
            Opcode reply = Opcode.forNumber(command.getNumber() + 1);
            if (reply != null
                    && reply.name().endsWith("_RESPONSE")
                    && !UNFILTERED_REPLIES.contains(reply.getNumber())) {
                replies[command.getNumber()] = new int[] {reply.getNumber()};
            }
        }
        replies[Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE] = DIALOGUE_REPLIES;
        replies[Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND_VALUE] = DIALOGUE_REPLIES;
        return replies;
    }

    private static boolean isCommand(String name) {
        return name.endsWith("_COMMAND") || name.endsWith("_REQUEST");
    }

    private static BitSet filteredReplies() {
        BitSet all = new BitSet();
        for (int[] replies : REPLIES_BY_COMMAND) {
            if (replies != null) {
                for (int reply : replies) {
                    all.set(reply);
                }
            }
        }
        return all;
    }
}
