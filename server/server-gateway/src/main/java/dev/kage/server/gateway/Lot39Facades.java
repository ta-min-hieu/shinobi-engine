package dev.kage.server.gateway;

import dev.kage.character.api.PersonalRecordFacade;
import dev.kage.character.api.WealthRankingFacade;
import dev.kage.guild.api.GuildBuffFacade;
import dev.kage.quest.api.QuestRewardPreviewFacade;

/** Facade lô 39 (ADR-239…) gom thành MỘT thành phần của {@link Lot38Facades}. */
record Lot39Facades(
        GuildBuffFacade guildBuff,
        PersonalRecordFacade personalRecords,
        WealthRankingFacade wealth,
        QuestRewardPreviewFacade questPreview) {

    /** Composition không có các tính năng này (test). */
    static final Lot39Facades DISABLED = new Lot39Facades(
            GuildBuffFacade.DISABLED,
            PersonalRecordFacade.DISABLED,
            WealthRankingFacade.DISABLED,
            QuestRewardPreviewFacade.DISABLED);
}
