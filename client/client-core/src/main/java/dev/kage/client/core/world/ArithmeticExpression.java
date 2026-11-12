package dev.kage.client.core.world;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Máy tính nhanh (ADR-302) — tính biểu thức số học {@code + - * / ( )}, số thập phân, dấu âm đơn ngôi. Đệ quy xuống
 * đơn giản, không eval, không reflection. Biểu thức sai / chia cho 0 / quá dài / lồng quá sâu ⇒ rỗng.
 */
final class ArithmeticExpression {

    /** Dài nhất được nhận (chặn dòng chat khổng lồ). */
    static final int MAX_LENGTH = 120;

    /** Lồng ngoặc sâu nhất (chặn tràn stack). */
    static final int MAX_DEPTH = 32;

    private static final MathContext PRECISION = new MathContext(20, RoundingMode.HALF_UP);
    private static final int SCALE = 6;

    private final String text;
    private int position;
    private int depth;

    private ArithmeticExpression(String text) {
        this.text = text;
    }

    /** Giá trị của {@code expression} (đã gọn số 0 thừa), hoặc rỗng nếu không tính được. */
    static Optional<BigDecimal> evaluate(String expression) {
        String compact =
                expression.replace(" ", "").replace(',', '.').replace('x', '*').replace(':', '/');
        if (compact.isEmpty() || compact.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        ArithmeticExpression parser = new ArithmeticExpression(compact);
        Optional<BigDecimal> value = parser.sum();
        if (value.isEmpty() || parser.position != compact.length()) {
            return Optional.empty();
        }
        BigDecimal rounded = value.get().setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        return Optional.of(rounded.scale() < 0 ? rounded.setScale(0) : rounded);
    }

    private Optional<BigDecimal> sum() {
        Optional<BigDecimal> left = product();
        while (left.isPresent()
                && position < text.length()
                && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
            char operator = text.charAt(position++);
            Optional<BigDecimal> right = product();
            left = right.isEmpty()
                    ? right
                    : Optional.of(
                            operator == '+'
                                    ? left.get().add(right.get())
                                    : left.get().subtract(right.get()));
        }
        return left;
    }

    private Optional<BigDecimal> product() {
        Optional<BigDecimal> left = unary();
        while (left.isPresent()
                && position < text.length()
                && (text.charAt(position) == '*' || text.charAt(position) == '/')) {
            char operator = text.charAt(position++);
            Optional<BigDecimal> right = unary();
            left = right.isEmpty() ? right : apply(left.get(), operator, right.get());
        }
        return left;
    }

    private static Optional<BigDecimal> apply(BigDecimal left, char operator, BigDecimal right) {
        if (operator == '*') {
            return Optional.of(left.multiply(right, PRECISION));
        }
        return right.signum() == 0 ? Optional.empty() : Optional.of(left.divide(right, PRECISION));
    }

    private Optional<BigDecimal> unary() {
        if (position < text.length() && text.charAt(position) == '-') {
            position++;
            return unary().map(BigDecimal::negate);
        }
        if (position < text.length() && text.charAt(position) == '+') {
            position++;
            return unary();
        }
        return atom();
    }

    private Optional<BigDecimal> atom() {
        if (position < text.length() && text.charAt(position) == '(') {
            if (++depth > MAX_DEPTH) {
                return Optional.empty();
            }
            position++;
            Optional<BigDecimal> inner = sum();
            if (inner.isEmpty() || position >= text.length() || text.charAt(position) != ')') {
                return Optional.empty();
            }
            position++;
            depth--;
            return inner;
        }
        return number();
    }

    private Optional<BigDecimal> number() {
        int start = position;
        while (position < text.length() && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) {
            position++;
        }
        String digits = text.substring(start, position);
        if (digits.isEmpty() || digits.indexOf('.') != digits.lastIndexOf('.') || ".".equals(digits)) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(digits));
    }
}
