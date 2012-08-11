package i3.parser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.DefaultStyledDocument.ElementSpec;
import javax.swing.text.Element;

/**
 * A fast buffered builder for creating DefaultStyledDocuments
 * @author i30817
 */
final class BufferedStyledDocumentBuilder {

    private final long MEMORY_CAPACITY_CHARS;
    private int bufferedChars;
    private final LinkedList<ElementSpec> textList = new LinkedList<>();
    private final char[] par = {'\n'};
    private final char[] space = {' '};
    private final DefaultStyledDocument doc;
    private final Append appendFunctor;

    private interface Append {

        void append();
    }

    /**
     * Looses all the benefits of a buffered insertion (almost)
     * but needed to allow document filters to operate correctly
     * (be called)
     */
    private final class LineByLineAppend implements Append {

        @Override
        public void append() {
            for (ElementSpec spec : textList) {
                if (spec.getLength() == 0) {
                    continue;
                }

                String string = new String(spec.getArray(), spec.getOffset(), spec.getLength());
                try {
                    doc.insertString(doc.getLength(), string, spec.getAttributes());
                } catch (BadLocationException ex) {
                    throw new AssertionError(ex);
                }
            }
        }
    }

    private final class ReflectiveAppend implements Append {

        private final Method bulkInsert;

        public ReflectiveAppend() {
            try {
                Class[] args = new Class[]{Integer.TYPE, ElementSpec[].class};
                bulkInsert = DefaultStyledDocument.class.getDeclaredMethod("insert", args);
                //naughty
                bulkInsert.setAccessible(true);
            } catch (NoSuchMethodException | SecurityException ex) {
                throw new AssertionError(ex);
            }
        }

        @Override
        public void append() {
            ElementSpec[] arr = new ElementSpec[textList.size()];
            Object[] bulkInsertArgs = {doc.getLength(), textList.toArray(arr)};
            try {
                bulkInsert.invoke(doc, bulkInsertArgs);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    public BufferedStyledDocumentBuilder(DefaultStyledDocument document) {
        doc = document;
        //need to do special processing if using document filters.
        if (document.getDocumentFilter() == null) {
            appendFunctor = new ReflectiveAppend();
        } else {
            appendFunctor = new LineByLineAppend();
        }
        //convert the free memory from bytes to dbytes (char size) and 4th it.
        MEMORY_CAPACITY_CHARS = (Runtime.getRuntime().freeMemory() / 2L) / 4L;
    }

    public Integer getLength() {
        return Integer.valueOf(doc.getLength() + bufferedChars);
    }

    public DefaultStyledDocument getDocument() {
        return doc;
    }

    public void clear() {
        try {
            doc.remove(0, doc.getLength());
            bufferedChars = 0;
            textList.clear();
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Removes the last inserted \n.
     * No checking so you must know you inserted a \n by appendEnd before.
     */
    public void removeLast() {
//        System.out.println("|REMOVELAST|");
        if (textList.isEmpty()) {
            try {
                doc.remove(doc.getLength() - 1, 1);
            } catch (BadLocationException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            textList.removeLast();
            textList.removeLast();
            textList.removeLast();
            bufferedChars = bufferedChars - 1;
        }
    }

    public void appendSpace(AttributeSet currentAttributes) {
//        System.out.println("|SPACE|");
        append(space, currentAttributes);
    }

    /**
     * You need to call this to signal the end of
     * all paragraphs
     * @param currentAttributes
     */
    public void appendEnd(AttributeSet currentAttributes) {
//        System.out.println("|END|");
        ElementSpec elementSpec = new ElementSpec(currentAttributes.copyAttributes(), ElementSpec.ContentType, par, 0, 1);
        textList.add(elementSpec);
        addParagraphEnd();
        addParagraphStart();
        bufferedChars += 1;
    }

    private void addParagraphStart() {
//        System.out.println("|PARAGRAPHSTART|");
        Element root = doc.getDefaultRootElement();
        ElementSpec elementSpec = new ElementSpec(root.getAttributes(), ElementSpec.StartTagType);
        textList.add(elementSpec);
    }

    private void addParagraphEnd() {
//        System.out.println("|PARAGRAPHEND|");
        Element root = doc.getDefaultRootElement();
        ElementSpec elementSpec = new ElementSpec(root.getAttributes(), ElementSpec.EndTagType);
        textList.add(elementSpec);
    }

    /**
     * Append all the char[] s
     * @param chars (attention: no copy done)
     * @param currentAttributes
     */
    public void append(char[] chars, AttributeSet currentAttributes) {
//        System.out.println("|"+new String(s)+"|");
        textList.add(new ElementSpec(currentAttributes.copyAttributes(), ElementSpec.ContentType, chars, 0, chars.length));
        bufferedChars += chars.length;

        if (bufferedChars > MEMORY_CAPACITY_CHARS) {
            flush();
        }
    }

    /**
     * Append only part of the given char[] s starting at 0
     * @param chars (attention: no copy done)
     * @param len
     * @param currentAttributes
     */
    public void append(char[] chars, int len, AttributeSet currentAttributes) {
//        System.out.println("|"+new String(s, 0, len)+"|");
        textList.add(new ElementSpec(currentAttributes.copyAttributes(), ElementSpec.ContentType, chars, 0, len));
        bufferedChars += len;

        if (bufferedChars > MEMORY_CAPACITY_CHARS) {
            flush();
        }
    }

    /**
     * Append only part of the given char[] s
     * @param chars (attention: no copy done)
     * @param len
     * @param currentAttributes
     */
    public void append(char[] chars, int index, int len, AttributeSet currentAttributes) {
//        System.out.println("|"+new String(s, 0, len)+"|");
        textList.add(new ElementSpec(currentAttributes.copyAttributes(), ElementSpec.ContentType, chars, index, len));
        bufferedChars += len;

        if (bufferedChars > MEMORY_CAPACITY_CHARS) {
            flush();
        }
    }

    private void flush() {
        appendFunctor.append();
        bufferedChars = 0;
        textList.clear();
    }

    /**
     * Call this after adding everything
     * you want to add to this buffer (once only)
     * @throws IllegalStateException if
     * commited more than once.
     */
    public void commit() {
        if (flushed) {
            throw new IllegalStateException("Already flushed once");
        }
        flush();
        flushed = true;
    }
    private boolean flushed = false;
}
