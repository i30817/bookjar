package i3.ui.data;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import i3.main.Bookjar;
import i3.io.IoUtils;
import org.apache.logging.log4j.LogManager;

/**
 *
 * @author Owner
 */
public class ReadImageFromCache {

    private final String key;
    private final static Path imagesDir = Bookjar.programLocation.resolve("images");

    public ReadImageFromCache(String name) {
        key = name;
    }

    /**
     * @return the cached file read from the file system if it exist or null.
     */
    public BufferedImage read() {
        if (WriteImageToCache.turnedOff) {
            return null;
        }
        //no extension for the cache files
        Path cached = IoUtils.getSafeFileSystemFile(imagesDir, key);
        try {
            if (Files.exists(cached)) {
                return ImageIO.read(cached.toFile());
            }
        } catch (IOException ex) {
            LogManager.getLogger().warn(ex);
        }
        return null;
    }
}
