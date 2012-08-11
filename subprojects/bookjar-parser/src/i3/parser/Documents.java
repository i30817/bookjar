package i3.parser;

import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;

public final class Documents {

    private Documents(){}

    /**
     * Vetoes any paragraph attribute of an element in a styleddocument
     *
     * @param e
     * the element where we want to veto the attribute, belonging to
     * a styleddocument
     * @param attribute
     * the attribute object to veto, normally one of the
     * StyleConstants attribute objects
     */
    public static void vetoParagraphAttribute(Element e, Object attribute) {
        SimpleAttributeSet set = new SimpleAttributeSet(e.getAttributes());
        set.removeAttribute(attribute);
        StyledDocument doc = (StyledDocument) e.getDocument();
        doc.setParagraphAttributes(e.getStartOffset(), e.getEndOffset() - e.getStartOffset(), set, true);
    }

    public static void vetoSpecificCharacterAttribute(Element e, Object attribute, Object value) {
        boolean contains = e.getAttributes().containsAttribute(attribute, value);
        if (contains) {
            SimpleAttributeSet set = new SimpleAttributeSet(e.getAttributes());
            set.removeAttribute(attribute);
            StyledDocument doc = (StyledDocument) e.getDocument();
            doc.setCharacterAttributes(e.getStartOffset(), e.getEndOffset() - e.getStartOffset(), set, true);
        }
    }
}
