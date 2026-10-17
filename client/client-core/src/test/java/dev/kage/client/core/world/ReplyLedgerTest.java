package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;

/** Dòng 67(a) (2026-10-17) — gói trả lời chỉ đi tiếp khi khớp một lệnh đang chờ. */
class ReplyLedgerTest {

    private final ReplyLedger ledger = new ReplyLedger();

    @Test
    void shouldAdmitTheReplyToAPendingCommand_exactlyOnce() {
        ledger.expect(Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE, 41);

        assertThat(ledger.admit(Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE, 41))
                .isTrue();
        assertThat(ledger.admit(Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE, 41))
                .as("mỗi lệnh một trả lời")
                .isFalse();
    }

    @Test
    void shouldDropAReplyNobodyAskedFor() {
        assertThat(ledger.admit(Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_RESPONSE_VALUE, 7))
                .isFalse();
        assertThat(ledger.admit(Opcode.OPCODE_LIST_FRIENDS_RESPONSE_VALUE, 0)).isFalse();
    }

    @Test
    void shouldDropAReplyWhoseSequenceIdBelongsToADifferentCommand() {
        ledger.expect(Opcode.OPCODE_LIST_FRIENDS_REQUEST_VALUE, 5);

        assertThat(ledger.admit(Opcode.OPCODE_LIST_GUILD_MEMBERS_RESPONSE_VALUE, 5))
                .isFalse();
        assertThat(ledger.admit(Opcode.OPCODE_LIST_FRIENDS_RESPONSE_VALUE, 5)).isTrue();
    }

    @Test
    void eventsErrorsPongAndMovementAreNeverFiltered() {
        assertThat(ledger.admit(Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE, 0)).isTrue();
        assertThat(ledger.admit(Opcode.OPCODE_ERROR_VALUE, 999)).isTrue();
        assertThat(ledger.admit(Opcode.OPCODE_PONG_VALUE, 3)).isTrue();
        assertThat(ledger.admit(Opcode.OPCODE_MOVE_RESPONSE_VALUE, 12)).isTrue();
        assertThat(ledger.admit(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE, 1))
                .isTrue();
    }

    @Test
    void dialogueCommandsAcceptEveryDialogueReply() {
        ledger.expect(Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE, 1);
        ledger.expect(Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND_VALUE, 2);
        ledger.expect(Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND_VALUE, 3);

        assertThat(ledger.admit(Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE, 1)).isTrue();
        assertThat(ledger.admit(Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE, 2)).isTrue();
        assertThat(ledger.admit(Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE, 3))
                .isTrue();
    }

    @Test
    void anErrorShouldForgetTheRejectedCommand() {
        ledger.expect(Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, 9);

        ledger.forget(9);

        assertThat(ledger.pendingCount()).isZero();
        assertThat(ledger.admit(Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE, 9)).isFalse();
    }

    @Test
    void commandsWithoutAFilteredReplyAreNotRecorded() {
        ledger.expect(Opcode.OPCODE_MOVE_COMMAND_VALUE, 1);
        ledger.expect(Opcode.OPCODE_ATTACK_COMMAND_VALUE, 2);
        ledger.expect(Opcode.OPCODE_JUMP_COMMAND_VALUE, 3);

        assertThat(ledger.pendingCount()).isZero();
    }

    @Test
    void shouldForgetTheOldestCommand_onceTheCapIsReached() {
        for (int sequenceId = 1; sequenceId <= ReplyLedger.MAX_PENDING + 10; sequenceId++) {
            ledger.expect(Opcode.OPCODE_LIST_MAIL_REQUEST_VALUE, sequenceId);
        }

        assertThat(ledger.pendingCount()).isEqualTo(ReplyLedger.MAX_PENDING);
        assertThat(ledger.admit(Opcode.OPCODE_LIST_MAIL_RESPONSE_VALUE, 1))
                .as("cũ nhất đã bị bỏ")
                .isFalse();
        assertThat(ledger.admit(Opcode.OPCODE_LIST_MAIL_RESPONSE_VALUE, ReplyLedger.MAX_PENDING + 10))
                .isTrue();
    }
}
