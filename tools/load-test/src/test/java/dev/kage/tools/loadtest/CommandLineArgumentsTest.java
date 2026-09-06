package dev.kage.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cú pháp {@code --key=value} dùng chung cho load test và probe — thuần logic, không cần server. */
final class CommandLineArgumentsTest {

    @Test
    void shouldReadKeyValuePairs() {
        CommandLineArguments args = CommandLineArguments.parse(new String[] {"--host=10.0.0.2", "--port=7788"});

        assertThat(args.text("host", "127.0.0.1")).isEqualTo("10.0.0.2");
        assertThat(args.integer("port", 7777)).isEqualTo(7788);
    }

    @Test
    void shouldFallBackToDefault_whenKeyAbsent() {
        CommandLineArguments args = CommandLineArguments.parse(new String[0]);

        assertThat(args.text("host", "127.0.0.1")).isEqualTo("127.0.0.1");
        assertThat(args.integer("port", 7777)).isEqualTo(7777);
        assertThat(args.number("characterId", 0L)).isZero();
        assertThat(args.optionalDouble("moveToX")).isEmpty();
    }

    /** Gradle chèn tham số của riêng nó vào là chuyện thường — bỏ qua im lặng, đừng nổ. */
    @Test
    void shouldIgnoreArgumentsThatAreNotDoubleDashKeyValue() {
        CommandLineArguments args =
                CommandLineArguments.parse(new String[] {"--flagWithoutValue", "plain", "-x=1", "--=empty"});

        assertThat(args.text("flagWithoutValue", "(none)")).isEqualTo("(none)");
        assertThat(args.text("", "(none)")).isEqualTo("(none)");
        assertThat(args.text("x", "(none)")).isEqualTo("(none)");
    }

    /** Giá trị chứa dấu {@code =} (vd. base64) phải giữ nguyên phần sau dấu bằng ĐẦU TIÊN. */
    @Test
    void shouldSplitOnFirstEqualsOnly() {
        CommandLineArguments args = CommandLineArguments.parse(new String[] {"--note=a=b=c"});

        assertThat(args.text("note", "")).isEqualTo("a=b=c");
    }
}
