package i3.ui.controller;

import java.awt.Color;
import java.awt.PaintContext;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import static javax.swing.SwingUtilities.*;
import javax.swing.UIManager;
import javax.swing.event.SwingPropertyChangeSupport;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.GapContent;
import javax.swing.text.Highlighter;
import javax.swing.text.Position.Bias;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import static javax.swing.text.html.HTML.Attribute.*;
import i3.parser.BookLoader;
import i3.parser.Documents;
import i3.parser.Property;
import i3.ui.styles.DocumentStyle;
import i3.io.IoUtils;
import i3.swing.SearchIterator;
import i3.swing.SwingUtils;

public class MovingPane implements Serializable {

    private static final long serialVersionUID = -7228251535750304121L;
    /**
     * A key for a propertyChangeEvent fired when the current open file is
     * removed from the pane and a new one is added.
     *
     * Has the oldvalue the old URL and the newvalue the new URL.
     */
    public static final String DOCUMENT_CHANGED = "finishedReadingNewInput";
    /**
     * A key for a propertyChangeEvent fired when a inside hyperlink is clicked.
     * Has in the oldValue, the old index of the link
     * and in the newValue the link index.
     */
    public static final String MOUSE_CLICK_HYPERLINK = "linked";
    /**
     * A key for a propertyChangeEvent fired once when the mouse
     * is over a file hyperlink.
     * Has in the oldValue null
     * and in the newValue null
     */
    public static final String MOUSE_ENTER_HYPERLINK = "linked3";
    /**
     * A key for a propertyChangeEvent fired once when the mouse
     * leaves a file hyperlink.
     * Has in the oldValue null
     * and in the newValue null
     */
    public static final String MOUSE_EXIT_HYPERLINK = "linked4";
    /**
     * The index in the buffer
     */
    private transient int index;
    /**
     * The buffer
     */
    private transient StyledDocument buffer = null;
    /**
     * The styles that are going to delegate the changing of a part of the view
     * Useful for removing all the italic text at once for example. Serializable.
     */
    private transient DocumentStyle documentStyle;
    /**
     * Mutable color corresponding to a hyperlink color
     */
    private transient WrappedColor linkColor = new WrappedColor(Color.orange);
    /**
     * Indicates there was a up movement before, needed because the up movement
     * can turn the index inconsistent if the component is resized afterwards.
     * Used in getIndex() and reset in cleanIndex(), set in
     * setDocument(StyledDocument d, boolean forwardLayout)
     */
    private transient boolean isDirty;
    /**
     * The path of the current stream of the document being parse.
     */
    private transient URL currentUrl = null;
    /**
     * Need to be restored.
     */
    /**
     * The composed view
     */
    private transient JEditorPane pane;
    /**
     * A parser that returns documents given files
     */
    private transient SwingPropertyChangeSupport support;
    private transient ListenToDocument listenToDocument;

    public MovingPane() {
        init();
    }

    private void readObject(java.io.ObjectInputStream input) throws IOException, ClassNotFoundException {
        init();
        pane.setBackground((Color) input.readObject());
        pane.setSelectionColor((Color) input.readObject());
        linkColor = (WrappedColor) input.readObject();
        documentStyle = (DocumentStyle) input.readObject();
    }

    private void writeObject(java.io.ObjectOutputStream output) throws IOException {
        output.writeObject(pane.getBackground());
        output.writeObject(pane.getSelectionColor());
        output.writeObject(linkColor);
        output.writeObject(documentStyle);
    }

    private void init() {
        pane = new JEditorPane();
        painter = new LineHighlightPainter();
        pane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        support = new SwingPropertyChangeSupport(this, true);
        listenToDocument = new ListenToDocument();
        LinkController controller = new LinkController();
        pane.addMouseListener(controller);
        pane.addMouseMotionListener(controller);
        pane.setEditorKit(new MovingEditorKit());
        pane.setEditable(false);
        pane.setFocusable(false);
        //some strange bug in GTK Synth ui overrides the  default color
        //from ui manager on update ui in constructor to something really stupid
        Color c = UIManager.getColor("EditorPane.background");
        if (c != null) {
            pane.setBackground(c);
        }
        c = UIManager.getColor("EditorPane.selectionBackground");
        if (c != null) {
            pane.setSelectionColor(c);
        }
    }

