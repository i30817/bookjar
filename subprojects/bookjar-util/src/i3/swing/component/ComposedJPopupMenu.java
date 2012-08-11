package i3.swing.component;

import java.awt.Component;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class ComposedJPopupMenu extends JPopupMenu {

    private List<WeakReference<JMenuItem>> cachedForRemoval = Collections.emptyList();
    private WeakReference<JComponent> containsComposed;

    public ComposedJPopupMenu(String label, JComponent containsComposed) {
        super(label);
        this.containsComposed = new WeakReference<>(containsComposed);
    }

    public ComposedJPopupMenu(JComponent containsComposed) {
        this.containsComposed = new WeakReference<>(containsComposed);
    }

    @Override
    protected void firePopupMenuWillBecomeInvisible() {
        super.firePopupMenuWillBecomeInvisible();
        for (WeakReference<JMenuItem> m : cachedForRemoval) {
            JMenuItem i = m.get();
            if (i != null) {
                remove(i);
            }
        }
    }

    @Override
    protected void firePopupMenuWillBecomeVisible() {
        super.firePopupMenuWillBecomeVisible();
        JComponent c = containsComposed.get();
        if (c == null) {
            return;
        }
        JPopupMenu other = c.getComponentPopupMenu();
        if (other == null) {
            return;
        }
        cachedForRemoval = new LinkedList<>();
        for (Component child : other.getComponents()) {
            if (child instanceof JMenuItem) {
                Action a = ((JMenuItem) child).getAction();
                JMenuItem i = new JMenuItem(a);
                cachedForRemoval.add(new WeakReference<>(i));
                add(i);
            }
        }
    }
}
