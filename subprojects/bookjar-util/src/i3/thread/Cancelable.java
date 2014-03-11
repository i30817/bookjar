package i3.thread;

import org.apache.logging.log4j.LogManager;

/**
 * Cancellables are runnables that can be cancelled when interrupted and used
 * with the Executors returned by util.threads.Threads.
 *
 * @author i30817
 */
public abstract class Cancelable implements Runnable {

    @Override
    public void run() {
        try {
            compute();
        } catch (Exception ex) {
            LogManager.getLogger().error("chain threw exception, cancelling: ", ex);
            doCancel(ex);
        }
    }

    void doCancel(Exception ex) {
        try {
            cancel(ex);
        } catch (Exception e) {
            LogManager.getLogger().error("cancel threw exception: ", e);
        }
    }

    /**
     * Do the computation. If an unrecoverable error/exception occurs, you
     * should let it escape, so that cancel can be called.
     */
    protected abstract void compute() throws Exception;

    /**
     * Cancel a link. This is automatically called when runLink throws a
     * Exception. It can be manually called by Executors returned by Threads if
     * shutdown is called and a ChainCallable was running
     *
     * @see Threads
     *
     * Dont assume cancel() is called before, during or after runLink(), or in
     * the same thread or not. Exceptions and Errors thrown during this method
     * will be logged, but not rethrown.
     * @param cause the cancellation cause
     */
    protected void cancel(Exception cause) throws Exception {
    }
}
