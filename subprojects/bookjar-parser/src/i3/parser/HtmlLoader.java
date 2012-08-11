package i3.parser;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import i3.util.Strings;
import static i3.io.IoUtils.*;

/**
 * Class responsible for creating standardized
 * styleddocuments from various datasources.
 * @author Paulo
 */
public final class HtmlLoader extends BookLoader {

    private static Set<String> types = new HashSet<>();

    static {
        types.add("htm");
        types.add("html");
        types.add("shtml");
        types.add("asp");
        types.add("jsp");
        types.add("jspa");
        types.add("php");
        types.add("pl");
        types.add("cgi");
    }

    @Override
    protected boolean acceptsMimeType(String mime) {
        return "text/html".equals(mime);
    }

    @Override
    protected boolean accepts(String file) {
        if (file == null) {
            return false;
        }

        String name = file.toLowerCase(Locale.ENGLISH);
        return name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".shtml") || name.endsWith(".asp") || name.endsWith(".jsp") || name.endsWith(".jspa") || name.endsWith(".php") || name.endsWith(".pl") || name.endsWith(".cgi");
    }

    @Override
    public Set<String> supportedExtensions() {
        return types;
    }

    private StyledDocument create(InputStream input, String filename, Map<Property, Object> properties) throws IOException {
        try (
                BufferedInputStream stream = new BufferedInputStream(input);
                Reader inputReader = new InputStreamReader(stream, findHtmlCharset(stream, 20000, "windows-1252"));) {
            return parseHTML(inputReader, filename, properties);
        }
    }

    @Override
    public StyledDocument create(final InputStream input, Map<Property, Object> properties) throws IOException {
        return create(input, System.getProperty("user.dir"), properties);
    }

    @Override
    public StyledDocument create(URL origin, Map<Property, Object> properties) throws IOException {
        return create(origin.openStream(), getName(origin), properties);
    }
    private XMLReader parser;

    private StyledDocument parseHTML(Reader reader, String filename, Map properties) throws IOException {
//        long time = System.currentTimeMillis();
        if (parser == null) {
            parser = new Parser();
        }
        Color hyperLinkColor = (Color) properties.get(Property.HYPERLINK_COLOR);
        filename = Strings.subStringAfterLast(filename, '\\');
        DefaultStyledDocument doc = new ParserDocument();
        doc.putProperty("filename", filename);
        HTMLCallBack call = new HTMLCallBack(doc, hyperLinkColor);
        parser.setContentHandler(call);
        DefaultStyledDocument docOut = call.getDocument();
        try {
            ParserListener l = (ParserListener) properties.get(Property.PARSER_LISTENER);
            if (l != null) {
                l.startDocument(docOut);
                parser.parse(new InputSource(reader));
                l.endDocument(docOut);
            } else {
                parser.parse(new InputSource(reader));
            }
        } catch (SAXException se) {
            throw new IOException(se);
        }
        Boolean reparse = (Boolean) properties.get(Property.REFORMAT);
        reparse = reparse == null ? Boolean.FALSE : Boolean.TRUE;
        return (reparse) ? reParser.reParse(docOut) : docOut;
//        System.out.println(System.currentTimeMillis() - time);
    }
}
