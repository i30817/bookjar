package i3.ui.styles;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.UIManager;
import javax.swing.event.SwingPropertyChangeSupport;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Updates document properties. This class receives a Document, but does not
 * prevent its garbage collection.
 *
 * @author i30817
 */
public class DocumentStyle implements Style {

    private static final long serialVersionUID = -6851494937059712434L;
    //weak so that this class does not control the garbage collection of the docoument.
    private final transient WeakReference<StyledDocument> document;
    private final MutableAttributeSet generalStyle = new SimpleAttributeSet();
    private final DenialSet denialSet = new DenialSet();
    private transient SwingPropertyChangeSupport support = new SwingPropertyChangeSupport(this, true);
    private transient boolean characterAttributesModified = true;
    private transient boolean paragraphAttributesModified = true;

    public DocumentStyle(StyledDocument doc) {
        document = new WeakReference<>(doc);
        denialSet.denyCharacterAttributes(deniedCharacterAttributes());
        resetStyles();
        if (doc != null) {
            replaceCharacterStyles(doc, 0, doc.getLength());
        }
    }

    private Object readResolve() {
        denialSet.denyCharacterAttributes(deniedCharacterAttributes());
        support = new SwingPropertyChangeSupport(this, true);
        characterAttributesModified = true;
        paragraphAttributesModified = true;
        return this;
    }

    @Override
    public void change() {
        StyledDocument doc;
        if (document == null || (doc = document.get()) == null) {
            return;
        }

        int end = doc.getLength();

        if (paragraphAttributesModified) {
            processParagraphStyles(doc, 0, end);
        }
        if (characterAttributesModified) {
            processCharacterStyles(doc, 0, end);
        }
        if (characterAttributesModified || paragraphAttributesModified) {
            characterAttributesModified = false;
            paragraphAttributesModified = false;
            support.firePropertyChange(null, null, null);
        }
    }

    private void resetStyles() {
        setForeground(UIManager.getColor("EditorPane.foreground"));
        setShowBold(Boolean.TRUE);
        setShowForeground(Boolean.TRUE);
        setShowFontFamily(Boolean.FALSE);
        setShowItalic(Boolean.TRUE);
        setShowStrikeThrough(Boolean.TRUE);
        setShowUnderline(Boolean.TRUE);
        setAlignment(StyleConstants.ALIGN_LEFT);
        setFontFamily("Arial Black");
        setFontSize(25);
        setFirstLineIndent((float) 10);
        setSpaceBelow((float) 10);
    }

    @Override
    public void copyFrom(Style other) {
        if (other == this || !(other instanceof DocumentStyle)) {
            return;
        }
        DocumentStyle otherStyle = (DocumentStyle) other;
        setAlignment(otherStyle.getAlignment());
        setFirstLineIndent(otherStyle.getFirstLineIndent());
        setSpaceBelow(otherStyle.getSpaceBelow());
        setForeground(otherStyle.getForeground());
        setFontFamily(otherStyle.getFontFamily());
        setFontSize(otherStyle.getFontSize());
        setShowBold(otherStyle.getShowBold());
        setShowForeground(otherStyle.getShowForeground());
        setShowFontFamily(otherStyle.getShowFontFamily());
        setShowItalic(otherStyle.getShowItalic());
        setShowStrikeThrough(otherStyle.getShowStrikeThrough());
        setShowUnderline(otherStyle.getShowUnderline());
        change();
    }

    @Override
    public void addListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    public void setShowBold(Boolean showBold) {
        if (showBold != null && !showBold.equals(getShowBold())) {
            denialSet.showBold(showBold);
            characterAttributesModified = true;
        }
    }

    public Boolean getShowBold() {
        return denialSet.isShowing(StyleConstants.Bold);
    }

    public void setShowFontFamily(Boolean showFontFamily) {
        if (showFontFamily != null && !showFontFamily.equals(getShowFontFamily())) {
            denialSet.showFonts(showFontFamily);
            characterAttributesModified = true;
        }
    }

    public Boolean getShowFontFamily() {
        return denialSet.isShowing(StyleConstants.FontFamily);
    }

    public void setShowForeground(Boolean showFontColor) {
        if (showFontColor != null && !showFontColor.equals(getShowForeground())) {
            denialSet.showForeground(showFontColor);
            characterAttributesModified = true;
        }
    }

    public Boolean getShowForeground() {
        return denialSet.isShowing(StyleConstants.Foreground);
    }

    public void setShowItalic(Boolean showItalic) {
        if (showItalic != null && !showItalic.equals(getShowItalic())) {
            denialSet.showItalic(showItalic);
            characterAttributesModified = true;
        }
    }

    public Boolean getShowItalic() {
        return denialSet.isShowing(StyleConstants.Italic);
    }

    public void setShowStrikeThrough(Boolean showStrikeThrough) {
        if (showStrikeThrough != null && !showStrikeThrough.equals(getShowStrikeThrough())) {
            denialSet.showStrikeThrough(showStrikeThrough);
            characterAttributesModified = true;
        }
    }

