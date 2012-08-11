package i3.parser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import static i3.io.IoUtils.*;

public final class TxtLoader extends BookLoader {

    private EditorKit txtEditor;
    private static Set<String> acceptedTypes = Collections.singleton("txt");

    @Override
    protected boolean accepts(String file) {
        if (file == null) {
            return false;
        }
        String name = file.toLowerCase(Locale.ENGLISH);
        return name.endsWith(".txt");
    }

    @Override
    protected boolean acceptsMimeType(String mime) {
        return "text/plain".equals(mime);
    }

    @Override
    public Set<String> supportedExtensions() {
        return acceptedTypes;
    }

    @Override
    public StyledDocument create(InputStream reader, Map<Property, Object> properties) throws IOException {
        try {
            return parseTXT(reader, properties);
        } catch (BadLocationException io) {
            throw new AssertionError("BadLocationException should never happen", io);
        }
    }

    @Override
    public StyledDocument create(URL origin, Map<Property, Object> properties) throws IOException {
        try (InputStream in = origin.openStream()) {
            return parseTXT(in, properties);
        } catch (BadLocationException ex) {
            throw new AssertionError("BadLocationException should never happen", ex);
        }
    }

    private StyledDocument parseTXT(final InputStream reader, Map<Property, Object> properties) throws IOException, BadLocationException {
        if (txtEditor == null) {
            txtEditor = new DefaultEditorKit();
        }
        //text needs a charset detector too.
        BufferedInputStream stream = new BufferedInputStream(reader);
        stream.mark(15000);
        String charset = probeCharset(stream, 15000);
        stream.reset();
        DefaultStyledDocument newDoc = new ParserDocument();

        ParserListener l = (ParserListener) properties.get(Property.PARSER_LISTENER);
        if (l != null) {
            l.startDocument(newDoc);
        }
        if (charset == null) {
            txtEditor.read(stream, newDoc, 0);
        } else {
            txtEditor.read(new InputStreamReader(stream, charset), newDoc, 0);
        }
        if (l != null) {
            l.endDocument(newDoc);
        }
        Boolean reparse = (Boolean) properties.get(Property.REFORMAT);
        reparse = reparse == null ? Boolean.FALSE : Boolean.TRUE;
        return (reparse) ? reParser.reParse(newDoc) : newDoc;
    }
}
