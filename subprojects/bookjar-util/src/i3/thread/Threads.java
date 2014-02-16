package i3.thread;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import i3.io.IoUtils;
import java.util.concurrent.Callable;

/**
 * The queues returned by this can use Cancellables or normal
 * callables/runnables, and have special thread creation and survival
 * characteristics.
 *
 * Warning: Some IO tasks don't respond to interrupts. A common hang like this
 * is using url.openStream - one solution is to set the default timeout
 * properties, but that can lead to failures in slow networks. Instead, in the
 * Cancellable save the HttpURLConnection (with a cast from url.openConnection)
 * in a volatile field, and in the doCancel(Throwable) method : if(conn !=
 * null){ conn.setConnectTimeout(1); conn.setReadTimeout(1); conn.disconnect();
 * }
 *
 * @author Owner
 */
public final class Threads {

    /**
     * This future is a null object. Its get method does nothing, but it returns
     * the given value.
     */
    public static <T> Future<T> newObjectFuture(T value) {
        return new FutureTask<>(new Runnable() {

            @Override
            public void run() {
            }
        }, value);
    }

    /**
     * This callable is a null object. Its call method does nothing, but it
     * returns the given value.
     */
    public static <T> Callable<T> newObjectCallable(final T value) {
        return new Callable<T>() {

            @Override
            public T call() throws Exception {
                return value;
            }
        };
    }

    private static CancellableExecutor createExecutor(boolean shutdownOnExit, int minimumNThreads, int maximumNThreads, long secondsTimeOut, BlockingQueue<Runnable> queue, String name) {
        final CancellableExecutor executor = new CancellableExecutor(
                minimumNThreads,
                maximumNThreads,
                secondsTimeOut,
                TimeUnit.SECONDS,
                queue,
                IoUtils.createThreadFactory(true, "ChainExecutor-" + name));
        if (shutdownOnExit) {
            IoUtils.addShutdownHook(new Runnable() {

                @Override
                public void run() {
                    executor.shutdownNow();
                }
            });
        }
        return executor;
    }

    private Threads() {
    }

    /**
     * A pool with exactly N threads that rejects any task after if there are no
     * threads available.
     *
     * @throws RejectedExecutionException if a task is submited when all threads
     * are busy.
     */
    public static ExecutorService newFixedRejectingExecutor(String name, int nThreads, boolean shutdownOnExit) {
        return createExecutor(shutdownOnExit, nThreads, nThreads, 0L, new LinkedBlockingQueue<Runnable>(nThreads), name);
    }

    /**
     * A pool with exactly N threads that discards any task after if there are
     * no threads available.
     */
    public static ExecutorService newFixedDiscardingExecutor(String name, int nThreads, boolean shutdownOnExit) {
        CancellableExecutor executor = createExecutor(shutdownOnExit, nThreads, nThreads, 0L, new LinkedBlockingQueue<Runnable>(nThreads), name);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }

    /**
     * A pool with exactly nThreads that don't timeout
     *
     * @param nThreads > 0
     * @param shutdownOnExit run a Shutdown Hook to shutdown on exit.
     * @return
     */
    public static ExecutorService newFIFOFixedExecutor(String name, int nThreads, boolean shutdownOnExit) {
        return createExecutor(shutdownOnExit, nThreads, nThreads, 0L, new LinkedBlockingQueue<Runnable>(), name);
    }

    /**
     * A pool with exactly nThreads that don't timeout
     *
     * @param nThreads > 0
     * @param shutdownOnExit run a Shutdown Hook to shutdown on exit.
     * @return
     */
    public static ExecutorService newLIFOFixedExecutor(String name, int nThreads, boolean shutdownOnExit) {
        return createExecutor(shutdownOnExit, nThreads, nThreads, 0L, new Stack<Runnable>(), name);
    }

    /**
     * A pool that has no maximum number of threads and will kill the threads
     * after a timeout after the last task
     *
     * @param secondsTimeOut > 0
     * @param shutdownOnExit run a Shutdown Hook to shutdown on exit.
     * @return
     */
    public static ExecutorService newFIFOCachedExecutor(String name, long secondsTimeout, boolean shutdownOnExit) {
        return createExecutor(shutdownOnExit, 0, Integer.MAX_VALUE, secondsTimeout, new SynchronousQueue<Runnable>(), name);
    }

