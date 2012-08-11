package i3.download;

import i3.swing.component.ModelObject;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.ListCellRenderer;

/**
 * A download view implements ListCellRenderer.
 *
 */
public abstract class DownloadView<E> implements ListCellRenderer<ModelObject<E>> {

    private final JComponent renderer = Box.createVerticalBox();
    private final static DecimalFormat totalFormat = new DecimalFormat("0.##");
    private final static DecimalFormat currentFormat = new DecimalFormat("0.00");
    private final static long bytesInMegaByte = 1048576;
    private final JProgressBar progress = new StampProgressBar();
    private final JLabel name = new JLabel();
    private final JLabel text = new JLabel();
    private boolean showName;
    private boolean showIcon;
    private boolean showProgress;

    static {
        currentFormat.setGroupingUsed(false);
        totalFormat.setGroupingUsed(false);
    }

    public DownloadView() {
        int gap = text.getIconTextGap();
        text.setBorder(BorderFactory.createEmptyBorder(0, gap, 0, gap));

        Font font = text.getFont();
        font = font.deriveFont((float) (font.getSize() - 1));
        text.setFont(font);

        progress.setBorder(BorderFactory.createEmptyBorder(0, gap, 0, gap));
        progress.setBorderPainted(false);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.setIndeterminate(false);
    }

    /**
     * Implementations should call this in the
     * constructor after super()
     */
    protected final void init(boolean showName, boolean showIcon, boolean showProgress) {
        this.showName = showName;
        this.showIcon = showIcon;
        this.showProgress = showProgress;

        if (showIcon || showName) {
            renderer.add(name);
        }
        if (showProgress) {
            renderer.add(text);
        }
        renderer.setAlignmentX(Component.LEFT_ALIGNMENT);
        renderer.setAlignmentY(Component.CENTER_ALIGNMENT);
    }

    /**
     * Already implemented CellRendererComponent based on download
     * state and constructor booleans here. Of course you can reimplement
     * if you want to (but why?).
     * @param list
     * @param value
     * @param index
     * @param isSelected
     * @param cellHasFocus
     * @return not null
     */
    @Override
    public Component getListCellRendererComponent(JList<? extends ModelObject<E>> list, ModelObject<E> value, int index, boolean isSelected, boolean cellHasFocus) {
        DownloadState current = value.download.getCurrentDownloadState();
        E realValue = value.value;
        Icon iconValue = getIcon(realValue, current);
        if (showIcon) {
            name.setIcon(iconValue);
        }
        if (showName) {
            name.setText(realValue.toString());
        }
        if (showProgress) {
            text.setText(getProgressDescription(realValue, current));
        }

        if (current.isDone() || current.isCancelled()) {
            renderer.remove(progress);
        } else {
            renderer.add(progress);
            boolean indeterminateProgress = !current.isRemoteSizeKnown();
            if (indeterminateProgress && showProgress) {
                progress.setValue(0);
            } else if (showProgress) {
                progress.setValue(current.getProgress());
            }
        }

        renderer.setOpaque(isSelected);
        if (isSelected) {
            Color bg = list.getSelectionBackground();
            Color fg = list.getSelectionForeground();
            setColors(bg, fg);
        } else {
            Color bg = list.getBackground();
            Color fg = list.getForeground();
            setColors(bg, fg);
        }

        return renderer;
    }

    private void setColors(Color bg, Color fg) {
        renderer.setBackground(bg);
        renderer.setForeground(fg);
        name.setBackground(bg);
        name.setForeground(fg);
        text.setBackground(bg);
        text.setForeground(fg);
    }

    /**
     * Already implemented progress description
     * returns if the file has unknown size, is cancelled
     * or the remaining time.
     * @param the value added to the list
     * @param state from the download that needs a description
     * @return not null
     */
    public String getProgressDescription(E value, DownloadState state) {
        if (state.isCancelled()) {
            Exception exc = state.getError();
            if (exc == null || exc.getLocalizedMessage() == null) {
                return "Cancelled";
            } else {
                return "Cancelled: " + exc.getLocalizedMessage();
            }
        }

        if (!state.isRemoteSizeKnown()) {
            return "Unknown size";
        }

        long expectedMegaBytes = Math.round(state.getExpectedSize() / bytesInMegaByte);
        if (state.isDone()) {
            return totalFormat.format(expectedMegaBytes) + " MB";
        } else {
            long readMegaBytes = Math.round(state.getBytesRead() / bytesInMegaByte);
            long remainingTime = state.getTimeRemaining();
            return currentFormat.format(readMegaBytes) + " MB of " + totalFormat.format(expectedMegaBytes) + " - remaining time " + HumanTime.approximately(remainingTime);
        }
    }

    /**
     * Get the tooltip text for the cell rendered by the List
     * that will use this view.
     * @param the value added to the list
     * @param state from the Download that needs a tooltip.
     */
    public abstract String getTooltipText(E value, DownloadState state);

    /**
     * Each implementation should return a appropriate height icon
     * for the downloadView accepted in DownloadViewProvider
     * (you can vary it according to download state, but for
     * gods sake cache the icon).
     * @param the value added to the list
     * @param state from download that needs a icon
     * @return not null
     */
    public abstract Icon getIcon(E value, DownloadState state);

    /**
     * Attempt to open a complete download (only, no need to check)
     * @param value
     * @param state
     */
    public abstract void open(E value, DownloadState state);

    /**
     * Finished a download.
     * @param value
     * @param state
     */
    public abstract void finished(E value, DownloadState state);

    /**
     * The height so that the icon fits in the view
     * This method serves to configure the JList
     * -1 is the default
     * @return -1 to Short.MAX_VALUE && >= icon height
     */
    public int getFixedCellHeight() {
        return -1;
    }

    /**
     * The width so that the icon fits in the view
     * This method serves to configure the JList
     * -1 is the default
     * @return -1 to Short.MAX_VALUE && >= icon width
     */
    public int getFixedCellWidth() {
        return -1;
    }

    private static class StampProgressBar extends JProgressBar {

        @Override
        public void repaint() {
        }

        @Override
        public void validate() {
        }

        @Override
        public void revalidate() {
        }

        @Override
        public void invalidate() {
        }

        @Override
        public void repaint(long tm, int x, int y, int width, int height) {
        }

        @Override
        public void repaint(Rectangle r) {
        }
    }
}
