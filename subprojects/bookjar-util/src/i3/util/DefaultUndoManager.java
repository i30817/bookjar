package i3.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/**
 * Adds Listeners and a disabled/enabled state. When disabled/enabled
 * a undo manager can't/can undo/redo and will fire a property change.
 * @author i30817
 */
public class DefaultUndoManager extends UndoManager {

    private PropertyChangeSupport prop = new PropertyChangeSupport(this);
    private volatile boolean enabled = true;

    public void addPropertyChangeListener(PropertyChangeListener l) {
        prop.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        prop.removePropertyChangeListener(l);
    }

    @Override
    public synchronized boolean canRedo() {
        return enabled && super.canRedo();
    }

    @Override
    public synchronized boolean canUndo() {
        return enabled && super.canUndo();
    }

    public void setEnabled(boolean enabled) {
        boolean oldEnabled = this.enabled;
        this.enabled = enabled;
        if (oldEnabled != enabled) {
            prop.firePropertyChange("enabled", oldEnabled, enabled);
        }
    }

    @Override
    public boolean addEdit(UndoableEdit edit) {
        if (!enabled) {
            return false;
        }

        boolean r = super.addEdit(edit);
        prop.firePropertyChange("addEdit", "1", "2");
        return r;
    }

    @Override
    public boolean replaceEdit(UndoableEdit edit) {
        if (!enabled) {
            return false;
        }

        boolean r = super.replaceEdit(edit);
        prop.firePropertyChange("replaceEdit", "1", "2");
        return r;
    }

    @Override
    public void discardAllEdits() {
        if (!enabled) {
            return;
        }

        super.discardAllEdits();
        prop.firePropertyChange("discardAllEdits", "1", "2");
    }

    @Override
    protected void undoTo(UndoableEdit edit) {
        if (!enabled) {
            return;
        }

        super.undoTo(edit);
        prop.firePropertyChange("undoTo", "1", "2");
    }

    /**
     * Overriden to only undo if possible
     */
    @Override
    public void undo() {
        if (canUndo()) {
            super.undo();
            prop.firePropertyChange("undo", "1", "2");
        }
    }

    /**
     * Overriden to only redo if possible
     */
    @Override
    public void redo() {
        if (canRedo()) {
            super.redo();
            prop.firePropertyChange("redo", "1", "2");
        }
    }

    @Override
    protected void trimForLimit() {
        if (!enabled) {
            return;
        }

        super.trimForLimit();
        prop.firePropertyChange("trimForLimit", "1", "2");
    }

    @Override
    protected void trimEdits(int from, int to) {
        if (!enabled) {
            return;
        }

        super.trimEdits(from, to);
        prop.firePropertyChange("trimEdits", "1", "2");
    }

    @Override
    protected void redoTo(UndoableEdit edit) {
        if (!enabled) {
            return;
        }

        super.redoTo(edit);
        prop.firePropertyChange("redoTo", "1", "2");
    }
}
