package i3.swing.dynamic;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;

/**
 * Beware: swingworkers can't be used more than once.
 * The dynamic swingworker offers support for
 * using strings to point to methods for both the
 * construct phase of the swingworker and the finished phase.
 * The construct phase will receive the arguments passed in the
 * constructor. The finished phase will receive the value (if any)
 * returned during the construct phase.
 *
 * All provided arguments are hard-referenced inside the swingworker 
 * You can't use null as 'arguments', since they
 * don't allow class information to be gleaned
 * @author i30817
 */
public final class DynamicSwingWorker extends SwingWorker {

    private MethodInvoker construct;
    private Object[] arguments;
    private MethodInvoker finished;

    private DynamicSwingWorker(Object target, String finishedMethod, String constructMethod, Object... arguments) {
        this(target, constructMethod, arguments);
        if (Void.TYPE.equals(construct.methodReturnType())) {
            finished = new MethodInvoker(target, finishedMethod);
        } else {
            finished = new MethodInvoker(target, finishedMethod, construct.methodReturnType());
        }
    }

    private DynamicSwingWorker(Object target, String constructMethod, Object... arguments) {
        this.arguments = arguments;
        Class[] types = MethodInvoker.toClasses(arguments);
        construct = new MethodInvoker(target, constructMethod, types);
    }

    public static SwingWorker create(Object target, String constructMethod, Object... arguments) {
        return new DynamicSwingWorker(target, constructMethod, arguments);
    }

    public static SwingWorker createWithFinished(Object target, String finishedMethod, String constructMethod, Object... arguments) {
        return new DynamicSwingWorker(target, finishedMethod, constructMethod, arguments);
    }

    @Override
    protected Object doInBackground() throws Exception {
        return construct.runMethod(arguments);
    }

    @Override
    public void done() {
        try {
            if (finished != null) {
                finished.runMethod(get());
            }
        } catch (InterruptedException | ExecutionException ex) {
            throw new UndeclaredThrowableException(ex);
        }
    }
}
