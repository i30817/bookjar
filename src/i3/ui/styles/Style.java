package i3.ui.styles;

import java.beans.PropertyChangeListener;
import java.io.Serializable;

/**
 * In general each style will coalesce
 * attribute changes and then effect them
 * at once on the target object(s).
 * Implementations shouldnt allow access
 * to the target object.
 * Styles should be immutable on the target object(s).
 * If you need to make it transient, then
 * create a new Style and use copyFrom(oldStyle) method.
 * @author i30817
 */
public interface Style extends Serializable {

    /**
     * Effect the changes and activates listeners.
     */
    void change();

    /**
     * Copy styles attributes from another Style
     * Dont copy the listeners.
     * Implementations of this and any other
     * bulk copy/set methods should call change()
     * as a courtesy to class clients.
     */
    void copyFrom(Style otherStyle);

    /**
     * Adds a listener (triggered in change()).
     * @param listener
     */
    void addListener(PropertyChangeListener listener);

    /**
     * Removes a listener
     * @param listener
     */
    void removeListener(PropertyChangeListener listener);
}
