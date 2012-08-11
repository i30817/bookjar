package i3.swing.dynamic;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.Action;

/**
 * A action whose actionPerformed method
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
 * @author Owner
 */
public final class DynamicAction implements Action {

    private final Action delegate = new DummyAction();
    private final Object[] arguments;
    private final MethodInvoker invoker;
    private final boolean passEvent;

    private DynamicAction(boolean passActionEvent, String name, String longDescription, Character mnemonic, Object target, String method, Object [] arguments, Class[] classes) {
        Objects.requireNonNull(target, "Please don't pass null 'target' field");
        Objects.requireNonNull(method, "Please don't pass null 'method' field");
        this.arguments = arguments;
        this.passEvent = passActionEvent;
        invoker = new MethodInvoker(target, method, classes);
        delegate.putValue(Action.NAME, name);
        delegate.putValue(Action.MNEMONIC_KEY, mnemonic == null ? null : (int) mnemonic);
        delegate.putValue(Action.LONG_DESCRIPTION, longDescription);

    }

    public static Action createAction(Character mnemonic, String name, String longDescription, Object target, String method, Object... arguments) {
        return new DynamicAction(false, name, longDescription, mnemonic, target, method, arguments, MethodInvoker.toClasses(arguments));
    }

    public static Action createAction(String name, String longDescription, Object target, String method, Object... arguments) {
        return new DynamicAction(false, name, longDescription, null, target, method, arguments, MethodInvoker.toClasses(arguments));
    }

    public static Action createAction(String name, Object target, String method, Object... arguments) {
        return new DynamicAction(false, name, null, null, target, method, arguments, MethodInvoker.toClasses(arguments));
    }

    public static Action createAction(Object target, String method, Object... arguments) {
        return new DynamicAction(false, null, null, null, target, method, arguments, MethodInvoker.toClasses(arguments));
    }

    /**
     * Creates an action that calls target.method(ActionEvent e, Arguments...args),
     * (if args is empty, no arguments except the event)
     * passing the originating actionevent
     */
    public static Action createEventAction(Object target, String method, Object... arguments) {
        Object [] newArgsWithSpace = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, newArgsWithSpace, 1, arguments.length);
        Class [] classes = MethodInvoker.toClassesAndPrefix(ActionEvent.class, arguments);
        return new DynamicAction(true, null, null, null, target, method, newArgsWithSpace, classes);
    }

    public static Action createEventAction(String name, String longDescription, Object target, String method, Object... arguments) {
        Object [] newArgsWithSpace = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, newArgsWithSpace, 1, arguments.length);
        Class [] classes = MethodInvoker.toClassesAndPrefix(ActionEvent.class, arguments);
        return new DynamicAction(true, name, longDescription, null, target, method, newArgsWithSpace, classes);
    }

    public void actionPerformed(ActionEvent e) {
        if (passEvent) {
            arguments[0] = e;
        }
        invoker.runMethod(arguments);
    }

    public void setEnabled(boolean b) {
        delegate.setEnabled(b);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        delegate.removePropertyChangeListener(listener);
    }

    public void putValue(String key, Object value) {
        delegate.putValue(key, value);
    }

    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    public Object getValue(String key) {
        return delegate.getValue(key);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        delegate.addPropertyChangeListener(listener);
    }

    private static final class DummyAction extends AbstractAction {

        public void actionPerformed(ActionEvent e) {
        }
    }
}
