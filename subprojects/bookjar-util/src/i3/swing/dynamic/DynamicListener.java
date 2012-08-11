package i3.swing.dynamic;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;

/**
 * A PropertyChangeListener action whose propertyChange method
 * is created by reflection of a user provided
 * method.
 *
 * All provided arguments are 'frozen', except on the
 * case where the used factory function has a name substring
 * create<b>Event</b>Action, create<b>Event</b>Listener
 * that denotes that the object will pass the appropriate
 * (and changing) argument to the first argument of the function.
 *
 * You can't use null as 'arguments', since they
 * don't allow class information to be gleaned
 */
public final class DynamicListener implements PropertyChangeListener {

    private final boolean passEvent;
    private final Object[] arguments;
    private final MethodInvoker invoker;

    private DynamicListener(boolean pass, Object target, String method, Object[] args, Class[] classes) {
        Objects.requireNonNull(target, "Please don't pass null 'target' field");
        Objects.requireNonNull(method, "Please don't pass null 'method' field");
        this.arguments = args;
        this.passEvent = pass;
        this.invoker = new MethodInvoker(target, method, classes);
    }

    public static PropertyChangeListener createListener(Object target, String method, Object... arguments) {
        return new DynamicListener(false, target, method, arguments, MethodInvoker.toClasses(arguments));
    }

    public static PropertyChangeListener createEventListener(Object target, String method, Object... arguments) {
        Object[] newArgsWithSpace = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, newArgsWithSpace, 1, arguments.length);
        Class[] classes = MethodInvoker.toClassesAndPrefix(PropertyChangeEvent.class, arguments);
        return new DynamicListener(true, target, method, newArgsWithSpace, classes);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (passEvent) {
            arguments[0] = evt;
        }
        invoker.runMethod(arguments);
    }
}
