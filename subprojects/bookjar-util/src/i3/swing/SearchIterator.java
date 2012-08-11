package i3.swing;

import com.eaio.stringsearch.BNDMCI;
import com.eaio.stringsearch.BoyerMooreHorspoolRaita;
import com.eaio.stringsearch.StringSearch;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;

/**
 * Searches words in a document.
 *
 * This class is not threadsafe. The document given
 * is saved as a WeakReference, so it only works while
 * there is a hard reference to the given document.
 * (done so memory won't leak). There is the danger,
 * that if the document is not referenced somewhere else, this
 * class never finds anything... But this can be avoided
 * by hard referencing the document, and is very uncommon.
 * @author i30817
 */
public final class SearchIterator implements Iterator<Integer> {

    private final StringSearch search;
    private final WeakReference<Document> doc;
    private int searchIndex;
    private int lastFound = -1;
    private char[] chars;
    private Object preprocessed;
    private String phrase;

    public SearchIterator(String phrase, Document doc, int index, boolean caseInsensitive) {
        super();
        this.doc = new WeakReference<>(doc);
        this.searchIndex = index;
        if (caseInsensitive) {
            search = new BNDMCI();
        } else {
            search = new BoyerMooreHorspoolRaita();
        }
        setSearchText(phrase);
    }

    /**
     * Defaults to case sensitive search
     * @param phrase
     * @param doc
     * @param index
     */
    public SearchIterator(String phrase, Document doc, int index) {
        this(phrase, doc, index, false);
    }

    public String getSearchText() {
        return phrase;
    }

    public void setSearchIndex(int index) {
        searchIndex = index;
    }

    /**
     * Sets the new word to search if different from the old.
     * (from the same internal index)
     * @param newPhrase not null
     */
    public void setSearchText(String newPhrase) {
        if (!newPhrase.equals(phrase)) {
            phrase = newPhrase;
            chars = newPhrase.toCharArray();
            //library invariant.
            if (!"".equals(phrase)) {
                preprocessed = search.processChars(chars);
            }
        }
    }

    /**
     * Memory optimized document search (the document array is never fully copied...)
     * @param searchIndex
     * @param textLength
     * @param s
     * @return
     */
    private boolean searchInSegment(Document doc, int searchIndex, int textLength) {
        Segment segment = new Segment();
        segment.setPartialReturn(true);

        try {
            //deal with gaps in document without full copies
            while (textLength > 0) {
//                System.out.println(searchIndex+" "+textLength+" "+doc.getLength());
                doc.getText(searchIndex, textLength, segment);
                //returns the position in the array where found, starting from
                //0 in the array (searchIndex in the doc is segment offset in the array)
                int found = search.searchChars(segment.array, segment.offset, segment.offset + segment.count, chars, preprocessed);
                if (found != -1) {
                    //found index in the document
                    lastFound = searchIndex + found - segment.offset;
                    return true;
                }

                if (segment.isPartialReturn()) {
                    //search at the segment gap boundary...
                    //So that there are no missed words
                    //(copy where the word could be at one gap boundary)
                    int startOfGap = searchIndex + segment.count;
                    int boundaryIndex = Math.max(searchIndex, startOfGap - phrase.length());
                    int boundaryLength = Math.min(doc.getLength() - boundaryIndex, phrase.length() * 2);
                    char[] boundaryText = doc.getText(boundaryIndex, boundaryLength).toCharArray();
                    found = search.searchChars(boundaryText, 0, boundaryText.length, chars, preprocessed);
                    if (found != -1) {
                        //add the start location (not searching from document array)
                        lastFound = boundaryIndex + found;
                        return true;
                    }
                }
                textLength -= segment.count;
                searchIndex += segment.count;
            }
        } catch (BadLocationException ex) {
            throw new AssertionError(ex);
        }
        return false;
    }

    @Override
    public boolean hasNext() {
        //already done at least once
        if (lastFound != -1) {
            return true;
        }
        if ("".equals(phrase)) {
            lastFound = -1;
            return false;
        }
        Document document = doc.get();
        if (document == null) {
            return false;
        }
        return searchInSegment(document, searchIndex, document.getLength() - searchIndex);
    }

    @Override
    public Integer next() {
        if (lastFound == -1) {
            throw new NoSuchElementException("Call hasNext() first even if you know it has one - its where the calculation is done.");
        }
        searchIndex = lastFound + 1;
        int result = lastFound;
        lastFound = -1;
        return result;
    }

    public boolean hasPrevious() {
        //already done at least once
        if (lastFound != -1) {
            return true;
        }
        if ("".equals(phrase)) {
            lastFound = -1;
            return false;
        }
        //lame O(n) backward search... sigh.
        //TODO please memoize this
        int localIndex = searchIndex;
        searchIndex = 0;
        int tmp, tmp2 = -1;

        while (hasNext()) {
            tmp = next();
            if ((tmp + 1) < localIndex) {
                tmp2 = tmp;
            } else {
                break;
            }
        }

        if (tmp2 == -1) {
            //revert the index if found nothing.
            searchIndex = localIndex;
            return false;
        } else {
            //prepare for previous (next() clobbered lastFoundLocation)
            lastFound = tmp2;
            return true;
        }
    }

    public Integer previous() {
        if (lastFound == -1) {
            throw new NoSuchElementException("Call hasPrevious() first even if you know it has one - its where the calculation is done.");
        }
        searchIndex = lastFound + 1;
        int result = lastFound;
        lastFound = -1;
        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Not supported.");
    }
}
