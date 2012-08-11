package i3.swing.dynamic;

import java.util.Objects;

/**
 * All provided arguments are 'frozen'
 *
 * You can't use null as 'arguments', since they
 * don't allow class information to be gleaned
 */
public final class DynamicRunnable implements Runnable {

    private final MethodInvoker construct;
    private final Object[] arguments;

    private DynamicRunnable(Object target, String method, Object[] arguments, Class[] classes) {
        Objects.requireNonNull(target, "Please don't pass null 'target' field");
        Objects.requireNonNull(method, "Please don't pass null 'method' field");
        this.arguments = arguments;
        construct = new MethodInvoker(target, method, classes);
    }

    public static DynamicRunnable create(Object target, String constructMethod, Object... arguments) {
        return new DynamicRunnable(target, constructMethod, arguments, MethodInvoker.toClasses(arguments));
    }

    public void run() {
        construct.runMethod(arguments);
    }
}
