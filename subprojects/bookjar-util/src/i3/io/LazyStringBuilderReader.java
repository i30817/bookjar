package i3.io;

import java.io.IOException;
import java.io.Reader;

/**
 * A reader that reads its characters from a StringBuilder.
 *
 * The fillStringBuilder() method should fill the
 * stringbuilder incrementally. In each evocation,
 * the stringbuilder is drained, so new contents can be
 * written without enlarging the string builder.
 *
 * When you want to stop reading, make isReady() return false.
 *
 *
 * Be careful working with this, since if fillStringBuilder()
 * uses an collection iterator to iterate over the data
 * and the collection is used afterwards,
 * but before the LazyStringBuilderReader read everything,
 * it will throw a concurrent modification exception the next
 * time that LazyStringBuilderReader tries to read.
 *
 * Specifically, if the fillStringBuilder() uses an iterator,
 * you may need to lazily create it (on the isReady() method).
 *
 * @author i30817
 */
public abstract class LazyStringBuilderReader extends Reader {

    private StringBuilderReader inner;

    /**
     * Instantiate the LazyStringBuilderReader with the
     * an StringBuilderReader of size stringBuilderSize
     * @param stringBuilderSize
     */
    public LazyStringBuilderReader(int stringBuilderSize) {
        super();
        inner = new StringBuilderReader(new StringBuilder(stringBuilderSize));
    }

    /**
     *
     * @return the StringBuilder the reader uses
     */
    protected final StringBuilder getStringBuilder() {
        return inner.str;
    }

    /**
     * Fill the StringBuilder in this method
     */
    protected abstract void fillStringBuilder();

    /**
     *
     * @return false when you want to stop reading
     */
    protected abstract boolean isReady();

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int result = inner.read(cbuf, off, len);
        //fill the stringbuilder. Optionally call stop
        if (result == -1 && isReady()) {
            inner.str.setLength(0);
            fillStringBuilder();
            inner.next = 0;
            inner.length = inner.str.length();
            return inner.read(cbuf, off, len);
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        inner.close();
    }
}