    /**
     * A pool that has a maximum number of threads and will kill the threads
     * after a timeout after the last task
     *
     * @param maximumNThreads > 0
     * @param secondsTimeOut > 0
     * @param shutdownOnExit run a Shutdown Hook to shutdown on exit.
     * @return
     */
    public static ExecutorService newFIFOScalingExecutor(String name, int maximumNThreads, long secondsTimeOut, boolean shutdownOnExit) {
        ScalingQueue<Runnable> queue = new ScalingQueue<>();
        CancellableExecutor executor = createExecutor(shutdownOnExit, 0, maximumNThreads, secondsTimeOut, queue, name);
        executor.setRejectedExecutionHandler(new AddToLargeQueuePolicy());
        queue.setThreadPoolExecutor(executor);
        return executor;
    }

    /**
     * A pool that has a maximum number of threads and will kill the threads
     * after a timeout after the last task
     *
     * @param maximumNThreads > 0
     * @param secondsTimeOut > 0
     * @param shutdownOnExit run a Shutdown Hook to shutdown on exit.
     * @return
     */
    public static ExecutorService newLIFOScalingExecutor(String name, int maximumNThreads, long secondsTimeOut, boolean shutdownOnExit) {
        ScalingStack<Runnable> queue = new ScalingStack<>();
        CancellableExecutor executor = createExecutor(shutdownOnExit, 0, maximumNThreads, secondsTimeOut, queue, name);
        executor.setRejectedExecutionHandler(new AddToLargeQueuePolicy());
        queue.setThreadPoolExecutor(executor);
        return executor;
    }

    /**
     * A pool that has a minimum number of threads, a maximum number of threads
     * and will kill maximumNThreads - minimumNThreads after a timeout after the
     * last task
     *
     * @param minimumNThreads >= 0
     * @param maximumNThreads > 0
     * @param secondsTimeOut > 0
     * @param shutdownOnExit run a Shutdown Hook to shutdown on exit.
     * @return
     */
    public static ExecutorService newFIFOScalingExecutor(String name, int minimumNThreads, int maximumNThreads, long secondsTimeOut, boolean shutdownOnExit) {
        ScalingQueue<Runnable> queue = new ScalingQueue<>();
        CancellableExecutor executor = createExecutor(shutdownOnExit, minimumNThreads, maximumNThreads, secondsTimeOut, queue, name);
        executor.setRejectedExecutionHandler(new AddToLargeQueuePolicy());
        queue.setThreadPoolExecutor(executor);
        return executor;
    }

    /**
     * A pool that has a minimum number of threads, a maximum number of threads
     * and will kill maximumNThreads - minimumNThreads after a timeout after the
     * last task
     *
     * @param minimumNThreads >= 0
     * @param maximumNThreads > 0
     * @param secondsTimeOut > 0
     * @param shutdownOnExit run a Shutdown Hook to shutdown on exit.
     * @return
     */
    public static ExecutorService newLIFOScalingExecutor(String name, int minimumNThreads, int maximumNThreads, long secondsTimeOut, boolean shutdownOnExit) {
        ScalingStack<Runnable> queue = new ScalingStack<>();
        CancellableExecutor executor = createExecutor(shutdownOnExit, minimumNThreads, maximumNThreads, secondsTimeOut, queue, name);
        executor.setRejectedExecutionHandler(new AddToLargeQueuePolicy());
        queue.setThreadPoolExecutor(executor);
        return executor;
    }

    /**
     * As you can see, we are going to reject the addition of a new task if
     * there are no threads to handle it. This will cause the thread pool
     * executor to try and allocate a new thread (up to the maximum threads). If
     * there are no threads, the task will be rejected. In our case, if the task
     * is rejected, we would like to put it back to the queue. This is a simple
     * thing to do with ThreadPoolExecutor since we can implement our own
     * RejectedExecutionHandler
     */
    private static final class ScalingQueue<E> extends LinkedBlockingQueue<E> {

        /**
         * The executor this Queue belongs to
         */
        private ThreadPoolExecutor executor;

        /**
         * Creates a <tt>TaskQueue</tt> with a capacity of
         * {@link Integer#MAX_VALUE}.
         */
        public ScalingQueue() {
            super();
        }

        /**
         * Creates a <tt>TaskQueue</tt> with the given (fixed) capacity.
         *
         * @param capacity the capacity of this queue.
         */
        public ScalingQueue(int capacity) {
            super(capacity);
        }

        /**
         * Sets the executor this queue belongs to.
         */
        public void setThreadPoolExecutor(ThreadPoolExecutor executor) {
            this.executor = executor;
        }

        /**
         * Inserts the specified element at the tail of this queue if there is
         * at least one available thread to run the current task. If all pool
         * threads are actively busy, it rejects the offer.
         *
         * @param o the element to add.
         * @return <tt>true</tt> if it was possible to add the element to this
         * queue, else <tt>false</tt>
         * @see ThreadPoolExecutor#execute(Runnable)
         */
        @Override
        public boolean offer(E o) {
            int toBeWorkingThreads = executor.getActiveCount() + super.size();
            return toBeWorkingThreads < executor.getPoolSize() && super.offer(o);
        }
    }

