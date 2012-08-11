/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package i3.decompress;

import java.nio.file.Path;
import java.util.Locale;

/**
 *
 * @author microbiologia
 */
public class RarExtractorProvider implements ExtractorProvider {

    public boolean acceptFile(String filename) {
        if (filename == null) {
            return false;
        }
        filename = filename.toLowerCase(Locale.ENGLISH);
        return filename.endsWith(".rar");
    }

    public Extractor create(Path fileToExtract) throws Exception {
        return new RarExtractor(fileToExtract);
    }
}
