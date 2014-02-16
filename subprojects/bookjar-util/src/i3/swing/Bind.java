package i3.swing;

import java.awt.Component;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Manages global keystroke > Actionto mapping only for a top level window.
 * (disabled in parented dialogs or textcomponents)
 * Serializes saved keystrokes.
 *
 * In this class Binding is strongly associated with the action. They are almost
 * indistinguishable (Binding has a default keystroke too) by design, so that
 * when different keystrokes map to the same binding a exception is launched.
 * The correct place to fix this is the source of the data, so the user can retry.
 * Giving null keystrokes removes the input mapping (but not the action mapping)
 * @author Owner
 */
public final class Bind implements Iterable<Entry<Bind.Binding, KeyStroke>>, KeyEventPostProcessor, Serializable {

    public interface Binding {

        Action getAction();

        KeyStroke getDefaultKeyStroke();
    }
    private static final long serialVersionUID = 6560653179388111724L;
    private transient ActionMap actionMap = new ActionMap();
    private InputMap inputMap = new InputMap();

    public Bind() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(this);
    }

    private Object readResolve() {
        actionMap = new ActionMap();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(this);
        return this;
    }

    @Override
    public boolean postProcessKeyEvent(KeyEvent e) {
        Component comp = e.getComponent();

        boolean textualKeyEvent =
                comp instanceof JTextComponent && ((JTextComponent) comp).isEditable() && comp.isEnabled() &&
        //whitelist these since they don't produce text
        //action keys used in textcomponent actions are consumed by those actions
        //so there is no problem with them activating actions when they shouldn't
                !e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isActionKey();


        if (e.isConsumed() || textualKeyEvent) {
            return false;
        }
        KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);
        Object k = inputMap.get(stroke);
        if (k != null) {
            Action a = actionMap.get(k);
            if (a != null && a.isEnabled()) {
                Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                if (active == null || SwingUtilities.getWindowAncestor(active) != null) {
                    return false;
                }
                ActionEvent evt = new ActionEvent(e.getSource(), e.getID(), k.toString(), e.getWhen(), e.getModifiers());
                e.consume();
                a.actionPerformed(evt);
                return true;
            }
        }
        return false;
    }

    //install actions and default keystrokes. Null keystrokes are ignored, duplicate ones raise a exception
    public void setup(Binding[] values) {
        Map<Binding, KeyStroke> invertedInput = invertKeyStrokeMap(values[0]);
        Set<KeyStroke> members = new HashSet<>();//to find duplicate defaultkeystrokes (illegal)
        for (Binding x : values) {
            //install action
            actionMap.put(x, x.getAction());
            //install defaultkeystroke if none (old serialized keys remain)
            boolean noMapping = invertedInput.get(x) == null;
            if (noMapping) {
                KeyStroke defaultKs = x.getDefaultKeyStroke();
                if (members.contains(defaultKs)) {
                    throw new IllegalArgumentException("The given entries have at least 2 entries with equal keystrokes");
                }
                if (defaultKs != null) {
                    members.add(x.getDefaultKeyStroke());
                    inputMap.put(x.getDefaultKeyStroke(), x);
                }
            }
        }
    }

    /**
     * @return the current enum, keystroke mapping
     */
    @Override
    public Iterator<Entry<Binding, KeyStroke>> iterator() {
        return new KeyStrokeIterator();
    }

    //install keystrokes. Null keystrokes are ignored, duplicate ones raise a exception
    public void install(Iterable<Entry<Binding, KeyStroke>> iterable) {
        Set<KeyStroke> keys = new HashSet<>();
        Map<Binding, KeyStroke> invertedInput = null;

        for (Entry<Binding, KeyStroke> e : iterable) {
            KeyStroke keystroke = e.getValue();
            if (keys.contains(keystroke)) {
                throw new IllegalArgumentException("The given entries have at least 2 entries with equal keystrokes");
            }
            if (invertedInput == null) {
                invertedInput = invertKeyStrokeMap(e.getKey());
            }
            if (keystroke != null) {
                keys.add(e.getValue());
                //kill old mapping
                inputMap.remove(invertedInput.get(e.getKey()));
                inputMap.put(e.getValue(), e.getKey());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Binding, KeyStroke> invertKeyStrokeMap(Binding value) {
        //Membership test
        Map<Binding, KeyStroke> invertedInput = value instanceof Enum
                ? new EnumMap(value.getClass())
                : new HashMap<>();
        //for some bizarre reason inputmap.keys can return null instead of a empty array
        KeyStroke[] old = inputMap.keys();
        if (old != null) {
            for (KeyStroke k : old) {
                Binding e = (Binding) inputMap.get(k);
                assert e != null;
                invertedInput.put(e, k);
            }
        }
        return invertedInput;
    }

    private class KeyStrokeIterator implements Iterator<Entry<Binding, KeyStroke>> {

        public KeyStrokeIterator() {
            keys = inputMap.keys();
            if (keys == null) {
                keys = new KeyStroke[0];
            }
        }
        KeyStroke[] keys;
        int offset;

        @Override
        public boolean hasNext() {
            return offset < keys.length;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Entry<Binding, KeyStroke> next() {
            return new SimpleEntry<>((Binding) inputMap.get(keys[offset]), keys[offset++]);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
