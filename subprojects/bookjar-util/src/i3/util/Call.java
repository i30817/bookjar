package i3.util;

/**
 * A interface for callbacks, differs from callable by not throwing
 */
public interface Call<T> {

    /**
     * Execute callback.
     * @param arguments
     */
    void run(T argument);
}
