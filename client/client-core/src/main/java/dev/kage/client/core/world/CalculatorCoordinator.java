package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Máy tính nhanh (ADR-302) — {@code /tinh <biểu thức>} (hoặc {@code /calc}): in kết quả vào chat, chỉ máy mình thấy.
 */
final class CalculatorCoordinator implements WindowFlow {

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    CalculatorCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/tinh".equals(command) && !"/calc".equals(command)) {
            return false;
        }
        String expression = words.length > 1 ? words[1] : "";
        chat.showSystemNotice(ArithmeticExpression.evaluate(expression)
                .map(value -> KageI18n.formatOrKey(i18n, "calc.result", expression.strip(), value.toPlainString()))
                .orElseGet(() -> KageI18n.textOrKey(i18n, "calc.usage")));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
