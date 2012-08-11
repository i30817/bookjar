//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package i3.io;

import java.io.*;
import java.net.URLEncoder;
import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copied from a google library. This is deprecated
 * (because the new version is faster)
 * but i didn't want to disentangle the new version.
 *
 * @see java.net.URLEncoder
 *
 */
public final class FastURLEncoder {

    private FastURLEncoder() {
    }



    private static boolean verifyAgainstJava = false;

    /**
     * Set this to 'true' if you are not certain that FastURLEncoder is
     * going to do the right thing for you and want to test for a while.
     * Set to 'false' if you want the speed and memory benefits of
     * FastURLEncoder. If this is set to 'true' and FastURLEncoder disagrees
     * with URLEncoder then FastURLEncoder will log a
     * java.util.logging.Level.SEVERE message and return the value provided
     * by URLEncoder.
     */
    static void setVerifyAgainstJava(boolean shouldVerify) {
        verifyAgainstJava = shouldVerify;
    }

    /**
     * @return 'true' if we are going to verify all results against URLEncoder.
     */
    static boolean getVerifyAgainstJava() {
        return verifyAgainstJava;
    }

    /**
     * URL-escapes s by encoding it with the specified character encoding, and
     * then escaping all octets not included in safeOctets.
     *
     * @param s String to encode.
     * @param encoding character encoding to use (e.g., "UTF-8")
     * @param safeOctets set of octets that should not be escaped.
     * @param plusForSpace whether octet 0x20, i.e. "space", should be encoded as
     * a plus sign rather than "%20". Note that this parameter is effectively
     * ignored if 0x20 is in safeOctets.
     *
     * @return the encoded version of {@code s}.  Will return {@code s}
     * itself if no encoding is necessary.
     *
     * @throws UnsupportedEncodingException if {@code encoding} is not supported.
     *
     * deprecated Use {@link com.google.gdata.util.common.base.CharEscapers#uriEscaper()}
     * or create an instance of {@link com.google.gdata.util.common.base.PercentEscaper}.
     * See {@link FastURLEncoder} for more details.
     */
    public static String encode(final String s, final String encoding,
            BitSet safeOctets, boolean plusForSpace)
            throws UnsupportedEncodingException {
        StringBuilder out = new StringBuilder(s.length() * 2);
        boolean needsEncoding;
        try {
            needsEncoding = encode(s, encoding, safeOctets, plusForSpace, out);
        } catch (UnsupportedEncodingException e) {
            throw e;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        if (needsEncoding) {
            return out.toString();
        } else {
            return s;
        }
    }

    /**
     * URL-escapes s by encoding it with the specified character encoding,
     * escaping all octets not included in safeOctets, and then outputting
     * the result to an Appendable.
     *
     * @param s String to encode.
     * @param encoding character encoding to use (e.g., "UTF-8")
     * @param safeOctets set of octets that should not be escaped.
     * @param plusForSpace whether octet 0x20, i.e. "space", should be encoded as
     * a plus sign rather than "%20". Note that this parameter is effectively
     * ignored if 0x20 is in safeOctets.
     * @param out the Appendable destination for the encoded string.
     *
     * @return true if {@code s} did need escaping, false otherwise.  In
     * other words, this returns false only if {@code s} was output to
     * {@code out} verbatim.
     *
     * @throws UnsupportedEncodingException if {@code encoding} is not supported.
     * @throws IOException if {@code out} does so when appended to.
     *
     * deprecated Use {@link com.google.gdata.util.common.base.CharEscapers#uriEscaper()}
     * or create an instance of {@link com.google.gdata.util.common.base.PercentEscaper}.
     * See {@link FastURLEncoder} for more details.
     */
    public static boolean encode(final String s, final String encoding,
            BitSet safeOctets, boolean plusForSpace,
            Appendable out)
            throws UnsupportedEncodingException, IOException {

        byte[] data = s.getBytes(encoding);
        boolean containsSpace = false;
        int outputLength = 0;

        for (int i = 0; i < data.length; i++) {
            int c = data[i];
            if (c < 0) {
                c += 256;  // convert from [-128, 127] to [0, 255]
            }
            if (safeOctets.get(c)) {
                out.append((char) c);
                outputLength += 1;
            } else if (plusForSpace && (c == ' ')) {
                containsSpace = true;
                out.append('+');
                outputLength += 1;
            } else {
                out.append('%');
                out.append(HEX_DIGITS[c >> 4]);
                out.append(HEX_DIGITS[c & 0xf]);
                outputLength += 3;
            }
        }

        return containsSpace || (outputLength != s.length());
    }

    /**
     * This should be a direct replacement for java.net.URLEncoder.encode().
     * @see java.net.URLEncoder#encode(String, String)
     * @param s String to encode.
     * @param encoding character encoding to use (e.g., "UTF-8")
     *
     * deprecated Use {@link com.google.gdata.util.common.base.CharEscapers#uriEscaper()}.
     * See {@link FastURLEncoder} for more details.
     */
    public static String encode(String s, String encoding)
            throws UnsupportedEncodingException {
        String result = encode(s, encoding, DEFAULT_SAFE_OCTETS, true);
        if (verifyAgainstJava) {
            String jresult = URLEncoder.encode(s, encoding);
            if (!jresult.equals(result)) {
                IoUtils.log.severe("FastURLEncoder does not match java. Java: '" + jresult + "'  FastURLEncoder: '" + result + "'");
                return jresult;
            }
        }


        return result;
    }

    /**
     * This should be a direct replacement for java.net.URLEncoder.encode(),
     * but appends its output to an Appendable.
     *
     * @see java.net.URLEncoder#encode(String, String)
     * @param s String to encode.
     * @param encoding character encoding to use (e.g., "UTF-8")
     * @param out the Appendable destination for the encoded string.
     *
     * @throws UnsupportedEncodingException if {@code encoding} is not supported.
     * @throws IOException if {@code out} does so when appended to.
     *
     * deprecated Use {@link com.google.gdata.util.common.base.CharEscapers#uriEscaper()}.
     * See {@link FastURLEncoder} for more details.
     */
    public static void encode(String s, String encoding, Appendable out)
            throws UnsupportedEncodingException, IOException {

        /*
         * Note that this method never compares its result with Java's
         * encoding; it does not not respect the verifyAgainstJava value
         */
        encode(s, encoding, DEFAULT_SAFE_OCTETS, true, out);
    }

    /**
     * Shortcut for encode(s, "UTF-8").
     * This is very similiar to java.net.URLEncoder.encode() except that it
     * uses UTF-8 instead of the platform's default encoding.
     * @see java.net.URLEncoder#encode(String)
     * @param s String to encode.
     *
     * deprecated Use {@link com.google.gdata.util.common.base.CharEscapers#uriEscaper()}.
     */
    public static String encode(String s) {
        try {
            return encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Shortcut for encode(s, "UTF-8", out).
     * This is very similiar to java.net.URLEncoder.encode() except that it
     * uses UTF-8 instead of the platform's default encoding.
     * @see java.net.URLEncoder#encode(String)
     * @param s String to encode.
     * @param out the Appendable destination for the encoded string.
     *
     * deprecated Use {@link com.google.gdata.util.common.base.CharEscapers#uriEscaper()}.
     * See {@link FastURLEncoder} for more details.
     */
    public static void encode(String s, Appendable out) throws IOException {
        try {

            /*
             * Note that this method never compares its result with Java's
             * encoding; it does not not respect the verifyAgainstJava value
             */
            encode(s, "UTF-8", out);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Shortcut for encode(s, "UTF-8").
     * This is very similiar to java.net.URLEncoder.encode() except that it
     * uses UTF-8 instead of the platform's default encoding.
     * @see java.net.URLEncoder#encode(String)
     * @param s String to encode.
     * @param safeOctets set of octets that should not be escaped.
     * @param plusForSpace whether octet 0x20, i.e., "space", should be encoded as
     * a plus sign rather than "%20". Note that this parameter is effectively
     * ignored if 0x20 is in safeOctets.
     *
     * deprecated Use {@link com.google.gdata.util.common.base.CharEscapers#uriEscaper()}.
     * or create an instance of {@link com.google.gdata.util.common.base.PercentEscaper}.
     * See {@link FastURLEncoder} for more details.
     */
    public static String encode(String s, BitSet safeOctets,
            boolean plusForSpace) {
        try {
            return encode(s, "UTF-8", safeOctets, plusForSpace);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
    /** java.net.URLEncoder uses upper-case hex digits so we should too. */
    private static final char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F'};
    /**
     * These octets all go directly into the URL, all others are escaped.
     */
    private static final BitSet DEFAULT_SAFE_OCTETS = new BitSet(256);

    static {
        // These characters are specified as unreservered in RFC 2396:
        //   "-", "_", ".", "!", "~", "*", "'", "(", ")",
        //   "0".."9", "A".."Z", "a".."z"
        // But wait... Java also escapes !, ~, ', (, and )
        // I'm only going to include -, _, ., and * to be consistent with java

        for (int i = '0'; i <= '9'; i++) {
            DEFAULT_SAFE_OCTETS.set(i);
        }

        for (int i = 'A'; i <= 'Z'; i++) {
            DEFAULT_SAFE_OCTETS.set(i);
        }

        for (int i = 'a'; i <= 'z'; i++) {
            DEFAULT_SAFE_OCTETS.set(i);
        }

        DEFAULT_SAFE_OCTETS.set('-');
        DEFAULT_SAFE_OCTETS.set('_');
        DEFAULT_SAFE_OCTETS.set('.');
        DEFAULT_SAFE_OCTETS.set('*');
    }
    /**
     * These octets mimic the ones escaped by the C++ webutil/url URL class --
     * the kGoogle1Escape set.
     * To produce the same escaping as C++, use this BitSet with the plusForSpace
     * option.
     *
     * deprecated Use {@link com.google.gdata.util.common.base.CharEscapers#cppUriEscaper()}
     */
    public static final BitSet CPLUSPLUS_COMPAT_SAFE_OCTETS = new BitSet(256);

    static {
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set('!');
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set(')');
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set('(');
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set('*');
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set(',');
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set('-');
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set('.');
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set('/');
        for (int i = '0'; i <= '9'; i++) {
            CPLUSPLUS_COMPAT_SAFE_OCTETS.set(i);
        }
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set(':');
        for (int i = 'A'; i <= 'Z'; i++) {
            CPLUSPLUS_COMPAT_SAFE_OCTETS.set(i);
        }
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set('_');
        for (int i = 'a'; i <= 'z'; i++) {
            CPLUSPLUS_COMPAT_SAFE_OCTETS.set(i);
        }
        CPLUSPLUS_COMPAT_SAFE_OCTETS.set('~');
    }

    /**
     * @return a BitSet suitable for passing to
     * {@link #encode(String,String,BitSet,boolean)} or
     * {@link #encode(String,BitSet,boolean)}. It defaults to containing the
     * octets that would not be escaped by
     * {@link java.net.URLEncoder#encode(String)}. Callers can edit the
     * result for specialized purposes.
     */
    public static BitSet createSafeOctetBitSet() {
        return (BitSet) DEFAULT_SAFE_OCTETS.clone();
    }
}