    private int findStartOfWordLTR(int requestedIndex, int length) throws BadLocationException {
        int getLen = Math.min(buffer.getLength() - requestedIndex, length);
        String s = buffer.getText(requestedIndex, getLen);
        int i;
        for (i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                break;
            }
        }

        return requestedIndex + i;
    }

    private int findStartOfWordRTL(int requestedIndex, int l) throws BadLocationException {
        int cons = requestedIndex - l;
        int tmp = Math.max(0, cons);
        String s = buffer.getText(tmp, cons < 0 ? requestedIndex : l);
        int stride = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(s.charAt(i))) {
                break;
            } else {
                stride++;
            }
        }
        return requestedIndex - stride;
    }

    private int findStartOfPreviousWordRTL(int requestedIndex, int l) throws BadLocationException {
        int cons = requestedIndex - l;
        int tmp = Math.max(0, cons);
        String s = buffer.getText(tmp, cons < 0 ? requestedIndex : l);
        int stride = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            stride++;
            if (!Character.isWhitespace(s.charAt(i))) {
                break;
            }
        }
        return findStartOfWordRTL(requestedIndex - stride, l);
    }

    public void setLinkColor(Color color) {
        if (color == null || color == linkColor || color.equals(linkColor)) {
            return;
        }
        linkColor.setWrapped(color);
        getView().repaint();
    }

    public Color getLinkColor() {
        return linkColor;
    }

    private int setWordIndexPrivate(int requestedIndex, Bias bias) {
        if (buffer.getLength() == 0) {
            return 0;
        }

        /**
        word beginning algorithm:
        Assumes left-to-right language. | is index, ! is candidate location

        for forward bias:
        If ! starts in a word char will always moved left towards the start of the word.
        If ! is in a space char it will moved right towards the start of the next word.
        ending states:
        put | at !

        for backward bias:
        If ! starts in a word char will always moved left towards the start of the word.
        If ! is in a space char it will moved left towards the start of the previous word.
        ending states:
        put | at !
         */
        int oldIndex = getIndex();
        int newIndex;
        try {
            String requested = buffer.getText(requestedIndex, Math.min(buffer.getLength() - requestedIndex, 1));
            boolean isWhiteSpace = !requested.isEmpty() && Character.isWhitespace(requested.charAt(0));
            final int MAXWORDSIZE = 100;

            if (isWhiteSpace) {
                if (bias == Bias.Forward) {
                    newIndex = findStartOfWordLTR(requestedIndex, MAXWORDSIZE);
                } else {
                    newIndex = findStartOfPreviousWordRTL(requestedIndex, MAXWORDSIZE);
                }
//                System.out.println("1>>>" + buffer.getText(newIndex, 10) + "<<<");
            } else {
                newIndex = findStartOfWordRTL(requestedIndex, MAXWORDSIZE);
//                System.out.println("2>>>" + buffer.getText(newIndex, 10) + "<<< " + requestedIndex);
            }
            if (oldIndex == newIndex) {
                return newIndex;
            }
            index = newIndex;
            bufferToViewLayoutForward();
            return newIndex;
        } catch (BadLocationException ex) {
            throw new AssertionError("The document view is out of bounds.", ex);
        }
    }

    public AttributeSet getCharacterAttributesAt(Point point) {
        int pos = pane.viewToModel(point);
        if (pos < 0) {
            return SimpleAttributeSet.EMPTY;
        }
        StyledDocument doc = (StyledDocument) pane.getDocument();
        return doc.getCharacterElement(pos).getAttributes();
    }

    /**
     * Moving text methods
     */
    /**
     * These auxiliar methods take the text and styles from the document buffer
     * and apply them to the container
     *
     *
     * @param doc
     *
     *            The document to apply the text
     * @param start
     *            The index where to end the text taken from the document buffer
     * @param length
     *            The lenght of styled text to copy from the document buffer to
     *            the doc
     */
    private void backwardConstructDocument(StyledDocument doc, int start, int length) {
        int var = Math.max(start - length, 0);
        int trueLength = start - var;
        forwardConstructDocument(doc, var, trueLength);
        /*Remove any space bellow. It looks bad in the middle of a paragraph*/
        /*This provokes a positioning bug don't do it*/
        //docFactory.vetoParagraphAttribute(doc.getParagraphElement(doc.getLength()), StyleConstants.SpaceBelow);
    }

    /**
     * This method moves the text backward using a paging stategy
     *
     */
    public void moveBackward() {
        /**
         * We do not move if there is nothing to move
         * or the clean index is zero
         */
        ObservedResult result = ((MovingEditorKit) pane.getEditorKit()).getObserver().getResult();
        //&& getIndex(result) != buffer.getLength() bug fix for setting the pane to the last  character
        //couldn't move back since the interval would always be zero)
        if (buffer == null || (result.getInterval() == 0 && getIndex(result) != buffer.getLength()) || getIndex(result) == 0) {
            return;
        }

        /**
         * If the component is dirty, we need to update the index before we move. MoveBackward dirties the index.
         */
        cleanIndex(result);
        StyledDocument doc = new DefaultStyledDocument(new GapContent(getDisplayableText() + 16), StyleContext.getDefaultStyleContext());
        backwardConstructDocument(doc, index, getDisplayableText());
        setDocument(doc, false);
    }

    /**
     * See if the index and the buffer permit the text to go backwards
     *
     * @return : boolean the text can go backward
     */
    public boolean canMoveBackward() {
        return buffer != null && getIndex() > 0;
    }

    /**
     * See if the index and the buffer permit the text to go forward
     *
     * @return : boolean the text can go forward
     */
    public boolean canMoveForward() {
        return buffer != null && getIndex() < buffer.getLength();
    }

    /**
     * Cleans the index when called. Only call this when you know you are going
     * to move the index afterwards
     * @param result the index is cleaned using the reported interval given
     * by this ObservedResult
     */
    private void cleanIndex(ObservedResult result) {
        index = getIndex(result);
        isDirty = false;
    }

    /**
     * These auxiliar methods take the text and styles from the document buffer
     * and apply them to the container
     *
     *
     * @param doc
     *            The document to apply the text
     * @param start
     *            The index where to start to get the text in the document
     *            buffer
     * @param length
     *            The length of styled text to copy from the document
     *            buffer to the doc (truncated if not possible)
     */
    private void forwardConstructDocument(StyledDocument doc, final int start, final int length) {

        Element e;
        final int bufferStart = Math.max(start, 0);
        final int bufferEnd = Math.min(start + length, buffer.getLength());
        int var = bufferStart;
        int len;

        try {
            while (var < bufferEnd) {
                e = buffer.getCharacterElement(var);
                len = (e.getEndOffset() >= bufferEnd) ? bufferEnd - var : e.getEndOffset() - var;
                doc.insertString(doc.getLength(), buffer.getText(var, len), e.getAttributes());
                var = e.getEndOffset();
            }

            /*Now copy the paragraph attributes ... being careful at the edges*/
            var = bufferStart;
            int counter = 0;
            while (var <= bufferEnd) {
                e = buffer.getParagraphElement(var);
                len = e.getEndOffset() - var;
                doc.setParagraphAttributes(counter, len, e.getAttributes(), true);
                var = e.getEndOffset();
                counter += len;
            }

            /*if the paragraph started before our copy erase the indent*/
            if (buffer.getParagraphElement(bufferStart).getStartOffset() < bufferStart) {
                Documents.vetoParagraphAttribute(doc.getParagraphElement(0), StyleConstants.FirstLineIndent);
            }
        } catch (BadLocationException ble) {
            throw new AssertionError("The document view is out of bounds.", ble);
        }
    }

    /**
     * This method moves the text forward using a paging stategy
     *
     */
    public void moveForward() {

        /**
         * We do not move if there is nothing to move
         * or the clean index + interval equals the end of the document.
         * All abstract documents have an addicional character applied
         * at the end but our observer takes care of that.
         */
        ObservedResult result = ((MovingEditorKit) pane.getEditorKit()).getObserver().getResult();

        if (buffer == null || result.getInterval() == 0 || (getIndex(result) + result.getInterval()) == buffer.getLength()) {
            return;
        }
        /**
         * If the component is dirty, we need to update the index before we move
         */
        cleanIndex(result);
        /* add the observed interval to start at the next block*/
        index += result.getInterval();
        bufferToViewLayoutForward();
    }

    /**
     * Before and during the DOCUMENT_CHANGING event the URL stays the same
     * until the DOCUMENT_CHANGED event where the URL is changed.
     * @return the URL of the current parse stream
     */
    public URL getURL() {
        return currentUrl;
    }

    private void installDocument(final StyledDocument doc, final int i, final URL newURL) {
        if (doc == null) {
            return;
        }
        Runnable install = new Runnable() {

            @Override
            public void run() {
                buffer = doc;
                index = Math.max(0, Math.min(i, buffer.getLength()));
                bufferToViewLayoutForward();
                changeURL(newURL);
            }
        };

        SwingUtils.runInEDTAndWait(install);
    }

    private void changeURL(URL newURL) {
        URL oldURL = currentUrl;
        currentUrl = newURL;
        support.firePropertyChange(DOCUMENT_CHANGED, oldURL, currentUrl);
    }

    private StyledDocument doStylesChange(StyledDocument doc) {
        DocumentStyle other = new DocumentStyle(doc);
        other.copyFrom(getDocumentStyle());
        other.addListener(listenToDocument);
        documentStyle = other;
        return doc;
    }

    /**
     * Parses a file and sets it in the view.
     *
     * The document configured according to the properties.
     * Sends a PropertyChangeEvent that signals that the the current
     * document finished changing from old to new, if it changed.
     *
     * @param url
     *            the URL to be parsed (html, htm or rtf, or parse as text otherwise)
     *            if null pr unreachable the method does nothing.
     * @param index
     *            the position where to start displaying the document.
     * @param props for the parsers
     */
    public void read(URL url, int index, Map<Property, Object> props) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("Null URL");
        }
        if (!IoUtils.canRead(url)) {
            throw new IOException("Can't read the given URL " + url.toExternalForm());
        }
        if (currentUrl != null && url.getPath().equals(currentUrl.getPath())) {
            return;
        }
        props.put(Property.HYPERLINK_COLOR, linkColor);
        //this removes the possibility to undo on failure...
        //but allows larger files without oom exceptions.
        //It's why i'm testing existence with IoUtils.canRead
        buffer = null;
        BookLoader loader = BookLoader.forFileName(IoUtils.getName(url));
        StyledDocument doc = doStylesChange(loader.create(url, props));
        installDocument(doc, index, url);
    }

    /**
     * Parses the given reader in format format,
     * and sets it in the view.
     * The reader will be configurated according to the properties
     *
     * @param stream the Reader to be parsed
     * @param format the format of the reader
     * @param props of the parser
     */
    public void read(final InputStream stream, final String format, int index, Map<Property, Object> props) throws IOException {
        buffer = null;
        props.put(Property.HYPERLINK_COLOR, linkColor);
        BookLoader loader = BookLoader.forFileName(format);
        StyledDocument doc = doStylesChange(loader.create(stream, props));
        installDocument(doc, index, null);
    }

    /**
     * Parses the given string in format format,
     * and sets it in the view.
     *
     * The reader will be configurated according to the properties
     *
     * @param string the string to be parsed
     * @param mimeType the mimeType of the string
     * @param props of the parser
     */
    public void read(String string, String mimeType, int index, Map<Property, Object> props) {
        buffer = null;
        props.put(Property.HYPERLINK_COLOR, linkColor);
        BookLoader loader = BookLoader.forMimeType(mimeType);
        StyledDocument doc = doStylesChange(loader.create(string, props));
        installDocument(doc, index, null);
    }

    private void bufferToViewLayoutForward() {
        DefaultStyledDocument doc = new DefaultStyledDocument(new GapContent(getDisplayableText() + 16), StyleContext.getDefaultStyleContext());
        forwardConstructDocument(doc, index, getDisplayableText());
        setDocument(doc, true);
    }

    /**
     * Sets the document with a order of layout
     * @param doc the document to display
     * @param layoutForward layout the views forward
     */
    private void setDocument(StyledDocument doc, boolean layoutForward) {
        ((MovingEditorKit) pane.getEditorKit()).setLayoutForward(layoutForward);
        isDirty = !layoutForward;
        ((AbstractDocument) doc).setDocumentProperties(((AbstractDocument) buffer).getDocumentProperties());
        pane.setDocument(doc);
    }

    /**
     * Sets the position of the buffer.
     * If the buffer is null pr the same
     * as the current one, does nothing.
     * @param i
     *            The position that the text should start to display, truncated
     *            to a legal value if < 0 or > buffer.getLength()
     */
    public void setIndex(int i) {
        if (buffer != null) {
            i = Math.max(0, Math.min(i, buffer.getLength()));
            if ((index != i || isDirty)) {
                index = i;
                bufferToViewLayoutForward();
            }
        }
    }

    /**
     * Sets a position of the buffer, but moves
     * the position so it displays the whole word
     * at the position set.
     * @param i the position truncated to a legal value
     * @param bias the bias to choose which word to set on.
     * Bias.Forward - next word, unless current word is truncated,
     * where it displays the current one
     * Bias.Backward - previous word (if truncated, the current word)
     * @return the actual position set, -1 if buffer is null
     * if i == index, then no actual moving is done
     */
    public int setWordIndex(int i, Bias bias) {
        if (buffer != null) {
            i = Math.max(0, Math.min(i, buffer.getLength()));
            return setWordIndexPrivate(i, bias);
        }
        return -1;
    }

    /**
     * Used to return a consistent index, at the exact moment the
     * given result was calculated. Only used internally for efficiency.
     *
     * @param result the current result
     * @return the calculated index in the buffer
     */
    private int getIndex(ObservedResult result) {
        if (isDirty) {
            assert (index - result.getInterval() >= 0) : "Negative interval offset " + (index - result.getInterval());
            return index - result.getInterval();
        }
        return index;
    }

    /**
     * Used to return a consistent index, at this exact moment.
     *
     * @return the calculated index in the buffer
     */
    public int getIndex() {
        if (isDirty) {
            ObservedResult result = ((MovingEditorKit) pane.getEditorKit()).getObserver().getResult();
            assert (index - result.getInterval() >= 0);
            return index - result.getInterval();
        }
        return index;
    }

    /**
     * Used to return a consistent index, at this exact moment,
     * for the next invisible char (after the visible text), if any.
     *
     * @return the getIndex() for the pane after a user
     * calls moveForward().
     */
    public int getLastVisibleIndex() {
        if (isDirty) {
            return index;
        } else {
            ObservedResult result = ((MovingEditorKit) pane.getEditorKit()).getObserver().getResult();
            return index + result.getInterval();
        }
    }

    /**
     * @return A estimative of an length of text that is
     * more than the legth of text displayable in the maximum
     * size of the movingPane
     */
    private int getDisplayableText() {
        return Math.min(buffer.getLength(), estimateDisplayableText());
    }

    /**
     * This method set the minimum number of text copied from the buffer to the
     * component document. It should use a fill algorithm to guess a lower bound
     * number of chars that the container can support in the current graphics
     * context
     */
    protected int estimateDisplayableText() {
        return 8000;
    }

    /**
     * Gets where we are on the text in a percentage
     * @return a float bettween 0 and 1 that is the percentage
     * from the start of the text.
     * If not possible to to get the percentage, return NaN
     */
    public float getPercentage() {

        if (buffer != null && buffer.getLength() != 0) {
            return (float) getIndex() / buffer.getLength();
        }
        return Float.NaN;
    }

    /**
     * Gets where we are on the text in a percentage
     * @return a float bettween 0 and 1 that is the percentage
     * from the start of the text to the end of the visible text.
     * If not possible to to get the percentage, return NaN
     */
    public float getLastVisiblePercentage() {

        if (buffer != null && buffer.getLength() != 0) {
            ObservedResult result = ((MovingEditorKit) pane.getEditorKit()).getObserver().getResult();
            int chars = getIndex(result) + result.getInterval();
            if (chars == buffer.getLength()) {
                return 1.0f;
            }
            return (float) chars / buffer.getLength();
        }
        return Float.NaN;
    }

    public int getLength() {
        if (buffer != null) {
            return buffer.getLength();
        }
        return 0;
    }

    public boolean isViewVisible() {
        return pane.isVisible();
    }

    public JComponent getView() {
        //can debug painting by adding JXLayer, commenthing the next and uncommenting the next after that
        return pane;
        //return new org.jdesktop.jxlayer.JXLayer(pane, new org.jdesktop.jxlayer.plaf.ext.DebugRepaintingUI());
    }

    public void setSelectionColor(Color color) {
        pane.setSelectionColor(color);
    }

    public Color getSelectionColor() {
        return pane.getSelectionColor();
    }

    /**
     * Gets text from between the indexes
     * @param index
     * @param endIndex
     * @return text
     * @throws IllegalArgumentException
     * if the location is not valid.
     */
    public String getText(int index, int endIndex) {
        try {
            return buffer.getText(index, endIndex - index);
        } catch (BadLocationException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Gets the visible text now.
     * @return text
     */
    public String getVisibleText() {
        return getText(getIndex(), getLastVisibleIndex());
    }

    /**
     * Text from a paragraph.
     * @param index
     * @return text
     * @throws IllegalArgumentException
     * if the paragraph does not exist.
     */
    public String getParagraphAt(int index) {
        Element root = buffer.getRootElements()[0];
        assert root.getElementCount() > 0;
        try {
            Element child = root.getElement(root.getElementIndex(index));
            int start = child.getStartOffset();
            int end = child.getEndOffset();
            return buffer.getText(start, end - start);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Text from a paragraph, truncated to start at index.
     * @param index
     * @return text
     * @throws IllegalArgumentException
     * if the paragraph does not exist.
     */
    private String getTruncatedParagraphAt(int index) {
        Element root = buffer.getRootElements()[0];
        assert root.getElementCount() > 0;
        try {
            Element child = root.getElement(root.getElementIndex(index));
            int end = child.getEndOffset();
            return buffer.getText(index, end - index);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Returns the current text paragraphs so that
     * the current paragraph is kept in view. Moves the
     * pane automatically.
     * @param initialParagraphIndex a index belonging to
     * the initial paragraph. The returned string will be
     * cut to only show the visible part of the paragraph.
     */
    public Iterator<String> getParagraphIterator(int initialParagraphIndex) {
        return new ParagraphIterator(initialParagraphIndex);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        support.removePropertyChangeListener(propertyName, listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        support.addPropertyChangeListener(propertyName, listener);
    }

    public DocumentStyle getDocumentStyle() {
        if (documentStyle == null) {
            documentStyle = new DocumentStyle(buffer);
        }
        return documentStyle;
    }

    public SearchIterator getSearchIterator(String searchWord, int startOfSearch, boolean caseInsensitive) {
        return new SearchIterator(searchWord, buffer, startOfSearch, caseInsensitive);
    }

    /**
     * Remove a highlighter
     * @param key if null does nothing
     */
    public void removeHighlight(final Object key) {
        if (key == null) {
            return;
        }

        Highlighter h = pane.getHighlighter();
        h.removeHighlight(key);
    }
    private transient Highlighter.HighlightPainter painter;

    /**
     * Highlight a visible part of the text. If the indexes
     * given are not visible, this doesn't add a highlighter.
     * @param highlightStart
     * @param highlightEnd
     * @return a highlight key
     */
    public Object highlight(int highlightStart, int highlightEnd) {
        if (highlightStart >= highlightEnd) {
            throw new IllegalArgumentException(
                    "Start highlight index can't be over the end highlight index");
        }

        try {
            Object o = null;
            if (isDirty) {
                int endDocIndex = getLastVisibleIndex();
                if (endDocIndex != 0 && highlightStart < endDocIndex) {
                    //translate document to view coordinates
                    int interval = endDocIndex - highlightStart;
                    int s = pane.getDocument().getLength() - interval;
                    int e = s + highlightEnd - highlightStart;
//                    System.out.println("START "+s+ " END "+e + " WORD "+getText(highlightStart, highlightEnd));
                    o = pane.getHighlighter().addHighlight(s, e, painter);
                }
            } else {
                int i = getIndex();
                if (highlightStart >= i) {
                    o = pane.getHighlighter().addHighlight(highlightStart - i, highlightEnd - i, painter);
                }
            }
            return o;
        } catch (BadLocationException ex) {
            throw new AssertionError("The document view is out of bounds.", ex);
        }
    }

    private final class ParagraphIterator implements Iterator<String> {

        int paragraphIndex;

        public ParagraphIterator(int initialParagraphIndex) {
            paragraphIndex = initialParagraphIndex;
        }

        @Override
        public boolean hasNext() {
            return paragraphIndex < getLength();
        }

        @Override
        public String next() {
            int barrierIndex = getLastVisibleIndex();
            String s;

            if (barrierIndex > paragraphIndex) {
                s = getTruncatedParagraphAt(paragraphIndex);
                if ((paragraphIndex + s.length()) > barrierIndex) {
                    setIndex(paragraphIndex);
                }
            } else {
                setIndex(paragraphIndex);
                s = getTruncatedParagraphAt(paragraphIndex);
            }
            paragraphIndex += s.length();
            return s;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    /**
     * Forward linkevents when the links are clicked.
     */
    private final class LinkController extends MouseAdapter {

        private String lastRefString;
        private Point point = new Point();

        @Override
        public void mouseClicked(MouseEvent evt) {
            point.x = evt.getX();
            point.y = evt.getY();
            AttributeSet set = getCharacterAttributesAt(point);
            Document d = pane.getDocument();

            if (!isLeftMouseButton(evt)) {
                return;
            }
            if (set.isDefined(HREF)) {//a indexed position
                Object key = set.getAttribute(HREF);

                if (key == null || d.getProperty(key) == null) {
                    return;
                }
                Integer destination = (Integer) d.getProperty(key);
                support.firePropertyChange(MOUSE_CLICK_HYPERLINK, getIndex(), destination.intValue());
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            point.x = e.getX();
            point.y = e.getY();
            AttributeSet set = getCharacterAttributesAt(point);

            if (set.isDefined(HREF)) {
                String refString = (String) set.getAttribute(HREF);
                //only the first time the url is hovered.
                if (!refString.equals(lastRefString)) {
                    lastRefString = refString;
                    support.firePropertyChange(MOUSE_ENTER_HYPERLINK, null, null);
                }
            } else {
                boolean wasIn = lastRefString != null;
                lastRefString = null;
                if (wasIn) {
                    support.firePropertyChange(MOUSE_EXIT_HYPERLINK, null, null);
                }
            }
        }
    }

    private final class ListenToDocument implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (buffer != null) {
                index = getIndex();
                bufferToViewLayoutForward();
            }
        }
    }

    private static final class WrappedColor extends Color {

        Color wrapped;

        @Override
        public String toString() {
            return wrapped.toString();
        }

        @Override
        public int hashCode() {
            return wrapped.hashCode();
        }

        @Override
        public int getTransparency() {
            return wrapped.getTransparency();
        }

        @Override
        public int getRed() {
            return wrapped.getRed();
        }

        @Override
        public float[] getRGBComponents(float[] compArray) {
            return wrapped.getRGBComponents(compArray);
        }

        @Override
        public float[] getRGBColorComponents(float[] compArray) {
            return wrapped.getRGBColorComponents(compArray);
        }

        @Override
        public int getRGB() {
            return wrapped.getRGB();
        }

        @Override
        public int getGreen() {
            return wrapped.getGreen();
        }

        @Override
        public float[] getComponents(ColorSpace cspace, float[] compArray) {
            return wrapped.getComponents(cspace, compArray);
        }

        @Override
        public float[] getComponents(float[] compArray) {
            return wrapped.getComponents(compArray);
        }

        @Override
        public ColorSpace getColorSpace() {
            return wrapped.getColorSpace();
        }

        @Override
        public float[] getColorComponents(ColorSpace cspace, float[] compArray) {
            return wrapped.getColorComponents(cspace, compArray);
        }

        @Override
        public float[] getColorComponents(float[] compArray) {
            return wrapped.getColorComponents(compArray);
        }

        @Override
        public int getBlue() {
            return wrapped.getBlue();
        }

        @Override
        public int getAlpha() {
            return wrapped.getAlpha();
        }

        @Override
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        public boolean equals(Object obj) {
            return wrapped.equals(obj);
        }

        @Override
        public Color darker() {
            return wrapped.darker();
        }

        @Override
        public PaintContext createContext(ColorModel colorModel, Rectangle r, Rectangle2D r2d, AffineTransform xform, RenderingHints hints) {
            return wrapped.createContext(colorModel, r, r2d, xform, hints);
        }

        @Override
        public Color brighter() {
            return wrapped.brighter();
        }

        public WrappedColor(Color wrapped) {
            super(0);
            this.wrapped = wrapped;
        }

        public void setWrapped(Color wrapped) {
            this.wrapped = wrapped;
        }
    }
}
