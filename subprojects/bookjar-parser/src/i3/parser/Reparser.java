package i3.parser;

import java.awt.Color;
import java.util.AbstractSequentialList;
import java.util.Collection;
import java.util.LinkedList;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.Segment;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTML.Attribute;

/**
 * This class is thread-safe (stateless).
 *
 * A class specially made to structurally standardize all kinds of documents
 * that descend from the StyledDocument class in java. It's a destructive
 * operation, and destroys the original document and uses document memory * 2.
 * Careful, it makes things to text to make its prose more readable but things
 * like poetry are mangled by it. Forget about ASCII art using this. Removes
 * spaces between words, and at the start of phrases Removes all tabs, and empty
 * paragraphs. Removes the color black from characters because : 1) It is the
 * default color if the color is unknown 2) It is the most common color and
 * quite useless on most documents (it gets there by accident mostly) 3) The
 * larger program replaces undefined colors. It would be neat to show uncommon
 * colors and replace the most common one. I'm guessing that the most common one
 * is black, and then I can show the uncommon ones and still allow the uncommon
 * to show.
 *
 * It also fixes malformed paragraphs, in that the first letter like char is
 * lowercase or pontuation. Things like: A strange city, but familiar
 * nonetheless.
 *
 * This kind of formatting is useless for prose, and an habitual error in
 * scanners.
 *
 * There is one situation where this doesn't fix the broken paragraph: But i was
 * going to Paris.
 *
 * Since this kind of thing is usual in titles: A tale of two cities Chapter one
 * It was ...
 *
 * would become: A tale of two cities Chapter one It was ...
 *
 * Any test of that condition would have this disadvantage. Just fix the
 * paragraphs in the book.
 *
 * @author i30817
 * @threadSafe
 */
public final class Reparser {

    /**
     * Reparses the given document to uniformize the wordspacing,
     * firstlineindent and paragraph spacing. These are set on other methods
     *
     * @param doc styleddocument to be standardized - WARNING - the original
     * document is modified (so that there are no news)
     * @return the standardized document. The value of any HTML.Attribute.HREF
     * key will have a mapping to the Position given by an internal link (if
     * any) in the new document like this:
     * doc.getProperty(attributeSet.getAttribute(HTML.Attribute.HREF))
     */
    public StyledDocument reParse(StyledDocument doc) {
        DefaultStyledDocument cache = new ParserDocument();
        BufferedStyledDocumentBuilder builder = new BufferedStyledDocumentBuilder(cache);
        try {
            nonRecursiveReparse(doc, builder);
        } catch (BadLocationException ex) {
            throw new AssertionError(ex);
        }
        builder.commit();
        return cache;
    }

    //The process this method uses is destructive to the original document.
    //the idea is to use the document segment (that can or can not be
    //the original document array) as a scratch pad for the normalized
    //document - this is done by replacing the chars you want to erase in the document
    //by a marker char at the paragraph level.
    //(the one i used appears in rtf document for breakpoints, so it should be removed anyway)
    //\n is handled specially by joining them if the current paragraph starts with lower case
    //P1 [Yes][.][ ][ ][\n]
    //P2 [It][ ][was][\n]
    //P3 [\n]
    //P4 [a][ ][ ][thing]->
    //P1 [Yes][.][umappable][unmappable][\n]
    //P2 [It][ ][was][ ][a][umappable][ ][thing]
    //each paragraph after processing is added to the new document with a builder that ignores
    //the unmappable char. The deleted \n are deleted on this new document with the builder
    private void nonRecursiveReparse(final StyledDocument oldDoc, final BufferedStyledDocumentBuilder newDoc) throws BadLocationException {
        SkipStringBuilder builder = new SkipStringBuilder(250);
        char unmapable = builder.getUnmapableChar();
        AbstractSequentialList listOfLinks = new LinkedList();
        //don't use partial returns, since there is not much point (only 1 gap)
        Segment paragraphText = new Segment();
        final Element root = oldDoc.getDefaultRootElement();
        final int numberOfParagraphs = root.getElementCount();

        //for each paragraph construct the new doc from the older.
        for (int i = 0; i < numberOfParagraphs; i++) {
            Element paragraph = root.getElement(i);
            int start = paragraph.getStartOffset();
            int end = paragraph.getEndOffset();
            assert ((end - start) >= 0);
            oldDoc.getText(start, end - start, paragraphText);
            markUnwantedCharacters(paragraphText, unmapable);
            handleParagraphMerge(newDoc, paragraphText);
            copyTextAndAtributes(paragraph, paragraphText, builder, newDoc, listOfLinks);
        }//END PARAGRAPHS FOR
    }

