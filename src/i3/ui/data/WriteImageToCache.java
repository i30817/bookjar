package i3.ui.data;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import i3.main.Bookjar;
import i3.io.IoUtils;

/**
 *
 * @author Owner
 */
public final class WriteImageToCache {

    static final String bestFormat;
    static final boolean turnedOff;
    private final String key;
    private final static Path imagesDir = Bookjar.programLocation.resolve("images");

    static {
        //chose best format with a reader and writer
        ImageIO.setUseCache(false);
        boolean writer = false, reader = false;
        for (String f : ImageIO.getReaderFileSuffixes()) {
            if ("jp2".equals(f)) {
                reader = true;
                break;
            }
        }

        for (String f : ImageIO.getWriterFileSuffixes()) {
            if ("jp2".equals(f)) {
                writer = true;
                break;
            }
        }
        bestFormat = writer && reader ? "jp2" : "jpg";
        turnedOff = !IoUtils.validateOrCreateDir(imagesDir, "Image dir not writable, not saving images...");
    }

    public WriteImageToCache(String name) {
        key = name;
    }

    public void write(RenderedImage arg) throws Exception {
        if (turnedOff) {
            return;
        }

        Path cached = IoUtils.getSafeFileSystemFile(imagesDir, key + "." + bestFormat);
        try {
            ImageIO.write(arg, bestFormat, cached.toFile());
        } catch (IOException t) {
            throw new IllegalArgumentException("Trying to write image from file \"" + key + "\" failed, cause: " + t.getMessage());
        }
    }
}
