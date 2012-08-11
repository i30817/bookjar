/*
 * Copyright (c) 2006, Stephen Kelvin Friedrich,  All rights reserved.
 *
 * This a BSD license. If you use or enhance the code, I'd be pleased if you sent a mail to s.friedrich@eekboom.com
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
following disclaimer in the documentation and/or other materials provided with the distribution.
 *     * Neither the name of the "Stephen Kelvin Friedrich" nor the names of its contributors may be used to endorse
 *       or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package i3.util;

import java.awt.FontMetrics;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.Collator;
import java.util.Comparator;

/**
 * Utility class for common String operations
 */
public final class Strings {

    /**
     * <p>A string comparator that does case sensitive comparisons and handles embedded numbers correctly.</p>
     * <p><b>Do not use</b> if your app might ever run on any locale that uses more than 7-bit ascii characters.</p>
     */
    private static final Comparator<String> NATURAL_COMPARATOR_ASCII = new Comparator<String>() {

        public int compare(String o1, String o2) {
            return compareNaturalAscii(o1, o2);
        }
    };
    /**
     * <p>A string comparator that does case insensitive comparisons and handles embedded numbers correctly.</p>
     * <p><b>Do not use</b> if your app might ever run on any locale that uses more than 7-bit ascii characters.</p>
     */
    private static final Comparator<String> IGNORE_CASE_NATURAL_COMPARATOR_ASCII = new Comparator<String>() {

        public int compare(String o1, String o2) {
            return compareNaturalIgnoreCaseAscii(o1, o2);
        }
    };

    /**
     * This is a utility class (static methods only), don't instantiate.
     */
    private Strings() {
    }

    public static void linebreakString(StringBuilder out, String stringToBreak, String breakSeperator, int requiredWidth, FontMetrics tooltipMetrics) {
        AttributedString st = new AttributedString(stringToBreak);
        st.addAttribute(TextAttribute.FONT, tooltipMetrics.getFont(), 0, stringToBreak.length());
        AttributedCharacterIterator paragraph = st.getIterator();
        FontRenderContext context = tooltipMetrics.getFontRenderContext();
        LineBreakMeasurer ms = new LineBreakMeasurer(paragraph, context);

        while (ms.getPosition() < stringToBreak.length()) {
            int startIndex = ms.getPosition();
            ms.nextLayout(requiredWidth);
            out.append(stringToBreak.substring(startIndex, ms.getPosition()));
            out.append(breakSeperator);
        }
    }

    /**
     * Searches for the first ocurrence of the substring inside s
     * that is bordered by startStringExclusive and endStringExclusive
     * or null if it doesn't exist
     * @param s
     * @param startStringExclusive
     * @param endStringExclusive
     * @return substring or null
     */
    public static String subStringSearchBetween(String s, String startStringExclusive, String endStringExclusive) {

        int index = s.indexOf(startStringExclusive);
        if (index == -1) {
            return null;
        }
        index += startStringExclusive.length();
        int endIndex = s.indexOf(endStringExclusive, index);
        if (endIndex == -1) {
            return null;
        }

        return s.substring(index, endIndex);
    }

