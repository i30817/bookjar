package i3.parser;

import i3.decompress.Content;
import i3.decompress.FileView;
import i3.decompress.Selector;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.text.StyledDocument;

public final class CompressedLoader extends BookLoader {

    @Override
    protected boolean accepts(String file) {
        return Selector.acceptsFile(file);
    }

    @Override
    protected boolean acceptsMimeType(String file) {
        return false;
    }

    @Override
    public Set<String> supportedExtensions() {
        //not really a text file innit.
        return Collections.emptySet();
    }

    @Override
    public StyledDocument create(URL origin, Map<Property,Object> properties) throws IOException {
        //may have to download...
        try (Selector archive = Selector.from(origin)){
            //can't read, ignore
            if (archive == null) {
                throw new IOException("Can't read " + origin.toString() + " as compressed File");
            }
            //find supported formats inside the file
            StringBuilder regex = new StringBuilder();
            Iterator<String> extensions = BookLoader.allSupportedExtensions().iterator();

            if (extensions.hasNext()) {
                regex.append("(?:.*\\.").append(extensions.next()).append("$)");
            }

            while (extensions.hasNext()) {
                regex.append("|(?:.*\\.").append(extensions.next()).append("$)");
            }

            archive.selectByRegex(regex.toString(), Pattern.CASE_INSENSITIVE);
            FileView fview = archive.getSelectedMax(Content.FileSize);
            if (fview == null) {
                throw new IOException("No parseable file in compressed file");
            }
            properties.put(Property.EXTRACTED_FILENAME, fview.getFileName());
            BookLoader loader = BookLoader.forFileName(fview.getFileName());
            return loader.create(fview.getInputStream(), properties);
        }
    }

    @Override
    public StyledDocument create(InputStream reader, Map<Property,Object> properties) throws IOException {
        throw new UnsupportedOperationException("Compressed Files can't support reading from inputStream yet.");
    }
}
