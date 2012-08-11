package i3.decompress;


/**
 * This class is used to signal a inequality direction.
 * This is a good class for the construction of apis over
 * Comparable types.
 *
 * @author i30817
 */
public class Inequalities {

    private Inequalities() {
    }

    public static <T extends Comparable<T>> Inequality<T> largerThan(T value) {
        return new LARGER<>(value);
    }

    public static <T extends Comparable<T>> Inequality<T> lesserThan(T value) {
        return new LESSER<>(value);
    }

    public static <T extends Comparable<T>> Inequality<T> largerOrEqualTo(T value) {
        return new LARGER_OR_EQUAL<>(value);
    }

    public static <T extends Comparable<T>> Inequality<T> lesserOrEqualTo(T value) {
        return new LESSER_OR_EQUAL<>(value);
    }

    public static <T extends Comparable<T>> Inequality<T> equalTo(T value) {
        return new EQUAL<>(value);
    }

    public static <T extends Comparable<T>> Inequality<T> notEqualTo(T value) {
        return new NOT_EQUAL<>(value);
    }

    private final static class LARGER<T extends Comparable<T>> implements Inequality<T> {

        T value;

        public LARGER(T value) {
            this.value = value;
        }

        @Override
        public Inequality<T> getNegation() {
            return new LESSER_OR_EQUAL<>(value);
        }

        @Override
        public boolean check(T i) {
            return value.compareTo(i) > 0;
        }
    }

    private final static class LARGER_OR_EQUAL<T extends Comparable<T>> implements Inequality<T> {

        T value;

        public LARGER_OR_EQUAL(T value) {
            this.value = value;
        }

        @Override
        public Inequality<T> getNegation() {
            return new LESSER<>(value);
        }

        @Override
        public boolean check(T i) {
            return value.compareTo(i) >= 0;
        }
    }

    private final static class LESSER<T extends Comparable<T>> implements Inequality<T> {

        T value;

        public LESSER(T value) {
            this.value = value;
        }

        @Override
        public Inequality<T> getNegation() {
            return new LARGER_OR_EQUAL<>(value);
        }

        @Override
        public boolean check(T i) {
            return value.compareTo(i) < 0;
        }
    }

    private final static class LESSER_OR_EQUAL<T extends Comparable<T>> implements Inequality<T> {

        T value;

        public LESSER_OR_EQUAL(T value) {
            this.value = value;
        }

        @Override
        public Inequality<T> getNegation() {
            return new LARGER<>(value);
        }

        @Override
        public boolean check(T i) {
            return value.compareTo(i) <= 0;
        }
    }

    private final static class EQUAL<T extends Comparable<T>> implements Inequality<T> {

        T value;

        public EQUAL(T value) {
            this.value = value;
        }

        @Override
        public Inequality<T> getNegation() {
            return new NOT_EQUAL<>(value);
        }

        @Override
        public boolean check(T i) {
            return value.compareTo(i) == 0;
        }
    }

    private final static class NOT_EQUAL<T extends Comparable<T>> implements Inequality<T> {

        T value;

        public NOT_EQUAL(T value) {
            this.value = value;
        }

        @Override
        public Inequality<T> getNegation() {
            return new EQUAL<>(value);
        }

        @Override
        public boolean check(T i) {
            return value.compareTo(i) != 0;
        }
    }
}
