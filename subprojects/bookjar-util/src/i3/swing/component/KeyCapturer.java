package i3.swing.component;

import java.awt.Color;
import java.awt.Font;
import java.awt.SystemColor;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 *
 * @author i30817
 */
public class KeyCapturer extends JTextField {

    /**
     * Field used to listen to property changed events refering to changed
     * captured keys.
     **/
    public static final String CAPTURED_KEYSTROKE = "Captured a keyStroke";
    private KeyStroke capturedKeyStroke;
    private boolean capturing = false;

    public KeyCapturer(String text) {
        super(text);
        setup();
    }

    public KeyCapturer() {
        super();
        setup();
    }

    private transient static FocusListener focus = new FocusAdapter() {

        Color c = SystemColor.controlShadow;

        @Override
        public void focusGained(FocusEvent e) {
            if (!e.isTemporary()) {
                KeyCapturer thisOne = (KeyCapturer) e.getComponent();
                thisOne.capturing = true;
                thisOne.setBackground(c);
            }
        }

        @Override
        public void focusLost(FocusEvent e) {
            if (!e.isTemporary()) {
                KeyCapturer thisOne = (KeyCapturer) e.getComponent();
                thisOne.capturing = false;
                thisOne.setBackground(null);
            }
        }
    };
    private transient KeyListener keyRecorder = new KeyAdapter() {

        @Override
        public void keyPressed(KeyEvent arg0) {

            if (capturing) {
                KeyStroke key = KeyStroke.getKeyStrokeForEvent(arg0);
                setKeyStroke(key);
            }
        }
    };

    public void setKeyStroke(KeyStroke k) {

        if (k.getKeyCode() != KeyEvent.VK_UNDEFINED) {
            KeyStroke old = capturedKeyStroke;
            capturedKeyStroke = k;
            setText(toString());
            firePropertyChange(CAPTURED_KEYSTROKE, old, capturedKeyStroke);
        }
    }

    public KeyStroke getKeyStroke() {
        return capturedKeyStroke;
    }

    @Override
    public String toString() {
        int code = capturedKeyStroke.getKeyCode();
        char charCode = capturedKeyStroke.getKeyChar();
        int modifier = capturedKeyStroke.getModifiers();
        String mod = KeyEvent.getKeyModifiersText(modifier);
        String key;

        if (KeyEvent.CHAR_UNDEFINED == charCode) {
            //Default locale seems correct here, since this is for display...
            key = KeyEvent.getKeyText(code).toLowerCase();
        } else {
            key = String.valueOf(charCode).toLowerCase();
        }

        return (mod.equalsIgnoreCase(key)) ? mod : mod + " " + key;
    }

    private void setup() {
        setFont(new Font("Arial Black", Font.PLAIN, getFont().getSize() + 8));
        addKeyListener(keyRecorder);
        addFocusListener(focus);
        setOpaque(true);
        setFocusable(true);
        setEditable(false);
        setHighlighter(null);
    }
}
