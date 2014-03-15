package i3.ui.data;

import i3.decompress.Content;
import i3.decompress.FileView;
import i3.decompress.Selector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import i3.io.IoUtils;

/**
 * Read 1 image from a compressed archive, using various heuristics to choose
 * the image
 *
 * @author Owner
 */
public final class ReadImageFromArchive {

    private final Path file;
    private static final Pattern fuzzy = Pattern.compile("(?:_| |')");

    static {
        ImageIO.setUseCache(false);
    }

    public ReadImageFromArchive(Path origin) {
        file = origin;
    }

    public BufferedImage read() throws Exception {
        InputStream imageInput = null;
        FileView imageFileView;
        Selector archive = null;
        try {
            archive = Selector.from(file);
            if (archive == null) {
                return null;
            }

            String regex = "(?:.*\\.jpg$)|(?:.*\\.jpeg$)|(?:.*\\.png$)|(?:.*\\.gif$)|(?:.*\\.bmp$)";
            archive.selectByRegex(regex, Pattern.CASE_INSENSITIVE);
            if (archive.selectedSize() == 0) {
                return null;
            }
            regex = "(?:.*rear.*)|(?:.*back.*)";
            //only png, gif etc; and not a back cover
            archive.limitSelector()
                    .selectByRegex(regex, Pattern.CASE_INSENSITIVE)
                    .limitSelectorInverse();

            if (archive.isEmpty()) {
                return null;
            } else {
                //things that can be covers by the name, ordered by least false positives.
                regex = "(?:.*fcover.*)|(?:.*front.*)|(?:.*cover.*)|(?:^fc\\..*)";
                archive.selectByRegex(regex, Pattern.CASE_INSENSITIVE);
                imageFileView = archive.getSelected();
            }

            if (imageFileView == null) {
                archive.selectAll();
                archive.orderByDescending(Content.NameSize);
                //introduce some fuzziness for this particular search
                //(remove missing or additional "_" or " " or "'")
                String archiveFileName = fuzzy.matcher(file.getFileName().toString().toLowerCase()).replaceAll("");
                for (FileView fv : archive) {
                    String fileName = fv.getFileName();
                    //disregard names too likely to occur in the archive name
                    if (likelyImageSubString(fileName)) {
                        continue;
                    }
                    fileName = fileName.substring(0, fileName.length() - 4);
                    fileName = fuzzy.matcher(fileName).replaceAll("").toLowerCase();
                    if (archiveFileName.contains(fileName)) {
                        imageFileView = fv;
                        break;
                    }
                }
            }
            if (imageFileView != null) {
                imageInput = imageFileView.getInputStream();
                BufferedImage image = ImageIO.read(imageInput);
                if (image != null && imageTooSmall(image)) {
                    image.getGraphics().dispose();
                    return null;
                }
                return image;
            }
        } finally {
            IoUtils.close(imageInput, archive);
        }
        //mark for subsequent invocations that there is no image in the archive
        return null;
    }

    private boolean likelyImageSubString(String fileName) {
        //count the extension (.jpg .png or .bmp)
        return fileName.length() < 8;
    }

    private boolean imageTooSmall(BufferedImage original) {
        return original.getHeight() < 40 || original.getWidth() < 40;
    }
}
