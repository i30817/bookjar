package i3.ui.data;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.jdesktop.swingx.util.GraphicsUtilities;

/**
 * Upscales or downscales a image to desired dimensions.
 * @author Owner
 */
public class ScaleImage {

    int desiredWidth;
    int desiredHeight;

    public ScaleImage(int scaleW, int scaleH) {
        super();
        this.desiredWidth = scaleW;
        this.desiredHeight = scaleH;
    }

    public BufferedImage scale(BufferedImage workImage) throws Exception {
        float widthRatio = (desiredWidth * 1.0F) / workImage.getWidth();
        float heightRatio = (desiredHeight * 1.0F) / workImage.getHeight();
        float ratio = (widthRatio < heightRatio) ? widthRatio : heightRatio;
        int newWidth = (int) (ratio * workImage.getWidth());
        int newHeight = (int) (ratio * workImage.getHeight());
        BufferedImage imageScaled;
        if (workImage.getWidth() > desiredWidth || workImage.getHeight() > desiredHeight) {
            imageScaled = GraphicsUtilities.createThumbnail(workImage, newWidth, newHeight);
        } else {
            //upscale
            imageScaled = GraphicsUtilities.createCompatibleImage(workImage, newWidth, newHeight);
            Graphics2D g = imageScaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(workImage, 0, 0, newWidth, newHeight, null);
        }
        workImage.getGraphics().dispose();
        return imageScaled;
    }
}
