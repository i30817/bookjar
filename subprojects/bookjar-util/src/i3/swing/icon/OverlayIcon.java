package i3.swing.icon;

import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;

public class OverlayIcon implements Icon {

    private List<Icon> icons = new ArrayList<>();
    private int spaceSize = 2;

    public OverlayIcon(Icon... icons) {
        for (int i = 0; i < icons.length; i++) {
            this.icons.add(icons[i]);
        }
    }

    @Override
    public int getIconHeight() {
        int result = 0;
        for (Icon icon : getIcons()) {
            result = Math.max(result, icon.getIconHeight());
        }
        return result;
    }

    @Override
    public int getIconWidth() {
        int result = 0;
        for (Icon icon : getIcons()) {
            result = Math.max(result, icon.getIconWidth());
        }
        return result;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        int h = getIconHeight();
        int w = getIconWidth();

        for (Icon icon : getIcons()) {
            icon.paintIcon(c, g, x + (w - icon.getIconWidth()) / 2, y + (h - icon.getIconHeight()) / 2);
        }
    }

    public int getSpaceSize() {
        return spaceSize;
    }

    public void setSpaceSize(int spaceSize) {
        this.spaceSize = spaceSize;
    }

    public void add(Icon icon) {
        icons.add(icon);
    }

    public List<Icon> getIcons() {
        return icons;
    }
}
