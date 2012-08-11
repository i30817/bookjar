package i3.parser;

/**
 * This StringBuilder foregoes range checks and
 * removes a default ummappable char \u00ad
 * - used as word-break indication in rtf -
 * Only char[] inserted by append or char by insert
 * will erase \u00ad. Only the append operation in
 * implemented like this - this is just the main use,
 * that of "erasing" things from a large array by replacing
 * them for this character and then using this class to
 * do buffered insertions into another array.
 *
 * This class exists to allow deletion operations
 * without news.
 * @author Owner
 */
final class SkipStringBuilder implements CharSequence {

    private char UNMAPPABLE = '\u00ad';
    char[] value;
    int count;

    public char[] toCharArray() {
        char[] dest = new char[count];
        System.arraycopy(value, 0, dest, 0, count);
        return dest;
    }

    @Override
    public String toString() {
        return new String(value, 0, count);
    }

    public SkipStringBuilder(int capacity) {
        value = new char[capacity];
    }

    SkipStringBuilder(char[] v, int start, int len) {
        value = new char[len];
        System.arraycopy(v, start, value, 0, len);
        count = len;
    }

    void expandCapacity(int minimumCapacity) {
        int newCapacity = (value.length + 1) * 2;
        if (newCapacity < 0) {
            newCapacity = Integer.MAX_VALUE;
        } else if (minimumCapacity > newCapacity) {
            newCapacity = minimumCapacity;
        }
        char[] newValue = new char[newCapacity];
        System.arraycopy(value, 0, newValue, 0, count);
        value = newValue;
    }

    public int length() {
        return count;
    }

    public char charAt(int arg0) {
        return value[arg0];
    }

    public CharSequence subSequence(int start, int end) {
        //return new String(value, start, end-start);
        return new SkipStringBuilder(value, start, end - start);
    }

    /**
     * As this is a perfomance aid class i'm not going to test negative
     * numbers of length or offset, or larger than max length. You will get an
     * arrayindexoutofboundsexception if you get smart.
     *
     * @param str the char array to use for insertion
     * @param offset the offset in str to start inserting from
     * @param len the length of text to insert
     * @return
     */
    public SkipStringBuilder append(char[] str, int offset, int len) {
        int newCount = count + len;
        if (newCount > value.length) {
            expandCapacity(newCount);
        }
        final int jmax = offset + len;
        char c;
        while (offset < jmax) {
            c = str[offset];
            offset++;
            if (UNMAPPABLE == c) {
                continue;
            }
            value[count] = c;
            count++;
        }

        return this;
    }

    public SkipStringBuilder deleteCharAt(int index) {
        System.arraycopy(value, index + 1, value, index, count - index - 1);
        count--;
        return this;
    }

    public SkipStringBuilder delete(int start, int end) {
        if (end > count) {
            end = count;
        }
        int len = end - start;
        if (len > 0) {
            System.arraycopy(value, start + len, value, start, count - end);
            count -= len;
        }
        return this;
    }

    public SkipStringBuilder insert(int offset, char c) {
        if (c == UNMAPPABLE) {
            return this;
        }
        int newCount = count + 1;
        if (newCount > value.length) {
            expandCapacity(newCount);
        }
        System.arraycopy(value, offset, value, offset + 1, count - offset);
        value[offset] = c;
        count = newCount;
        return this;
    }

    public SkipStringBuilder replace(int start, int end, String str) {
        if (end > count) {
            end = count;
        }
        int len = str.length();
        int newCount = count + len - (end - start);
        if (newCount > value.length) {
            expandCapacity(newCount);
        }
        System.arraycopy(value, end, value, start + len, count - end);
        str.getChars(0, len, value, start);
        count = newCount;
        return this;
    }

    public void clear() {

        count = 0;
    }

    public char getUnmapableChar() {
        return UNMAPPABLE;
    }

    public void setUnmapableChar(char c) {
        this.UNMAPPABLE = c;
    }
}
