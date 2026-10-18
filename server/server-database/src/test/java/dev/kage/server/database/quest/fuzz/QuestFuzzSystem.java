package dev.kage.server.database.quest.fuzz;

import javax.sql.DataSource;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.application.AbandonQuestUseCase;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ListQuestsUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.dialogue.PostgresNpcTemplateAdapter;
import dev.kage.server.database.quest.JdbcQuestTransactionRunner;
import dev.kage.server.database.quest.PostgresQuestAuditAdapter;
import dev.kage.server.database.quest.PostgresQuestExpAdapter;
import dev.kage.server.database.quest.PostgresQuestGoldAdapter;
import dev.kage.server.database.quest.PostgresQuestIdempotencyAdapter;
import dev.kage.server.database.quest.PostgresQuestInventoryGrantAdapter;
import dev.kage.server.database.quest.PostgresQuestItemTakeAdapter;
import dev.kage.server.database.quest.PostgresQuestProgressAdapter;
import dev.kage.server.database.quest.PostgresQuestTemplateAdapter;

/**
 * Ba đối tượng gateway gọi cho vòng quest — lắp y hệt {@code ServerDatabaseBootstrap}: QuestFacade
 * (nhận/sự kiện/lĩnh/nhật ký), DialogueFacade (cây hội thoại, nơi lệnh nhận quest thật sự tới), và
 * {@link GrantExperienceUseCase} (EXP hạ quái + hoà giải cột level sau khi lĩnh thưởng, tức
 * {@code CharacterFacade.grantExperience}/{@code syncExperienceAfterQuestReward}).
 *
 * <p>"Đăng nhập lại" = vứt bộ cũ, lắp bộ mới: không còn gì sống trong bộ nhớ ngoài DB, nên mọi thứ
 * đọc ra sau đó là thứ DB thật sự giữ.
 */
record QuestFuzzSystem(QuestFacade quest, DialogueFacade dialogue, GrantExperienceUseCase experience) {

    static QuestFuzzSystem wire(DataSource dataSource) {
        PostgresQuestProgressAdapter progressPort = new PostgresQuestProgressAdapter(dataSource);
        PostgresQuestTemplateAdapter templatePort = new PostgresQuestTemplateAdapter(dataSource);
        PostgresQuestAuditAdapter auditPort = new PostgresQuestAuditAdapter();
        JdbcQuestTransactionRunner transactionRunner = new JdbcQuestTransactionRunner(dataSource);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                auditPort,
                new PostgresQuestIdempotencyAdapter(),
                new PostgresQuestGoldAdapter());
        QuestFacade questFacade = new QuestFacade(
                new AcceptQuestUseCase(progressPort, templatePort, transactionRunner),
                new ProcessQuestEventUseCase(
                        progressPort, new PostgresQuestItemTakeAdapter(), auditPort, transactionRunner),
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, transactionRunner),
                new dev.kage.quest.api.QuestLogUseCases(
                        new ListQuestsUseCase(progressPort),
                        new AbandonQuestUseCase(progressPort, templatePort, auditPort, transactionRunner)));
        DialogueFacade dialogueFacade =
                new DialogueFacade(new GetDialogueNodeUseCase(new PostgresNpcTemplateAdapter(dataSource)));
        return new QuestFuzzSystem(
                questFacade, dialogueFacade, new GrantExperienceUseCase(new PostgresCharacterAdapter(dataSource)));
    }
}
