package i3.swing.component;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

/**
 *
 * @author microbiologia
 */
public final class EmptyIcon implements Icon {

    private final int width;
    private final int height;

    public EmptyIcon(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
    }

    public int getIconWidth() {
        return width;
    }

    public int getIconHeight() {
        return height;
    }
}
