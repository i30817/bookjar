/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package i3.decompress;

import java.nio.file.Path;

/**
 *
 * @author microbiologia
 */
public interface ExtractorProvider {

    boolean acceptFile(String filename);

    Extractor create(Path fileToExtract) throws Exception;
}
