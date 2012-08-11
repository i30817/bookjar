/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package i3.decompress;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipException;

/**
 *
 * @author microbiologia
 */
public class ZipExtractorProvider implements ExtractorProvider {

    public ZipExtractor create(Path fileToExtract) throws ZipException, IOException {
        return new ZipExtractor(fileToExtract);
    }

    public boolean acceptFile(String filename) {
        if (filename == null) {
            return false;
        }
        filename = filename.toLowerCase(Locale.ENGLISH);
        return filename.endsWith(".zip") || filename.endsWith(".htmlz");
    }
}
