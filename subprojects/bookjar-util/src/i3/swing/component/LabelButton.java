package i3.swing.component;

import java.awt.Color;
import java.awt.Component;
import java.awt.SystemColor;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;

/**
 * A button that doesn't paint anything except the text
 * and icons.
 * @author Owner
 */
public class LabelButton extends JButton {

    public LabelButton() {
        this(null, false, false, false);
    }

    public LabelButton(Action action) {
        this(action, false, false, false);
    }

    private LabelButton(Action action, boolean rollOver, boolean border, boolean focus) {
        super(action);
        ForegroundHighlighter h = new ForegroundHighlighter();
        addMouseListener(h);
        addFocusListener(h);
        addHierarchyListener(h);
        setRolloverEnabled(rollOver);
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
        setHorizontalTextPosition(SwingConstants.CENTER);
        setVerticalTextPosition(SwingConstants.CENTER);
        if (!border) {
            setBorderPainted(false);
            setBorder(BorderFactory.createEmptyBorder());
        }
        setFocusPainted(focus);
        setContentAreaFilled(false);
    }

    /**
     * For this class to highlight components correctly, it
     * needs to be added as a mouselistener, focuslistener and hierachylistener.
     * Frankly i'm questioning it's wisdom.
     * @author i30817
     */
    private static class ForegroundHighlighter extends MouseAdapter implements FocusListener, HierarchyListener {

        private Color componentColor = null;
        private boolean inFocus, inMouseOver;

        @Override
        public void mouseEntered(MouseEvent arg) {
            Component comp = arg.getComponent();
            if (componentColor == null && comp.isEnabled()) {
                componentColor = comp.getForeground();
                comp.setForeground(SystemColor.textHighlight);
            }
            inMouseOver = true;
        }

        public void focusGained(FocusEvent e) {
            Component comp = e.getComponent();
            if (componentColor == null && comp.isEnabled()) {
                componentColor = comp.getForeground();
                comp.setForeground(SystemColor.textHighlight);
            }
            inFocus = true;
        }

        @Override
        public void mouseExited(MouseEvent arg) {
            if (!inFocus && componentColor != null) {
                arg.getComponent().setForeground(componentColor);
                componentColor = null;
            }
            inMouseOver = false;
        }

        public void focusLost(FocusEvent e) {
            if (!inMouseOver && componentColor != null) {
                e.getComponent().setForeground(componentColor);
                componentColor = null;
            }
            inFocus = false;
        }

        public void hierarchyChanged(HierarchyEvent e) {
            boolean shouldReset = HierarchyEvent.DISPLAYABILITY_CHANGED == (e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED);
            if (shouldReset && !e.getComponent().isDisplayable()) {
                inFocus = false;
                inMouseOver = false;
                if (componentColor != null) {
                    e.getComponent().setBackground(componentColor);
                }
            }
        }
    }
}