    /**
     * Tries to join paragraphs with a simple test - If the first letter is
     * lowercase, delete the previous paragraph \n
     *
     * @param newDoc
     * @param paragraphText
     * @throws BadLocationException
     */
    private void handleParagraphMerge(BufferedStyledDocumentBuilder newDoc, Segment paragraphText) throws BadLocationException {
        int len = paragraphText.offset + paragraphText.count;
        //Don't do it in the first paragraph
        if (newDoc.getLength() != 0) {
            for (int index = paragraphText.offset; index < len; index++) {
                if (Character.isLetter(paragraphText.array[index])) {
                    if (Character.isLowerCase(paragraphText.array[index])) {
                        //Last char in the parsed text should be \n
                        newDoc.removeLast();
                        //there is no space at the end so we have to insert:
                        //it was removed from copyTextAttributes after being
                        //marked by markUnwantedCharacters
                        newDoc.appendSpace(SimpleAttributeSet.EMPTY);
                    }
                    break;
                }
            }
        }
    }

    private void copyTextAndAtributes(Element paragraph, Segment fragmentText, SkipStringBuilder builder, BufferedStyledDocumentBuilder newDoc, AbstractSequentialList listOfLinks) throws BadLocationException {
        final int numberOfFragments = paragraph.getElementCount();
        boolean paragraphInserted = false;
        int startParagraph = paragraph.getStartOffset();
        //int endParagraph = paragraph.getEndOffset();
//        System.out.print(">>"+fragmentText.toString()+"<<");
        for (int i = 0; i < numberOfFragments; i++) {
            Element fragment = paragraph.getElement(i);
            //translate document indexes to the segment indexes.
            int start = fragmentText.offset + (fragment.getStartOffset() - startParagraph);
            int end = fragmentText.offset + (fragment.getEndOffset() - startParagraph);
            //copy any urls until we get insertable text (Names are href destinations)
            if (fragment.getAttributes().isDefined(Attribute.NAME)) {
                listOfLinks.addAll((Collection) fragment.getAttributes().getAttribute(Attribute.NAME));
            }
            //erase the unmappable char
            builder.append(fragmentText.array, start, end - start);
            int builderLen = builder.length();
            if (builderLen != 0) {//now finally insert the processed line
                for (Object s : listOfLinks) {//put the name attr as links for this document index
                    newDoc.getDocument().putProperty(s, Integer.valueOf(newDoc.getLength()));
                }
                listOfLinks.clear();
                paragraphInserted = true;
                //insert the string with the old atributes except Color if black
                Documents.vetoSpecificCharacterAttribute(fragment, StyleConstants.Foreground, Color.BLACK);
                AttributeSet set = fragment.getAttributes();
//                System.out.print("->"+builder.toString());
                newDoc.append(builder.toCharArray(), set);
                builder.clear();
            }
        }
        if (paragraphInserted) {
            newDoc.appendEnd(SimpleAttributeSet.EMPTY);
        }
    }

    /**
     * Sets unwanted spaces or special characters to the unmappable char in the
     * array A space is unwanted, if it starts at the beginning or end of the
     * array, or if there is more than 1 between words.
     *
     * @param paragraphSegment
     * @param unmapable
     */
    private void markUnwantedCharacters(final Segment paragraphSegment, final char unmapable) {
        final int len = paragraphSegment.offset + paragraphSegment.count;
        int startIndex = paragraphSegment.offset, endIndex = len - 1;
        final char[] array = paragraphSegment.array;

        //erases whitespace at start of paragraph  and unmmappable chars
        //erases whole paragraph if empty
        for (; startIndex < len; startIndex++) {
            final char c = array[startIndex];
            if (!isWhite(c) && !isUnmappable(c)) {
                break;
            }
            array[startIndex] = unmapable;
        }

        //erases whitespace at end of paragraph and unmmappable chars
        for (; endIndex > startIndex; endIndex--) {
            final char c = array[endIndex];
            if (!isWhite(c) && !isUnmappable(c)) {
                break;
            }
            array[endIndex] = unmapable;
        }
        //erase the duplicate spaces between words and unmmappable chars
        for (; startIndex < endIndex; startIndex++) {
            final char c = array[startIndex];
            if (isWhite(c)) {
                //all whitespace becomes simple space (TODO: consider supporting tabs)
                array[startIndex] = ' ';
                //startIndex not 0 if isWhite(c)==true (see first for)
                final int last = startIndex - 1;
                if (array[last] == ' ') {
                    //make the last whitespace unmapable
                    array[last] = unmapable;
                }
            } else if (isUnmappable(c)) {
                array[startIndex] = unmapable;
            }
            //not whitespace or unmappable
        }
    }

    private boolean isUnmappable(final char c) {
        return //c == '\u00A0' || //nonbreaking space (used as formatting, leave it)
                //c == '\u2007';   // || //nonbreaking spaces
                c == '\u2027';// || //wierd rtf control
        //c == '\u202F';   //nonbreaking spaces
    }

    private boolean isWhite(final char c) {
        return Character.isWhitespace(c);
    }
}
