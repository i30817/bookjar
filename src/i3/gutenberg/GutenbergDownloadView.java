package i3.gutenberg;

import i3.download.DownloadState;
import i3.download.DownloadView;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import i3.main.GutenbergBook;
import i3.main.LocalBook;
import i3.ui.Application;
import i3.ui.BookMarks;
import i3.io.IoUtils;
import i3.swing.icon.AnimatedIcon;
import i3.swing.icon.OverlayIcon;

/**
 * A view Provider for gutenberg text downloads
 * @author microbiologia
 */
public class GutenbergDownloadView extends DownloadView<GutenbergBook> {
    //images are 32 x 32
    private final static int ICON_HEIGHT = 36;
    private final static int ICON_WIDTH = 36;
    private final Icon loadingText, loadingHtml, loadingDocument;
    private final Icon percentageText, percentageHtml, percentageDocument;
    private final Icon cancelledText, cancelledHtml, cancelledDocument;
    private final Icon doneText, doneHtml, doneDocument;
    private final PercentageIcon percentageIcon = new PercentageIcon();

    public GutenbergDownloadView() {
        init(false, true, false);
        Icon textIcon = new ImageIcon(GutenbergDownloadView.class.getResource("text.png"));
        Icon htmlIcon = new ImageIcon(GutenbergDownloadView.class.getResource("html.png"));
        Icon documentIcon = new ImageIcon(GutenbergDownloadView.class.getResource("document.png"));
        percentageText = new OverlayIcon(textIcon, percentageIcon);
        percentageHtml = new OverlayIcon(htmlIcon, percentageIcon);
        percentageDocument = new OverlayIcon(documentIcon, percentageIcon);
        ImageIcon loadingIconNotAnimated = new ImageIcon(GutenbergDownloadView.class.getResource("loading.gif"));
        Icon loadingIcon = new AnimatedIcon(loadingIconNotAnimated);
        loadingText = new OverlayIcon(textIcon, loadingIcon);
        loadingHtml = new OverlayIcon(htmlIcon, loadingIcon);
        loadingDocument = new OverlayIcon(documentIcon, loadingIcon);
        Icon cancelledIcon = new ImageIcon(GutenbergDownloadView.class.getResource("cancel.png"));
        cancelledText = new OverlayIcon(textIcon, cancelledIcon);
        cancelledHtml = new OverlayIcon(htmlIcon, cancelledIcon);
        cancelledDocument = new OverlayIcon(documentIcon, cancelledIcon);
        Icon doneIcon = new ImageIcon(GutenbergDownloadView.class.getResource("done.png"));
        doneText = new OverlayIcon(textIcon, doneIcon);
        doneHtml = new OverlayIcon(htmlIcon, doneIcon);
        doneDocument = new OverlayIcon(documentIcon, doneIcon);
    }

    @Override
    public int getFixedCellWidth() {
        return ICON_WIDTH;
    }

    @Override
    public int getFixedCellHeight() {
        return ICON_HEIGHT;
    }

    @Override
    public String getTooltipText(GutenbergBook value, DownloadState element) {
        String mime = element.getMimeType();
        if (mime == null) {
            return "<html><body>" + element.getDownloadedFile().getFileName() + "<br></html></body>";
        }
        return "<html><body>" + element.getDownloadedFile().getFileName() + "<br>" + mime + "</html></body>";
    }

    @Override
    public Icon getIcon(GutenbergBook value, DownloadState state) {
        String mime = state.getMimeType();
        Icon loading;
        Icon percent;
        Icon cancelled;
        Icon done;
        switch (mime) {
            case "text/html":
                loading = loadingHtml;
                percent = percentageHtml;
                cancelled = cancelledHtml;
                done = doneHtml;
                break;
            case "text/plain":
                loading = loadingText;
                percent = percentageText;
                cancelled = cancelledText;
                done = doneText;
                break;
            default:
                loading = loadingDocument;
                percent = percentageDocument;
                cancelled = cancelledDocument;
                done = doneDocument;
                break;
        }

        if (state.isDone()) {
            return done;
        }

        if (state.isCancelled()) {
            return cancelled;
        }

        if (!state.isRemoteSizeKnown()) {
            return loading;
        }
        percentageIcon.setPercentage(state.getProgress());
        return percent;
    }

    @Override
    public void open(GutenbergBook value, DownloadState state) {
        Path fileToRead = state.getDownloadedFile();
        try {
            URL urlToRead = fileToRead.toUri().toURL();
            Application.app.toggleGutenbergList();
            Application.app.read(urlToRead);
        } catch (MalformedURLException ex) {
            throw new AssertionError("Impossible!", ex);
        }
    }

    @Override
    public void finished(GutenbergBook book, DownloadState state) {
        String language = book.getFirstLanguage();
        Path downloadedFile = state.getDownloadedFile();
        URL downloadedURL = IoUtils.toURL(downloadedFile);
        BookMarks bookMarks = Application.app.getBookMarks();
        bookMarks.put(downloadedURL, new LocalBook(downloadedFile, language, 0, 0F, true));
    }

    private static class PercentageIcon implements Icon {

        private Font font;
        private int height = Short.MAX_VALUE, width = Short.MAX_VALUE,
                percentage;

        public void setPercentage(int percentage) {
            this.percentage = percentage;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (font == null) {
                init(c, ICON_HEIGHT, ICON_WIDTH);
            }
            Graphics2D g2 = (Graphics2D) g.create();
            Color color = g2.getColor();
            g2.setColor(Color.RED);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            g2.translate(x, y + fm.getAscent());
            g2.drawString(Integer.toString(percentage) + "%", 2, 0);
            g2.setColor(color);
        }

        @Override
        public int getIconWidth() {
            return ICON_WIDTH;
        }

        @Override
        public int getIconHeight() {
            return ICON_HEIGHT;
        }

        private void init(Component iconParent, int height, int width) {
            Graphics g = iconParent.getGraphics();
            String max = "100%";
            font = iconParent.getFont().deriveFont(Font.BOLD);
            while (this.height > height || this.width > width) {
                font = font.deriveFont((float) font.getSize() - 1);
                FontMetrics metrics = g.getFontMetrics(font);
                this.height = metrics.getHeight() + 2;
                this.width = metrics.stringWidth(max) + 2;
            }
        }
    }
}

