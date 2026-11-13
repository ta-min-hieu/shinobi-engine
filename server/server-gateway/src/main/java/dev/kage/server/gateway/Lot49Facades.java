package dev.kage.server.gateway;

import dev.kage.character.api.BreakthroughLogFacade;
import dev.kage.character.api.ReferralFacade;
import dev.kage.guild.api.GuildMemberNoteFacade;

/** Facade lô 49 (ADR-319…) gom thành MỘT thành phần của {@link Lot48Facades}. */
record Lot49Facades(GuildMemberNoteFacade memberNotes, ReferralFacade referral, BreakthroughLogFacade breakthroughs) {

    /** Composition không có các tính năng này (test). */
    static final Lot49Facades DISABLED =
            new Lot49Facades(GuildMemberNoteFacade.DISABLED, ReferralFacade.DISABLED, BreakthroughLogFacade.DISABLED);
}