    public Boolean getShowStrikeThrough() {
        return denialSet.isShowing(StyleConstants.StrikeThrough);
    }

    public void setShowUnderline(Boolean showUnderline) {
        if (showUnderline != null && !showUnderline.equals(getShowUnderline())) {
            denialSet.showUnderline(showUnderline);
            characterAttributesModified = true;
        }
    }

    public Boolean getShowUnderline() {
        return denialSet.isShowing(StyleConstants.Underline);
    }

    public void setAlignment(Integer alignment) {
        if (alignment != null && !alignment.equals(getAlignment())) {
            StyleConstants.setAlignment(generalStyle, alignment);
            paragraphAttributesModified = true;
        }
    }

    public Integer getAlignment() {
        return StyleConstants.getAlignment(generalStyle);
    }

    public void setFontFamily(String fontFamily) {
        if (fontFamily != null && !fontFamily.equals(getFontFamily())) {
            StyleConstants.setFontFamily(generalStyle, fontFamily);
            paragraphAttributesModified = true;
        }
    }

    public String getFontFamily() {
        return StyleConstants.getFontFamily(generalStyle);
    }

    public void setFontSize(Integer fontSize) {
        if (fontSize != null && !fontSize.equals(getFontSize())) {
            StyleConstants.setFontSize(generalStyle, fontSize);
            paragraphAttributesModified = true;
        }
    }

    public Integer getFontSize() {
        return StyleConstants.getFontSize(generalStyle);
    }

    public void setForeground(Color fontColor) {
        if (fontColor != null && !fontColor.equals(getForeground())) {
            StyleConstants.setForeground(generalStyle, fontColor);
            paragraphAttributesModified = true;
        }
    }

    public Color getForeground() {
        return StyleConstants.getForeground(generalStyle);
    }

    public void setFirstLineIndent(Float firstLineIndent) {
        if (firstLineIndent != null && !firstLineIndent.equals(getFirstLineIndent())) {
            StyleConstants.setFirstLineIndent(generalStyle, firstLineIndent);
            paragraphAttributesModified = true;
        }
    }

    public Float getFirstLineIndent() {
        return StyleConstants.getFirstLineIndent(generalStyle);
    }

    public void setSpaceBelow(Float spaceBelow) {
        if (spaceBelow != null && !spaceBelow.equals(getSpaceBelow())) {
            StyleConstants.setSpaceBelow(generalStyle, spaceBelow);
            paragraphAttributesModified = true;
        }
    }

    public Float getSpaceBelow() {
        return StyleConstants.getSpaceBelow(generalStyle);
    }

    private void processParagraphStyles(StyledDocument doc, int start, int end) {
        Element e;
        int length;
        int index = start;

        while (index <= end) {
            e = doc.getParagraphElement(index);
            length = e.getEndOffset() - e.getStartOffset();
            doc.setParagraphAttributes(index, length, generalStyle, true);
            index += length;
        }
    }

    private void replaceCharacterStyles(StyledDocument doc, int start, int end) {
        Element e;
        int length;
        int index = start;

        if (doc instanceof AbstractDocument) {
            try {
                try {
                    Method lock = AbstractDocument.class.getDeclaredMethod("writeLock");
                    lock.setAccessible(true);
                    lock.invoke(doc);
                    while (index <= end) {
                        e = doc.getCharacterElement(index);
                        denialSet.replaceAttributesInLock((MutableAttributeSet) e.getAttributes(), generalStyle);
                        index += e.getEndOffset() - e.getStartOffset();
                    }
                } finally {
                    Method unlock = AbstractDocument.class.getDeclaredMethod("writeUnlock");
                    unlock.setAccessible(true);
                    unlock.invoke(doc);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
                throw new AssertionError(ex);
            }
        } else {
            while (index <= end) {
                e = doc.getCharacterElement(index);
                length = e.getEndOffset() - e.getStartOffset();
                doc.setCharacterAttributes(index, length, denialSet.replaceAttributes(e.getAttributes(), generalStyle), true);
                index += length;
            }
        }
    }

    private void processCharacterStyles(StyledDocument doc, int start, int end) {
        Element e;
        int length;
        int index = start;

        while (index <= end) {
            e = doc.getCharacterElement(index);
            length = e.getEndOffset() - e.getStartOffset();
            doc.setCharacterAttributes(index, length, denialSet.processAttributes(e.getAttributes()), true);
            index += length;
        }
    }

    /**
     * Function that returns the character attributes that are never shown.
     * Override this method to change them. Default never shown attributes:
     * StyleConstants.Background, StyleConstants.ComposedTextAttribute,
     * StyleConstants.Size
     *
     * @return the denied character attributes
     */
    protected Object[] deniedCharacterAttributes() {
        return new Object[]{StyleConstants.Background, StyleConstants.ComposedTextAttribute, StyleConstants.Size};
    }
}
