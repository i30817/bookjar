package i3.swing.component;

import i3.io.IoUtils;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.MemoryImageSource;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import javax.swing.Timer;

/**
 * A jframe with full screen functionality that can be serialized the only
 * serializable information is the fullscreen state (no serialization of
 * jcomponents) This may change.
 *
 * @author i30817
 */
public class FullScreenFrame extends javax.swing.JFrame implements Externalizable {

    private static final long serialVersionUID = 1545271155622176167L;
    private transient Rectangle sizeWhileNotFullscreen = new Rectangle();
    private transient boolean isFullscreen = false;
    private transient Cursor currentCursor;
    //This cannot be static since it uses methods of the instance
    private transient Timer timeToHideMouse = new Timer(1500, new HideMouse());
    private static Cursor emptyCursor = createEmptyCursor();
    //workaround for java X11 issue (Toolkit.getScreenInsets) wrong values
    private static final ProcessBuilder panelWorkaround = new ProcessBuilder("xprop", "-root", "-notype", "_NET_WORKAREA");
    private static final boolean isX11;

    static {
        Class c = null;
        try {
            c = Class.forName("sun.awt.X11GraphicsEnvironment");
        } catch (Exception e) {
        }
        isX11 = c != null && c.isInstance(GraphicsEnvironment.getLocalGraphicsEnvironment());
    }

    private Rectangle getAdequateFullSize() throws HeadlessException {
        //bug in linux x11 binding (getScreenInsets not counting panels)...
        if (isX11) {
            try {
                Process proc = panelWorkaround.start();
                String output = IoUtils.toString(proc.getInputStream(), true);
                String[] results = output.split("=");
                if (proc.waitFor() == 0 && results.length == 2) {
                    //GNOME WM seems to dislike not being the one to make sure 
                    //windows don't overlap System panels 
                    //(non-zero origin fake fullscreen gets a grey screen)
                    //set x and y to 0 to make it happy
                    String[] firstWindowProperties = results[1].split(",");
                    int width = Integer.parseInt(firstWindowProperties[2].trim());
                    int height = Integer.parseInt(firstWindowProperties[3].trim());
                    return new Rectangle(0, 0, width, height);
                }
            } catch (IOException | InterruptedException | NumberFormatException ex) {
                ex.printStackTrace();
                return getNormalAdequateSize();
            }
        }
        return new Rectangle(getToolkit().getScreenSize());
    }

    private Rectangle getNormalAdequateSize() throws HeadlessException {
        Insets i = getToolkit().getScreenInsets(getGraphicsConfiguration());
        Rectangle max = new Rectangle(getToolkit().getScreenSize());
        max.width -= (i.left + i.right);
        max.height -= (i.top + i.bottom);
        return max;
    }

    private class HideMouse implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            currentCursor = getCursor();
            setCursor(emptyCursor);
        }
    }
    private transient AWTEventListener scheduleHideMouse = new HideMouseStart();

    private class HideMouseStart implements AWTEventListener {

        @Override
        public void eventDispatched(AWTEvent e) {
            if (isFullscreen) {
                if (getCursor() == emptyCursor) {
                    setCursor(currentCursor);
                }
                timeToHideMouse.restart();
                timeToHideMouse.setRepeats(false);
            }
        }
    }

    private static Cursor createEmptyCursor() {
        //lets hear it for the idiotic setcursor methods! Bravo!
        int[] pixels = new int[0/*16 * 16*/];
        Image image = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(0, 0, pixels, 0, 0));
        return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(0, 0), "InvisibleCursor");
    }

    public FullScreenFrame() {
        this(null);
    }

    public FullScreenFrame(String s) {
        super(s);
        Rectangle max = getNormalAdequateSize();
        setBounds(max);
    }

    public boolean isFullScreen() {
        return isFullscreen;
    }

    /**
     * This method places the JFrame in full screen mode. This means 1) Not
     * resizable. 2) Undecorated. Moreover it also installs a mouse listener
     * that hides the cursor if it is not moved in more than 1.5s and shows it
     * when moved.
     *
     * @param setFull : set the fullscreen
     */
    public void setFullScreen(final boolean setFull) {
        if (setFull == isFullscreen) {
            return;
        }
        final boolean wasVisible = isVisible();
        final Component oldFocusComponent
                = KeyboardFocusManager.getCurrentKeyboardFocusManager().
                getPermanentFocusOwner();

        setVisible(false);
        dispose();

        if (setFull) {
            getToolkit().addAWTEventListener(scheduleHideMouse, AWTEvent.MOUSE_MOTION_EVENT_MASK);
            getBounds(sizeWhileNotFullscreen);
            setBounds(getAdequateFullSize());
        } else {
            getToolkit().removeAWTEventListener(scheduleHideMouse);
            timeToHideMouse.stop();
            setCursor(Cursor.getDefaultCursor());
            setBounds(sizeWhileNotFullscreen);
        }
        setUndecorated(setFull);
        setResizable(!setFull);
        validate();
        setVisible(wasVisible);
        if (oldFocusComponent != null) {
            oldFocusComponent.requestFocus();
        }
        isFullscreen = setFull;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeBoolean(isFullscreen);
        if (isFullscreen) {
            out.writeObject(sizeWhileNotFullscreen);
        } else {
            out.writeObject(getBounds(sizeWhileNotFullscreen));
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        final boolean isFull = in.readBoolean();
        final Rectangle old = (Rectangle) in.readObject();
        if (isFull) {
            setFullScreen(true);
            sizeWhileNotFullscreen = old;
        } else {
            setBounds(old);
        }
    }
}
