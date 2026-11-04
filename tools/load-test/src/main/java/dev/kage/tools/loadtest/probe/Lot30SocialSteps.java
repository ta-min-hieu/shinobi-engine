package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenameGuildCommand;
import dev.kage.protocol.RenameGuildResponse;

/**
 * Bước lô 30 (phần xã hội): đổi tên bang (ADR-172) {@code renameGuild:<tên>}. Tách khỏi {@link Lot30Steps} vì switch ở
 * đó chạm trần CyclomaticComplexity.
 */
final class Lot30SocialSteps {

    private final Lot23Scenario.Asker asker;

    Lot30SocialSteps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "renameGuild" -> renameGuild(arg);
            default -> "bước không hiểu";
        };
    }

    private String renameGuild(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_RENAME_GUILD_COMMAND_VALUE,
                RenameGuildCommand.newBuilder().setNewName(name).build(),
                Opcode.OPCODE_RENAME_GUILD_RESPONSE_VALUE,
                p -> {
                    RenameGuildResponse r = RenameGuildResponse.parseFrom(p);
                    return r.getOutcome().name() + " tên=" + r.getName() + " phí=" + r.getFee();
                });
    }
}
