package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

/**
 * Bắt mọi dòng log ERROR của {@code dev.kage.*} trong lúc fuzz — input do client gây ra KHÔNG được
 * sinh log ERROR (log ERROR = "server hỏng", là thứ trực ban bị đánh thức vì nó).
 *
 * <p>Trong lúc gắn, log {@code dev.kage.*} KHÔNG ra console ({@code additive=false}): vài nghìn payload
 * hỏng là vài nghìn dòng WARN kèm stack trace (câu chờ chủ dự án, bảng chờ dòng 44), làm chìm mất
 * báo cáo của chính bộ fuzz. Tháo ra ({@link #close()}) thì trả nguyên cấu hình cũ.
 */
final class ErrorLogCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {

    private static final int STACK_FRAMES_IN_SUMMARY = 6;

    private final Logger logger;
    private final boolean previousAdditive;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private ErrorLogCapture(Logger logger) {
        this.logger = logger;
        this.previousAdditive = logger.isAdditive();
    }

    static ErrorLogCapture attach() {
        Logger logger = (Logger) LoggerFactory.getLogger("dev.kage");
        ErrorLogCapture capture = new ErrorLogCapture(logger);
        capture.setContext(logger.getLoggerContext());
        capture.setName("fuzz-error-capture");
        capture.start();
        logger.addAppender(capture);
        logger.setAdditive(false);
        return capture;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel().isGreaterOrEqual(Level.ERROR)) {
            String summary = summarize(event);
            synchronized (errors) {
                errors.add(summary);
            }
        } else if (event.getLevel() == Level.WARN) {
            String summary = summarize(event);
            synchronized (warnings) {
                warnings.add(summary);
            }
        }
    }

    /** Số dòng ERROR đã bắt — dùng làm mốc trước một payload. */
    int errorCount() {
        synchronized (errors) {
            return errors.size();
        }
    }

    /** Các dòng ERROR bắt được từ mốc {@code fromIndex}. */
    List<String> errorsSince(int fromIndex) {
        synchronized (errors) {
            return List.copyOf(errors.subList(fromIndex, errors.size()));
        }
    }

    /** Các dòng WARN (đúng mức WARN) bắt được từ mốc {@code fromIndex}. */
    List<String> warningsSince(int fromIndex) {
        synchronized (warnings) {
            return List.copyOf(warnings.subList(fromIndex, warnings.size()));
        }
    }

    @Override
    public void close() {
        logger.detachAppender(this);
        logger.setAdditive(previousAdditive);
        stop();
    }

    private static String summarize(ILoggingEvent event) {
        StringBuilder text =
                new StringBuilder().append(event.getLoggerName()).append(": ").append(event.getFormattedMessage());
        IThrowableProxy cause = event.getThrowableProxy();
        while (cause != null) {
            text.append("\n    ").append(cause.getClassName()).append(": ").append(cause.getMessage());
            StackTraceElementProxy[] frames = cause.getStackTraceElementProxyArray();
            for (int i = 0; i < Math.min(STACK_FRAMES_IN_SUMMARY, frames.length); i++) {
                text.append("\n        at ").append(frames[i].getStackTraceElement());
            }
            cause = cause.getCause();
        }
        return text.toString();
    }
}