    /**
     * A queue that acts like a lifo stack it has final almost everywhere as a
     * performance hack
     */
    private static class Stack<E> extends LinkedBlockingDeque<E> {

        public Stack(int capacity) {
            super(capacity);
        }

        public Stack() {
            super();
        }

        @Override
        public final boolean add(E e) {
            addFirst(e);
            return true;
        }

        @Override
        public boolean offer(E o) {
            return super.offerFirst(o);
        }

        @Override
        public final E peek() {
            return peekFirst();
        }

        @Override
        public final E poll() {
            return pollFirst();
        }

        @Override
        public final E poll(long timeout, TimeUnit unit) throws InterruptedException {
            return pollFirst(timeout, unit);
        }

        @Override
        public final E pop() {
            return removeFirst();
        }

        @Override
        public final void push(E item) {
            addFirst(item);
        }

        @Override
        public final void put(E item) throws InterruptedException {
            putFirst(item);
        }

        @Override
        public final E take() throws InterruptedException {
            return takeFirst();
        }
    }

    /**
     * As you can see, we are going to reject the addition of a new task if
     * there are no threads to handle it. This will cause the thread pool
     * executor to try and allocate a new thread (up to the maximum threads). If
     * there are no threads, the task will be rejected. In our case, if the task
     * is rejected, we would like to put it back to the queue. This is a simple
     * thing to do with ThreadPoolExecutor since we can implement our own
     * RejectedExecutionHandler
     */
    private static final class ScalingStack<E> extends Stack<E> {

        /**
         * The executor this Queue belongs to
         */
        private ThreadPoolExecutor executor;

        public ScalingStack(int capacity) {
            super(capacity);
        }

        public ScalingStack() {
            super();
        }

        @Override
        public boolean offer(E o) {
            int toBeWorkingThreads = executor.getActiveCount() + super.size();
            return toBeWorkingThreads < executor.getPoolSize() && super.offer(o);
        }

        /**
         * Sets the executor this queue belongs to.
         */
        public void setThreadPoolExecutor(ThreadPoolExecutor executor) {
            this.executor = executor;
        }
    }

    /**
     * If rejected just add directly (doesn't block on all factories since the
     * ones that use this don't put a limit on the queue/stack)
     */
    private static final class AddToLargeQueuePolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                throw new RejectedExecutionException(e);
            }
        }
    }

    private static final class CancellableExecutor extends ThreadPoolExecutor implements ExecutorService {
        //needs to be protected by synchronized
        //(including the iterator, and Collections doesn't do that)

        private final Object lock = new Object();
        private Collection<DelegateFutureTask> tasks = new HashSet<>();

        /**
         * {@link  java.util.concurrent.ThreadPoolExecutor#ThreadPoolExecutor(int corePoolSize,
         * int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue workQueue, ThreadFactory threadFactory) superclass contructor passthruu}
         */
        public CancellableExecutor(int corePoolSize,
                int maximumPoolSize,
                long keepAliveTime,
                TimeUnit unit,
                BlockingQueue<Runnable> workQueue,
                ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        private final static class DelegateFutureTask<V> extends FutureTask<V> {

            private final Cancelable innerComputation;

            public DelegateFutureTask(Cancelable callable, V result) {
                super(callable, result);
                innerComputation = callable;
            }

            public Cancelable getInnerComputation() {
                return innerComputation;
            }
        }

        /**
         * Override so that we can recognize the cancellable.
         *
         * @param <T>
         * @param callable
         * @return
         */
        @Override
        protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
            if (runnable instanceof Cancelable) {
                return new DelegateFutureTask<>((Cancelable) runnable, value);
            }
            return super.newTaskFor(runnable, value);
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (r instanceof DelegateFutureTask) {
                synchronized (lock) {
                    tasks.remove((DelegateFutureTask) r);
                }
            }
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            if (r instanceof DelegateFutureTask) {
                synchronized (lock) {
                    tasks.add((DelegateFutureTask) r);
                }
            }

            super.beforeExecute(t, r);
        }

        @Override
        public List<Runnable> shutdownNow() {
            List<Runnable> unExecuted = super.shutdownNow();
            InterruptedException interrupt = new InterruptedException("shutdown interrupt");
            synchronized (lock) {
                for (DelegateFutureTask c : tasks) {
                    Cancelable computation = c.getInnerComputation();
                    computation.doCancel(interrupt);
                }
                tasks.clear();
            }
            return unExecuted;
        }
    }
}
