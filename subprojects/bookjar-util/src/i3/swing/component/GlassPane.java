package i3.swing.component;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.lang.ref.SoftReference;
import javax.swing.JPanel;

/**
 * Sets text, and blocks all events, and refocuses the last focus
 * owner. Please don't add components to this. IT WONT PAINT THEM!!!
 * If you want something more complete check out the JXGlassPane and
 * related projects.
 * @author  i30817
 */
public class GlassPane extends JPanel implements AWTEventListener {

    private boolean isBlocking = false;
    private String string;
    // Focus will be returned to this component.
    private transient SoftReference<Component> lastFocusOwner;
    // The shared font
    private static Font font = Font.decode("Arial Black-BOLD-16");
    // The shared stroke for painting the text border
    private static Stroke basic = new BasicStroke(2);
    // The shared event consumers
    private static MouseAdapter mouseBlackHole = new MouseAdapter() {
    };
    private static KeyAdapter keyBlackHole = new KeyAdapter() {
    };
    private static MouseWheelListener mouseWheelBlackHole = new MouseWheelListener() {

        public void mouseWheelMoved(MouseWheelEvent evt) {
        }
    };

    public GlassPane() {
        super();
        setOpaque(false);
    }

    public GlassPane(LayoutManager manager) {
        super(manager);
        setOpaque(false);
    }

    public void eventDispatched(AWTEvent anEvent) {
        if (isBlocking && anEvent instanceof KeyEvent && anEvent.getSource() instanceof Component) {
            ((KeyEvent) anEvent).consume();
        }
    }

    public void block() {
        if (!isBlocking) {
            isBlocking = true;

            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
            if (focusOwner != this) {
                lastFocusOwner = new SoftReference<>(focusOwner);
            }


            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.KEY_EVENT_MASK);
            requestFocus();
            addMouseListener(mouseBlackHole);
            addMouseWheelListener(mouseWheelBlackHole);
            addKeyListener(keyBlackHole);
            setFocusTraversalKeysEnabled(false);

            repaint();
        }
    }

    public void unBlock() {
        if (isBlocking) {
            isBlocking = false;

            if (lastFocusOwner != null && lastFocusOwner.get() != null) {
                lastFocusOwner.get().requestFocus();
            }
            setCursor(null);
            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
            setFocusTraversalKeysEnabled(true);
            removeMouseListener(mouseBlackHole);
            removeMouseWheelListener(mouseWheelBlackHole);
            removeKeyListener(keyBlackHole);
            //removeComponentListener(componentBlackHole);

            repaint();
        }
    }

    public void setText(String s) {
        string = s;
        FontRenderContext context = ((Graphics2D) getGraphics()).getFontRenderContext();
        layout = new TextLayout(string, font, context);
        Rectangle2D tmp = layout.getBounds();
        bounds.width = (int) tmp.getWidth();
        bounds.height = (int) tmp.getHeight();
        repaint();
    }
    //text temporaries
    Rectangle bounds = new Rectangle(0, 0, 0, 0);
    TextLayout layout;

    @Override
    public void paintComponent(Graphics g) {
        //super.paintComponent(g);

        if (isBlocking) {
            drawTextAt(string, font, (Graphics2D) g, getWidth(), 10);
        }
    }

    public void drawTextAt(String text, Font font, Graphics2D g2, int width, double y) {
        if (text != null) {
            //calculate text position
            float textX = (float) (width - bounds.getWidth()) / 2;
            float textY = (float) (y + layout.getLeading() + 2 * layout.getAscent());
            int bahW = (int) bounds.getWidth() + 30;
            int bahH = (int) bounds.getHeight() + 30;

            Paint gradient = new GradientPaint(0, 0, SystemColor.text, bahW, bahH, SystemColor.text.darker(), true);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(gradient);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));

            g2.fillRoundRect((int) textX - 15, (int) (textY - bounds.getHeight() - 15), bahW, bahH, bahH, bahH);
            g2.setColor(SystemColor.textText);
            g2.setStroke(basic);


            g2.drawRoundRect((int) textX - 15, (int) (textY - bounds.getHeight() - 15), bahW, bahH, bahH, bahH);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));

            layout.draw(g2, textX, (float) textY - layout.getLeading());
        }
    }
}
