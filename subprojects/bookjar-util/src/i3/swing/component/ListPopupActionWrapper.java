package i3.swing.component;

import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * This action wraps another and activates and deactivates if set over a valid
 * JList cell when the menu given becomes visible. It also selects the
 * underlying cell if it is not selected yet
 *
 * Action starts disabled, and activates and deactivates on the popup according
 * to if the mouse is over a list cell and if the wrapped action is enabled
 */
public class ListPopupActionWrapper extends AbstractAction implements PopupMenuListener {

    Action delegate;

    public ListPopupActionWrapper(Action delegate, JPopupMenu menuToListenTo) {
        this.delegate = delegate;
        setEnabled(delegate.isEnabled());
        putValue(NAME, delegate.getValue(NAME));
        putValue(MNEMONIC_KEY, delegate.getValue(MNEMONIC_KEY));
        putValue(DISPLAYED_MNEMONIC_INDEX_KEY, delegate.getValue(DISPLAYED_MNEMONIC_INDEX_KEY));
        putValue(ACCELERATOR_KEY, delegate.getValue(ACCELERATOR_KEY));
        putValue(SHORT_DESCRIPTION, delegate.getValue(SHORT_DESCRIPTION));
        putValue(LONG_DESCRIPTION, delegate.getValue(LONG_DESCRIPTION));
        putValue(LARGE_ICON_KEY, delegate.getValue(LARGE_ICON_KEY));
        putValue(SMALL_ICON, delegate.getValue(SMALL_ICON));
        menuToListenTo.addPopupMenuListener(this);
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent evt) {
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent evt) {
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent evt) {
        //JPopupMenus are full of positioning bugs
        //first getMousePosition doesn't work
        //(parent is null - invoker serves as, but mouse is not on it somehow
        // - invoker.getMousePosition() == null)
        Component component = ((JPopupMenu) evt.getSource()).getInvoker();
        if (component instanceof JList) {
            Point mousePoint = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.convertPointFromScreen(mousePoint, component);
            JList list = (JList) component;
            int index = list.locationToIndex(mousePoint);
            final boolean validIndex = index >= 0 && delegate.isEnabled();
            setEnabled(validIndex);
            if (validIndex && !list.isSelectedIndex(index)) {
                list.setSelectedIndex(index);
            }
        } else {
            setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        delegate.actionPerformed(e);
    }

    @Override
    public void setEnabled(boolean b) {
        delegate.setEnabled(b);
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

}
