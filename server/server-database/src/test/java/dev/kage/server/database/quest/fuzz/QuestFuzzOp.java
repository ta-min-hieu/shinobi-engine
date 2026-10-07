package dev.kage.server.database.quest.fuzz;

import java.util.List;

/**
 * Một lệnh người chơi (hoặc sự kiện thế giới) của bộ fuzz — đúng những gì gateway chuyển vào
 * QuestFacade/DialogueFacade/CharacterFacade. Mỗi lệnh chạy được tuần tự hoặc chen nhau trong một
 * loạt song song.
 */
sealed interface QuestFuzzOp {

    /** Quái chết dưới tay nhân vật: sự kiện quest + EXP hạ quái (hai lời gọi, như gateway). */
    record Kill(String monster) implements QuestFuzzOp {}

    /** {@code TalkToNpcCommand}: mở hội thoại; NPC có thật thì bắn sự kiện {@code npc_talked}. */
    record Talk(String npc) implements QuestFuzzOp {}

    /** Gọi thẳng {@code acceptQuest} — cả mã bịa, cả quest chưa tới lượt. */
    record Accept(String questCode) implements QuestFuzzOp {}

    /**
     * Mở lời rồi bấm lần lượt {@code optionIndexes} trong cây hội thoại (chỉ số có thể ngoài khoảng);
     * gặp {@code accept_quest:<code>} thì nhận quest y như gateway.
     */
    record Dialogue(String npc, List<Integer> optionIndexes) implements QuestFuzzOp {
        public Dialogue {
            optionIndexes = List.copyOf(optionIndexes);
        }
    }

    /** {@code ClaimQuestRewardCommand} — lĩnh xong OK thì hoà giải vạch EXP như gateway. */
    record Claim(String questCode, String idempotencyKey) implements QuestFuzzOp {}
}
