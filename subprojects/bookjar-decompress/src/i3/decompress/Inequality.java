package i3.decompress;

public interface Inequality<T extends Comparable<T>> {

    /**
     * Check the boolean value of the inequality
     * given the missing value
     * @param i
     * @return
     */
    boolean check(T i);

    /**
     * Use this function when you want to find the negatory test
     * @return a Inequality
     */
    Inequality<T> getNegation();
}
