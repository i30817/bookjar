package i3.ui.data;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import i3.main.Bookjar;
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
        //no extension whatever the format, trust the headers
        //strip extensions not to confuse filemanagers
        int last = key.lastIndexOf('.');
        String imageName = last == -1 ? key : key.substring(0, last);
        Path cached = imagesDir.resolve(imageName);
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
