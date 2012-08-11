package i3.parser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.GapContent;
import javax.swing.text.StyleContext;
import javax.swing.undo.UndoableEdit;

/**
 * A document implementation that doesn't duplicate memory in buffered insertion
 * @author Owner
 */
public final class ParserDocument extends DefaultStyledDocument {

    private final Method replace;
    private final Object[] bulkArgs = new Object[4];

    public ParserDocument() {
        super(new SanerStyleContext());
        try {
            Class[] args = new Class[]{Integer.TYPE, Integer.TYPE, Object.class, Integer.TYPE};
            replace = GapContent.class.getSuperclass().getDeclaredMethod("replace", args);
            replace.setAccessible(true);
        } catch (NoSuchMethodException | SecurityException ex) {
            throw new AssertionError(ex);
        }
    }

    @Override
    public void insert(int offset, ElementSpec[] data) throws BadLocationException {
        if (offset < 0 || offset > getLength()) {
            throw new BadLocationException("Invalid insert", offset);
        }
        if (data == null || data.length == 0) {
            return;
        }

        writeLock();
        try {
            Content content = getContent();
            //since instead of doing normal string insert we are going to use the gapvector directly, we don't do this
            //UndoableEdit cEdit = content.insertString(offset, sb.toString());

            int charArraysSize = 0;
            int index = offset;
            for (ElementSpec e : data) {
                if (e.getLength() > 0) {
                    charArraysSize += e.getLength();
                    bulkArgs[0] = index;
                    bulkArgs[1] = e.getOffset();
                    bulkArgs[2] = e.getArray();
                    bulkArgs[3] = e.getLength();
                    index += e.getLength();
                    try {
                        replace.invoke(content, bulkArgs);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                        throw new AssertionError(ex);
                    }
                }
            }
            if (charArraysSize == 0) {
                return;
            }
            //created the undoable edit corresponding to the whole inserted string
            //this can't be created since it is package default in GapContent
            //UndoableEdit cEdit = new InsertUndo(offset, charArraysSize);
            DefaultDocumentEvent evnt = new DefaultDocumentEvent(offset, charArraysSize, DocumentEvent.EventType.INSERT);
            //evnt.addEdit(cEdit);
            buffer.insert(offset, charArraysSize, data, evnt);
            //this can't be used because the 'super' here is DefaultStyledDocument, not AbstractDocument
            //like the superclass method wants.
            //super.insertUpdate(evnt, null);
            // notify the listeners
            evnt.end();
            fireInsertUpdate(evnt);
            fireUndoableEditUpdate(new UndoableEditEvent(this, evnt));
        } finally {
            writeUnlock();
        }
    }

    // Use a shared styleContext for all the documents in all instances
    private static final class SanerStyleContext extends StyleContext {

        @Override
        protected int getCompressionThreshold() {
            //always compress
            return 100;
        }

        @Override
        protected SmallAttributeSet createSmallAttributeSet(AttributeSet attributeSet) {
            //Redefinied for speed.
            return new SmallAttributeSet(attributeSet) {

                @Override
                @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
                public final boolean equals(Object obj) {
                    return isEqual((AttributeSet) obj);
                }

                @Override
                public boolean isEqual(AttributeSet attr) {
                    if (attr == this) {
                        return true;
                    } else if (attr == null) {
                        return false;
                    } else {
                        int c = getAttributeCount();
                        return c == attr.getAttributeCount() && (c == 0 || containsAttributes(attr));
                    }
                }

                @Override
                public boolean containsAttributes(AttributeSet attrs) {
                    if (attrs.getAttributeCount() > getAttributeCount()) {
                        return false;
                    }
                    return super.containsAttributes(attrs);
                }
            };
        }
    }
}
