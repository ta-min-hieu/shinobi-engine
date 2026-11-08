package dev.kage.server.gateway;

import dev.kage.character.api.InspectPrivacyFacade;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.protocol.DeleteFinishedMailResponse;
import dev.kage.protocol.PinMailCommand;
import dev.kage.protocol.PinMailResponse;
import dev.kage.protocol.SetBioCommand;
import dev.kage.protocol.SetBioOutcome;
import dev.kage.protocol.SetBioResponse;
import dev.kage.protocol.SplitStackCommand;
import dev.kage.protocol.SplitStackOutcome;
import dev.kage.protocol.SplitStackResponse;

/** Lớp lệnh lô 36 (ADR-215…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot36Commands(
        Lot36Facades facades,
        InspectPrivacyFacade profiles,
        ConnectedCharacterRegistry registry,
        GuildInviteCommands guildInvites) {

    static Lot36Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        return new Lot36Commands(
                gateway.progression().expansion().lot34().lot35().lot36(),
                gateway.progression().expansion().lot30().inspectPrivacy(),
                worldGateway.registry(),
                new GuildInviteCommands(new GuildInviteCommands.Deps(
                        gateway.characterFacade(),
                        gateway.contentFacades().socialFacades().guildFacade(),
                        worldGateway.registry(),
                        java.time.Clock.systemUTC())));
    }

    /** ADR-215 — Virtual Thread; tách {@code amount} cái khỏi một chồng sang ô mới. */
    SplitStackResponse splitStack(long characterId, SplitStackCommand command) {
        return SplitStackResponse.newBuilder()
                .setOutcome(SplitStackOutcome.valueOf("SPLIT_STACK_OUTCOME_"
                        + facades.stackSplit()
                                .split(characterId, command.getInventoryItemId(), command.getAmount())
                                .name()))
                .build();
    }

    /** ADR-216 — Virtual Thread; xoá mọi thư đã nhận / không đính kèm, trừ thư ghim. */
    DeleteFinishedMailResponse deleteFinishedMail(long characterId) {
        return DeleteFinishedMailResponse.newBuilder()
                .setDeleted(facades.mailHousekeeping().deleteFinished(characterId))
                .build();
    }

    /** ADR-217 — Virtual Thread; ghim / bỏ ghim một thư của chính mình. */
    PinMailResponse pinMail(long characterId, PinMailCommand command) {
        boolean ok = facades.mailHousekeeping().setPinned(characterId, command.getMailId(), command.getPinned());
        return PinMailResponse.newBuilder()
                .setOk(ok)
                .setPinned(ok && command.getPinned())
                .build();
    }

    /** ADR-218 — Virtual Thread; ghi dòng giới thiệu (lọc từ cấm như chat). */
    SetBioResponse setBio(long characterId, SetBioCommand command) {
        SetBioOutcome outcome;
        if (ProfanityFilter.containsBannedWord(command.getBio())) {
            outcome = SetBioOutcome.SET_BIO_OUTCOME_BANNED_WORD;
        } else if (profiles.setBio(characterId, command.getBio())) {
            outcome = SetBioOutcome.SET_BIO_OUTCOME_OK;
        } else {
            outcome = SetBioOutcome.SET_BIO_OUTCOME_TOO_LONG;
        }
        return SetBioResponse.newBuilder()
                .setOutcome(outcome)
                .setBio(profiles.bio(characterId))
                .build();
    }
}