    public static String longestSubstring(String str1, String str2) {

        StringBuilder sb = new StringBuilder();
        if (str1 == null || str1.isEmpty() || str2 == null || str2.isEmpty()) {
            return "";
        }

// ignore case
        str1 = str1.toLowerCase();
        str2 = str2.toLowerCase();

// java initializes them already with 0
        int[][] num = new int[str1.length()][str2.length()];
        int maxlen = 0;
        int lastSubsBegin = 0;

        for (int i = 0; i < str1.length(); i++) {
            for (int j = 0; j < str2.length(); j++) {
                if (str1.charAt(i) == str2.charAt(j)) {
                    if ((i == 0) || (j == 0)) {
                        num[i][j] = 1;
                    } else {
                        num[i][j] = 1 + num[i - 1][j - 1];
                    }

                    if (num[i][j] > maxlen) {
                        maxlen = num[i][j];
                        // generate substring from str1 >= i
                        int thisSubsBegin = i - num[i][j] + 1;
                        if (lastSubsBegin == thisSubsBegin) {
                            //if the current LCS is the same as the last time this block ran
                            sb.append(str1.charAt(i));
                        } else {
                            //this block resets the string builder if a different LCS is found
                            lastSubsBegin = thisSubsBegin;
                            sb = new StringBuilder();
                            sb.append(str1.substring(lastSubsBegin, i + 1));
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Returns a comparator that compares contained numbers based on their numeric values and compares other parts
     * using the current locale's order rules.
     * <p>For example in German locale this will be a comparator that handles umlauts correctly and ignores
     * upper/lower case differences.</p>
     *
     * @return <p>A string comparator that uses the current locale's order rules and handles embedded numbers
     *         correctly.</p>
     * @see #getNaturalComparator(java.text.Collator)
     */
    public static Comparator<String> getNaturalComparator() {
        Collator collator = Collator.getInstance();
        return getNaturalComparator(collator);
    }

    /**
     * Returns a comparator that compares contained numbers based on their numeric values and compares other parts
     * using the given collator.
     *
     * @param collator used for locale specific comparison of text (non-number) subwords - must not be null
     * @return <p>A string comparator that uses the given Collator to compare subwords and handles embedded numbers
     *         correctly.</p>
     * @see #getNaturalComparator()
     */
    public static Comparator<String> getNaturalComparator(final Collator collator) {
        if (collator == null) {
            // it's important to explicitly handle this here - else the bug will manifest anytime later in possibly
            // unrelated code that tries to use the comparator
            throw new NullPointerException("collator must not be null");
        }
        return new Comparator<String>() {

            public int compare(String o1, String o2) {
                return compareNatural(collator, o1, o2);
            }
        };
    }

    /**
     * Returns a comparator that compares contained numbers based on their numeric values and compares other parts
     * based on each character's Unicode value.
     *
     * @return <p>a string comparator that does case sensitive comparisons on pure ascii strings and handles embedded
     *         numbers correctly.</p>
     *         <b>Do not use</b> if your app might ever run on any locale that uses more than 7-bit ascii characters.
     * @see #getNaturalComparator()
     * @see #getNaturalComparator(java.text.Collator)
     */
    public static Comparator<String> getNaturalComparatorAscii() {
        return NATURAL_COMPARATOR_ASCII;
    }

    /**
     * Returns a comparator that compares contained numbers based on their numeric values and compares other parts
     * based on each character's Unicode value while ignore upper/lower case differences.
     * <b>Do not use</b> if your app might ever run on any locale that uses more than 7-bit ascii characters.
     *
     * @return <p>a string comparator that does case insensitive comparisons on pure ascii strings and handles embedded
     *         numbers correctly.</p>
     * @see #getNaturalComparator()
     * @see #getNaturalComparator(java.text.Collator)
     */
    public static Comparator<String> getNaturalComparatorIgnoreCaseAscii() {
        return IGNORE_CASE_NATURAL_COMPARATOR_ASCII;
    }

    /**
     * <p>Compares two strings using the current locale's rules and comparing contained numbers based on their numeric
     * values.</p>
     * <p>This is probably the best default comparison to use.</p>
     * <p>If you know that the texts to be compared are in a certain language that differs from the default locale's
     * langage, then get a collator for the desired locale ({@link java.text.Collator#getInstance(java.util.Locale)})
     * and pass it to {@link #compareNatural(java.text.Collator, String, String)}</p>
     *
     * @param s first string
     * @param t second string
     * @return zero iff <code>s</code> and <code>t</code> are equal,
     *         a value less than zero iff <code>s</code> lexicographically precedes <code>t</code>
     *         and a value larger than zero iff <code>s</code> lexicographically follows <code>t</code>
     */
    public static int compareNatural(String s, String t) {
        return compareNatural(s, t, false, Collator.getInstance());
    }

    /**
     * <p>Compares two strings using the given collator and comparing contained numbers based on their numeric
     * values.</p>
     *
     * @param s first string
     * @param t second string
     * @return zero iff <code>s</code> and <code>t</code> are equal,
     *         a value less than zero iff <code>s</code> lexicographically precedes <code>t</code>
     *         and a value larger than zero iff <code>s</code> lexicographically follows <code>t</code>
     */
    public static int compareNatural(Collator collator, String s, String t) {
        return compareNatural(s, t, true, collator);
    }

    /**
     * <p>Compares two strings using each character's Unicode value for non-digit characters and the numeric values off
     * any contained numbers.</p>
     * <p>(This will probably make sense only for strings containing 7-bit ascii characters only.)</p>
     *
     * @return zero iff <code>s</code> and <code>t</code> are equal,
     *         a value less than zero iff <code>s</code> lexicographically precedes <code>t</code>
     *         and a value larger than zero iff <code>s</code> lexicographically follows <code>t</code>
     */
    public static int compareNaturalAscii(String s, String t) {
        return compareNatural(s, t, true, null);
    }

    /**
     * <p>Compares two strings using each character's Unicode value - ignoring upper/lower case - for non-digit
     * characters and the numeric values of any contained numbers.</p>
     * <p>(This will probably make sense only for strings containing 7-bit ascii characters only.)</p>
     *
     * @return zero iff <code>s</code> and <code>t</code> are equal,
     *         a value less than zero iff <code>s</code> lexicographically precedes <code>t</code>
     *         and a value larger than zero iff <code>s</code> lexicographically follows <code>t</code>
     */
    public static int compareNaturalIgnoreCaseAscii(String s, String t) {
        return compareNatural(s, t, false, null);
    }

    /**
     * @param s             first string
     * @param t             second string
     * @param caseSensitive treat characters differing in case only as equal - will be ignored if a collator is given
     * @param collator      used to compare subwords that aren't numbers - if null, characters will be compared
     *                      individually based on their Unicode value
     * @return zero iff <code>s</code> and <code>t</code> are equal,
     *         a value less than zero iff <code>s</code> lexicographically precedes <code>t</code>
     *         and a value larger than zero iff <code>s</code> lexicographically follows <code>t</code>
     */
    private static int compareNatural(String s, String t, boolean caseSensitive, Collator collator) {
        int sIndex = 0;
        int tIndex = 0;

        int sLength = s.length();
        int tLength = t.length();

        while (true) {
            // both character indices are after a subword (or at zero)

            // Check if one string is at end
            if (sIndex == sLength && tIndex == tLength) {
                return 0;
            }
            if (sIndex == sLength) {
                return -1;
            }
            if (tIndex == tLength) {
                return 1;
            }

            // Compare sub word
            char sChar = s.charAt(sIndex);
            char tChar = t.charAt(tIndex);

            boolean sCharIsDigit = Character.isDigit(sChar);
            boolean tCharIsDigit = Character.isDigit(tChar);

            if (sCharIsDigit && tCharIsDigit) {
                // Compare numbers

                // skip leading 0s
                int sLeadingZeroCount = 0;
                while (sChar == '0') {
                    ++sLeadingZeroCount;
                    ++sIndex;
                    if (sIndex == sLength) {
                        break;
                    }
                    sChar = s.charAt(sIndex);
                }
                int tLeadingZeroCount = 0;
                while (tChar == '0') {
                    ++tLeadingZeroCount;
                    ++tIndex;
                    if (tIndex == tLength) {
                        break;
                    }
                    tChar = t.charAt(tIndex);
                }
                boolean sAllZero = sIndex == sLength || !Character.isDigit(sChar);
                boolean tAllZero = tIndex == tLength || !Character.isDigit(tChar);
                if (sAllZero && tAllZero) {
                    continue;
                }
                if (sAllZero && !tAllZero) {
                    return -1;
                }
                if (tAllZero) {
                    return 1;
                }

                int diff = 0;
                do {
                    if (diff == 0) {
                        diff = sChar - tChar;
                    }
                    ++sIndex;
                    ++tIndex;
                    if (sIndex == sLength && tIndex == tLength) {
                        return diff != 0 ? diff : sLeadingZeroCount - tLeadingZeroCount;
                    }
                    if (sIndex == sLength) {
                        if (diff == 0) {
                            return -1;
                        }
                        return Character.isDigit(t.charAt(tIndex)) ? -1 : diff;
                    }
                    if (tIndex == tLength) {
                        if (diff == 0) {
                            return 1;
                        }
                        return Character.isDigit(s.charAt(sIndex)) ? 1 : diff;
                    }
                    sChar = s.charAt(sIndex);
                    tChar = t.charAt(tIndex);
                    sCharIsDigit = Character.isDigit(sChar);
                    tCharIsDigit = Character.isDigit(tChar);
                    if (!sCharIsDigit && !tCharIsDigit) {
                        // both number sub words have the same length
                        if (diff != 0) {
                            return diff;
                        }
                        break;
                    }
                    if (!sCharIsDigit) {
                        return -1;
                    }
                    if (!tCharIsDigit) {
                        return 1;
                    }
                } while (true);
            } else {
                // Compare words
                if (collator != null) {
                    // To use the collator the whole subwords have to be compared - character-by-character comparision
                    // is not possible. So find the two subwords first
                    int aw = sIndex;
                    int bw = tIndex;
                    do {
                        ++sIndex;
                    } while (sIndex < sLength && !Character.isDigit(s.charAt(sIndex)));
                    do {
                        ++tIndex;
                    } while (tIndex < tLength && !Character.isDigit(t.charAt(tIndex)));

                    String as = s.substring(aw, sIndex);
                    String bs = t.substring(bw, tIndex);
                    int subwordResult = collator.compare(as, bs);
                    if (subwordResult != 0) {
                        return subwordResult;
                    }
                } else {
                    // No collator specified. All characters should be ascii only. Compare character-by-character.
                    do {
                        if (sChar != tChar) {
                            if (caseSensitive) {
                                return sChar - tChar;
                            }
                            sChar = Character.toUpperCase(sChar);
                            tChar = Character.toUpperCase(tChar);
                            if (sChar != tChar) {
                                sChar = Character.toLowerCase(sChar);
                                tChar = Character.toLowerCase(tChar);
                                if (sChar != tChar) {
                                    return sChar - tChar;
                                }
                            }
                        }
                        ++sIndex;
                        ++tIndex;
                        if (sIndex == sLength && tIndex == tLength) {
                            return 0;
                        }
                        if (sIndex == sLength) {
                            return -1;
                        }
                        if (tIndex == tLength) {
                            return 1;
                        }
                        sChar = s.charAt(sIndex);
                        tChar = t.charAt(tIndex);
                        sCharIsDigit = Character.isDigit(sChar);
                        tCharIsDigit = Character.isDigit(tChar);
                    } while (!sCharIsDigit && !tCharIsDigit);
                }
            }
        }
    }

    /**
     * @param s notnull string
     * @param delimiter last delimiter that serves as boundary to the result
     * substring (not inclusive)
     * @return s.substring(0, s.lastIndexOf(delimiter)) or s
     */
    public static String subStringBeforeLast(String s, char delimiter) {
        int lastIndex = s.lastIndexOf(delimiter);
        if (lastIndex != -1) {
            s = s.substring(0, lastIndex);
        }
        return s;
    }

    /**
     * @param s notnull string
     * @param delimiter last delimiter that serves as start offset to the result
     * substring (exclusive)
     * @return s.substring(s.lastIndexOf(delimiter)+1) or ""
     */
    public static String subStringAfterLast(String s, char delimiter) {
        int lastIndex = s.lastIndexOf(delimiter);
        if (lastIndex != -1) {
            return s.substring(lastIndex + 1);
        }
        return "";
    }

    /**
     * @param s notnull string
     * @param delimiter last delimiter that serves as start offset to the result
     * substring (inclusive)
     * @return s.substring(s.lastIndexOf(delimiter)) or ""
     */
    public static String subStringFromLast(String s, char delimiter) {
        int lastIndex = s.lastIndexOf(delimiter);
        if (lastIndex != -1) {
            return s.substring(lastIndex);
        }
        return "";
    }

    public static String subStringBeforeFirst(String s, char delimiter) {
        int index = s.indexOf(delimiter);
        if (index != -1) {
            s = s.substring(0, index);
        }
        return s;
    }

    public static String subStringAfterFirst(String s, char delimiter) {
        int index = s.indexOf(delimiter);
        if (index != -1) {
            return s.substring(index + 1);
        }
        return "";
    }

    public static String subStringFromFirst(String s, char delimiter) {
        int index = s.indexOf(delimiter);
        if (index != -1) {
            return s.substring(index);
        }
        return "";
    }

    public static String trimIncludingNonbreakingSpace(String s) {
        return s.replaceFirst("^[\\x00-\\x200\\xA0]+", "").replaceFirst("[\\x00-\\x20\\xA0]+$", "");
    }
}
