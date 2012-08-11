/**
 *
 */
package i3.ui.controller;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

public class MovingEditorKit extends StyledEditorKit implements ViewFactory {

    /**
     * The observer of the views
     */
    private transient TextObserver observer;
    /**
     * Sets if the text is going to be layed-out forward
     */
    private transient boolean layoutForward = true;
    private static final long serialVersionUID = 8876216256327929774L;

    TextObserver getObserver() {
        return observer;
    }

    @Override
    public void install(JEditorPane pane) {
        super.install(pane);
        observer = new TextObserver(pane);
    }

    @Override
    public void deinstall(JEditorPane pane) {
        super.deinstall(pane);
        observer = null;
    }
    /**
     * To identify the text visual bounds a edited EditorKit (MovingEditorKit)
     * installs a view that counts the visible repaints and sets the bounds.
     *
     * The problem with this approach is that the bounds need to be reset before
     * the new complete (and only complete, not dirty regions) paint.
     * Otherwise, incomplete (small dirty region) or wrong (stale) bounds would
     * be recorded.
     * Fortunatly, it seems that the clip when painting the whole component is
     * trivially checkable for the size of the component. Only reseting the
     * indexes there should give the right results.
     *
     */
    final Rectangle clipCache = new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);

    final class ResetBoxView extends BoxView {

        public ResetBoxView(Element elem, int axis) {
            super(elem, axis);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            //no change is no clip, aka "screen clip"
            g.getClipBounds(clipCache);
            JComponent cp = getObserver().getText();
            if (clipCache.width >= cp.getWidth() && clipCache.getHeight() >= cp.getHeight()) {
                getObserver().reset();
                clipCache.width = Integer.MAX_VALUE;
                clipCache.height = Integer.MAX_VALUE;
            }
            super.paint(g, allocation);
        }
    }

    /**
     * InvertedBoxView for layout out the text backward and give
     * space for the southern component.
     */
    final class InvertedBoxView extends BoxView {

        public InvertedBoxView(Element elem, int axis) {
            super(elem, axis);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            //no change is no clip, aka "screen clip"
            g.getClipBounds(clipCache);
            JComponent cp = getObserver().getText();
            if (clipCache.width >= cp.getWidth() && clipCache.getHeight() >= cp.getHeight()) {
                getObserver().reset();
                clipCache.width = Integer.MAX_VALUE;
                clipCache.height = Integer.MAX_VALUE;
            }
            super.paint(g, allocation);
        }

        @Override
        public void layoutMajorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
            super.layoutMajorAxis(targetSpan, axis, offsets, spans);
            int aux = targetSpan;
            for (int i = getViewCount() - 1; i >= 0; i--) {
                aux = aux - spans[i];
                offsets[i] = aux;
            }
        }
    }

    /**
     * Counting paragraph view
     */
    final class CountingParagraphView extends ParagraphView {

        private final Rectangle line;
        private final Rectangle win;

        public CountingParagraphView(Element elem) {
            super(elem);
            line = new Rectangle();
            win = new Rectangle();
        }

        /**
         * Overrides the normal paint method for eliminating
         * cut lines at the top and bottom of the viewport
         * and for counting the number of visible chars from
         * the model (without added \n from line-wrap,or tabs)
         * the visible chars are stored in an observer of
         * the enclosing class who this method updates.
         * Note that it can't be passed in the constructor without an
         * exception since the super constructor calls paint, before
         * the observer is assigned.
         *
         * @param g
         * @param a
         */
        @Override
        public void paint(Graphics g, Shape a) {
            Rectangle box = (a instanceof Rectangle) ? (Rectangle) a : a.getBounds();

            int n = getViewCount();
            int x = box.x;
            int y = box.y;

            getContainer().getBounds(win);
            /**
             * translate the window to the BoxView coordinate space.
             */
            win.x = getLeftInset();
            win.y = getTopInset();
            win.height -= getBottomInset();
            win.width -= getRightInset();
//                        java.awt.Color c1 = g.getColor();
//                        g.setColor(Color.BLUE);
//                        g.draw3DRect(win.x, win.y, win.width, 1, true);
//                        g.draw3DRect(win.x, win.y + win.height, win.width, 1, true);
//                        g.setColor(c1);
            for (int i = 0; i < n; i++) {
                line.x = x + getOffset(X_AXIS, i);
                line.y = y + getOffset(Y_AXIS, i);
                line.width = getSpan(X_AXIS, i);
                line.height = getSpan(Y_AXIS, i);
                View view = getView(i);

//				java.awt.Color c = g.getColor();
//				g.setColor(java.awt.Color.RED);
//				g.draw3DRect(line.x, line.y, line.width, line.height, true);
//				g.setColor(c);

                if (containsOrEqual(win, line)) {
                    observer.observe(view, line);
                    paintChild(g, line, i);
                }

            }
        }

        /**
         * The stupid rectangle class considers that a rectangle with
         * a zero dimension can't be contained in any other.
         * This is the workaround.
         *
         * @return: true if rectangle a contains or is equal to rectangle b
         */
        private boolean containsOrEqual(Rectangle a, Rectangle b) {

            double bMinX = b.getMinX();
            double bMaxX = b.getMaxX();
            double bMinY = b.getMinY();
            double bMaxY = b.getMaxY();
            double aMinX = a.getMinX();
            double aMaxX = a.getMaxX();
            double aMinY = a.getMinY();
            double aMaxY = a.getMaxY();

            return (bMinX >= aMinX && bMinY >= aMinY && bMinX <= aMaxX && bMinY <= aMaxY && bMaxX >= aMinX && bMaxY >= aMinY && bMaxX <= aMaxX && bMaxY <= aMaxY);
        }
    }

    /**
     * The paint of this class is not being called in java 6 version rc-b70
     * This makes the program work poorly with images
     * (the pane doen't report the text that holds the image attribute
     * so the last visible text after the image, gets repeated. If there is
     * only a image the text doesn't move at all! This could be fixed here,
     * if it was being called)
     * @author Owner
     *
     */
    final class CenteredComponentView extends ComponentView {

        public CenteredComponentView(Element elem) {
            super(elem);
        }

        @Override
        public float getMinimumSpan(int axis) {
            if (axis == View.X_AXIS) {
                return getContainer().getWidth();
            }
            return super.getMinimumSpan(axis);
        }

        @Override
        public float getPreferredSpan(int axis) {
            if (axis == View.X_AXIS) {
                return getContainer().getWidth();
            }
            return super.getPreferredSpan(axis);
        }

        @Override
        public void paint(Graphics g, Shape a) {
            //observer.observe(this, getComponent().getBounds());
            super.paint(g, a);
        }

        public TextObserver getObserver() {
            return observer;
        }
    }

    /**
     * VIEWFACTORY METHODS
     */
    @Override
    public ViewFactory getViewFactory() {
        return this;
    }

    @Override
    public View create(Element elem) {
//        System.out.println("CALLED");
        String name = elem.getName();
        if (name != null) {
            switch (name) {
                case AbstractDocument.ContentElementName:
                    return new LabelView(elem);
                case AbstractDocument.ParagraphElementName:
                    return new CountingParagraphView(elem);
                case AbstractDocument.SectionElementName:
                    if (isLayoutForward()) {
                        return new ResetBoxView(elem, View.Y_AXIS);
                    } else {
                        return new InvertedBoxView(elem, View.Y_AXIS);
                    }
                case StyleConstants.ComponentElementName:
                    return new CenteredComponentView(elem);
                case StyleConstants.IconElementName:
                    return new IconView(elem);
            }
        }
        return new LabelView(elem);

    }

    /**
     * Indicates if we are going to layout the text forward or backward
     *
     * @return : if we are going layout the text forward
     */
    public boolean isLayoutForward() {
        return this.layoutForward;
    }

    /**
     * Sets how the text is going to be layed out
     * @param layoutForward if true the text is layed out from
     * beggining to end, otherwise the opposite
     */
    public void setLayoutForward(boolean layoutForward) {
        this.layoutForward = layoutForward;
    }
}
