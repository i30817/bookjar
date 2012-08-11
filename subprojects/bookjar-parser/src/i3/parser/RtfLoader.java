package i3.parser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

public final class RtfLoader extends BookLoader {

    private EditorKit rtfEditor;
    private static Set<String> acceptedTypes = Collections.singleton("rtf");

    @Override
    protected boolean accepts(String file) {
        if (file == null) {
            return false;
        }
        String name = file.toLowerCase(Locale.ENGLISH);
        return name.endsWith(".rtf");
    }

    @Override
    protected boolean acceptsMimeType(String mime) {
        return "text/rtf".equals(mime) || "text/richtext".equals(mime) || "application/rtf".equals(mime);
    }

    @Override
    public Set<String> supportedExtensions() {
        return acceptedTypes;
    }

    @Override
    public StyledDocument create(InputStream reader, Map<Property, Object> properties) throws IOException {
        try {
            return parseRTF(reader, properties);
        } catch (BadLocationException ex) {
            throw new AssertionError("BadLocationException should never happen", ex);
        }
    }

    @Override
    public StyledDocument create(URL origin, Map<Property, Object> properties) throws IOException {
        try (InputStream inRTF = new BufferedInputStream(origin.openStream(), 1200)) {
            return parseRTF(inRTF, properties);
        } catch (BadLocationException ex) {
            throw new AssertionError("BadLocationException should never happen", ex);
        }
    }

    private StyledDocument parseRTF(final InputStream inRtf, Map<Property, Object> properties) throws IOException, BadLocationException {
        if (rtfEditor == null) {
            rtfEditor = new RTFEditorKit();
            addUnimplementedTagByReflection();
        }


        DefaultStyledDocument toParse = new ParserDocument();

        ParserListener l = (ParserListener) properties.get(Property.PARSER_LISTENER);
        if (l != null) {
            l.startDocument(toParse);
            rtfEditor.read(inRtf, toParse, 0);
            l.endDocument(toParse);
        } else {
            rtfEditor.read(inRtf, toParse, 0);
        }
        Boolean reparse = (Boolean) properties.get(Property.REFORMAT);
        reparse = reparse == null ? Boolean.FALSE : Boolean.TRUE;
        return (reparse) ? reParser.reParse(toParse) : toParse;
    }

    /**
     * A missing tag in the java rtf support is added here.
     */
    @SuppressWarnings({"unchecked"})
    private void addUnimplementedTagByReflection() {
        try {
            Class rtfReaderClass = Class.forName("javax.swing.text.rtf.RTFReader");
            Field field = rtfReaderClass.getDeclaredField("textKeywords");
            field.setAccessible(true);
            //static, can be null
            Dictionary<String, String> table = (Dictionary<String, String>) field.get(null);
            table.put("line", "\n");
        } catch (Exception e) {
            BookLoader.log.warning("Couldn't reflect field to fix rtf \\line bug");
        }
    }
}
