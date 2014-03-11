package i3.swing;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.logging.log4j.LogManager;

/**
 * A delegate for normal listeners that removes them (and their references)
 * automatically when the Listener is no longer registered in any component.
 * Obviously, of course, the constructor PropertyChangeListener must be held by
 * a hard reference outside - preferably in the the object that is registering
 * the reference into the source of events. Then when that object goes away, the
 * hard reference also goes away, and any registration of it also goes away.
 *
 * @author i30817
 */
public class WeakChangeListener implements ChangeListener {

    WeakReference<ChangeListener> listenerRef;

    public WeakChangeListener(ChangeListener listener) {
        listenerRef = new WeakReference<>(listener);
    }

    public void stateChanged(ChangeEvent e) {
        ChangeListener listener = listenerRef.get();
        if (listener == null) {
            removeListener(e.getSource());
        } else {
            listener.stateChanged(e);
        }
    }

    private void removeListener(Object src) {
        try {
            LogManager.getLogger().info("removing unused listener " + this);
            Method method = src.getClass().getMethod("removeChangeListener", new Class[]{ChangeListener.class});
            method.invoke(src, new Object[]{this});
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            LogManager.getLogger().warn("cannot remove listener: " + e);
        }
    }
}
