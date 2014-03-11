package i3.ui.data;

import i3.io.IoUtils;
import i3.main.Bookjar;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

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

    public void write(BufferedImage arg) throws Exception {
        if (turnedOff) {
            return;
        }
        boolean hasAlpha = arg.getColorModel().hasAlpha();
        String format = bestFormat;
        //was originally png or similar, save it as png, since jpg doesn't support transparency
        if (hasAlpha && format.equals("jpg")) {
            format = "png";
        }
        //no extension whatever the format, trust the headers
        //strip extensions not to confuse filemanagers
        int last = key.lastIndexOf('.');
        String imageName = last == -1 ? key : key.substring(0, last);
        Path cached = IoUtils.getSafeFileSystemFile(imagesDir, imageName);
        try {
            ImageIO.write(arg, format, cached.toFile());
        } catch (IOException t) {
            throw new IllegalArgumentException("failed to write image of type(" + arg.getType() + ") to file \"" + cached + "\"", t);
        }
    }
}
