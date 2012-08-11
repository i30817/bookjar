package i3.swing.component;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.JViewport;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import i3.image.GlowFilter;

/**
 * This image list requests images in
 * a interface that can be asynchronous.
 *
 * All images have a in memory cache in this
 * class, but only a limited number will be on
 * at a time, so you should use a secondary image
 * disk cache. When this needs a image it requests
 * it in RenderValues.getCellImage and you should
 * return it in ImageList.returnImage. If you don't,
 * it will never ask again. To request to try again
 * at a later time use ImageList.deferImage.
 *
 * All images are removed on removeNotify, and the not
 * displayed ones are regularly removed on a variable
 * number of requests relating to the screen
 * resolution (minimum) and requested again when needed.
 *
 * To ask if the list is at the right place to display
 * a Image for a Object use isObjectVisible()
 * (for async loading), no need to call it on the EDT.
 * @author Owner
 */
public final class ImageList<E> {

    private final JScrollPane layer = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private WrappedList list;
    private final RenderValues<E> update;
    //Only use this cache on the EDT. Even if synchronized it would
    //throw concurrent modification exceptions because iterators are used.
    //this map 'invisible' entries are cleared every MAX_IMAGES_REQUESTS in the renderer.
    //Thus, there is no memory leak per-se.
    private final Map<E, BufferedImage> memoryCache = new IdentityHashMap<>();

    /**
     * Use this to make the image list retry the request
     * for the Image for this value at a later date (inside a RenderValues impl)
     * if already requested and its if not needed now
     * @param valueAsKey
     * @throws IllegalArgumentException if valueAsKey is null.
     */
    public void deferImage(final E valueAsKey) {
        if (valueAsKey == null) {
            throw new IllegalArgumentException("Can't return a null argument");
        }
        if (SwingUtilities.isEventDispatchThread()) {
            memoryCache.remove(valueAsKey);
        } else {
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    memoryCache.remove(valueAsKey);
                }
            });
        }
    }

    /**
     * Use this to put a image into a cell of the list (inside a RenderValues impl).
     * @param valueAsKey
     * @param image
     * @throws IllegalArgumentException if a argument is null.
     */
    public void putImage(final E valueAsKey, final BufferedImage image) {
        if (image == null || valueAsKey == null) {
            throw new IllegalArgumentException("Can't return a null argument");
        }

        if (SwingUtilities.isEventDispatchThread()) {
            repaintForObject(valueAsKey, image);
            //see http://www.pushing-pixels.org/?p=369
        } else if (list.isVisible() && list.isShowing()) {
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    repaintForObject(valueAsKey, image);
                }
            });
        }
    }

    private void repaintForObject(final E valueAsKey, final BufferedImage image) {
        memoryCache.put(valueAsKey, image);
        if (innerIsObjectVisible(valueAsKey)) {
            layer.repaint();
        }
    }

    /**
     * @return the object at a given ImageList point, if any. Otherwise return null.
     */
    public E locationToObject(Point location) {
        assert SwingUtilities.isEventDispatchThread() : "Not in EDT";
        int index = list.locationToIndex(location);
        if (index >= 0) {
            return list.getModel().getElementAt(index);
        }

        return null;
    }

    /**
     * @return the objects that are currently selected
     * return a empty array if nothing is selected
     */
    public List<E> getSelectedObjects() {
        assert SwingUtilities.isEventDispatchThread() : "Not in EDT";
        return list.getSelectedValuesList();
    }

    public void ensureSelectedIsVisible(){
        assert SwingUtilities.isEventDispatchThread() : "Not in EDT";
        int i = list.getSelectedIndex();
        if(i != -1){
            list.ensureIndexIsVisible(i);
        }
    }

    /**
     * {@link  javax.swing.JList#addMouseListener(MouseListener listener) view class method}
     */
    public void addMouseListener(MouseListener listener) {
        assert SwingUtilities.isEventDispatchThread() : "Not in EDT";
        list.addMouseListener(listener);
    }

    /**
     * {@link  javax.swing.JList#removeMouseListener(MouseListener listener) view class method}
     */
    public void removeMouseListener(MouseListener listener) {
        assert SwingUtilities.isEventDispatchThread() : "Not in EDT";
        list.removeMouseListener(listener);
    }

    /**
     * {@link  javax.swing.JList#getInputMap() view class method}
     */
    public InputMap getInputMap() {
        assert SwingUtilities.isEventDispatchThread() : "Not in EDT";
        return list.getInputMap();
    }

    /**
     * {@link  javax.swing.JList#getActionMap() view class method}
     */
    public ActionMap getActionMap() {
        assert SwingUtilities.isEventDispatchThread() : "Not in EDT";
        return list.getActionMap();
    }

    /**
     * Returns if the list object is not visible in the viewport
     * @param obj
     * @return
     */
    public boolean isObjectInvisible(final E obj) {
        if (EventQueue.isDispatchThread()) {
            return !innerIsObjectVisible(obj);
        }

        FutureTask<Boolean> result = new FutureTask<>(new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                return !innerIsObjectVisible(obj);
            }
        });
        EventQueue.invokeLater(result);
        try {
            return result.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new AssertionError("Impossible!", ex);
        }
    }

    private boolean innerIsObjectVisible(E obj) {
        int first = list.getFirstVisibleIndex();
        if (first > -1) {
            ListModel<E> model = list.getModel();
            int last = list.getLastVisibleIndex();
            for (int i = first; i <= last; i++) {
                if (model.getElementAt(i).equals(obj)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * As images are a heavyweight object ImageList has a lazy
     * strategy to create and dispose them after use. To implement
     * this optimally, the getCellImage method can offload image
     * creation to another thread, if it needs to be loaded -
     * images are returned by calling ImageList.returnImage(Object value, Image).
     * If you don't call it, images are assumed not to exist for the index,
     * as if you call it with a null image.
     */
    public interface RenderValues<E> {

        /**
         * Given a list object value and desired width and height give a
         * appropriate image. This method has to call returnImage(Object value, Image)
         * (can be called in another thread). If you offload image loading
         * to a thread call returnImage in the thread after loading.
         * If you don't want to offload image loading read the image and then call
         * returnImage directly.
         *
         * @param obj not null
         * @param imageWidth
         * @param imageHeight
         */
        void requestCellImage(ImageList<E> list, E obj, int imageWidth, int imageHeight);

        /**
         * Given a list object and desired width and height give a
         * appropriate cell text.
         * @param obj not null
         * @return cell text
         */
        String getCellText(ImageList<E> list, E obj);

        /**
         * Given a list object and desired cell give a
         * appropriate tooltip text.
         * @param obj not null
         * @param tooltipFont the tooltip FontMetrics
         * @return cell tooltip text
         */
        String getTooltipText(ImageList<E> list, FontMetrics tooltipMetrics, E obj);
    }

    /**
     * To display the image list. If you want
     * the scrollbar elsewhere use the getVerticalScrollBar()
     * method.
     */
    public JComponent getView() {
        return layer;
    }

    public ImageList(int cellWidth, int cellHeight, RenderValues<E> update, ListModel<E> dataModel, ListSelectionModel selModel) {
        this(cellWidth, cellHeight, update, dataModel);
        list.setSelectionModel(selModel);
    }

    public ImageList(int cellWidth, int cellHeight, RenderValues<E> update, ListModel<E> dataModel) {
        this.update = update;
        init(new WrappedList(dataModel), cellWidth, cellHeight);
    }

    private void init(final WrappedList list, int cellWidth, int cellHeight) {
        this.list = list;
        //for text (? assume 30) and borders (20*2)
        int imageHeight = Math.max(0, cellHeight - 70);
        int imageWidth = Math.max(0, cellWidth - 40);
        ImageFileListCellRenderer renderer = new ImageFileListCellRenderer(imageWidth, imageHeight);
        list.setCellRenderer(renderer);
        list.setFixedCellWidth(cellWidth);
        list.setFixedCellHeight(cellHeight);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setInheritsPopupMenu(true);
        layer.setInheritsPopupMenu(true);
        list.setOpaque(true);
        renderer.renderer.setOpaque(true);
        layer.setOpaque(true);
        JViewport port = layer.getViewport();
        port.setOpaque(true);
        port.setLayout(new CenteredViewPortLayout());
        port.add(list);
        //don't allow the laf to be a smart-ass
        //(sometimes they replace uiresource colors
        //acording to the components).
        //Setting a "normal" color avoid that in most of them
        Color viewColor = new Color(UIManager.getColor("List.background").getRGB());
        port.setBackground(viewColor);
        layer.setBackground(viewColor);
        list.setBackground(viewColor);
        renderer.renderer.setBackground(viewColor);
        renderer.renderer.setForeground(new Color(UIManager.getColor("List.foreground").getRGB()));

        ActionMap actions = list.getActionMap();
        //values got from (it's private)
        //javax.swing.plaf.basic.BasicListUI.Actions
        Action prev = actions.get("selectPreviousColumn");
        actions.put("selectPreviousColumn", new MoveRow(list, prev, true));
        prev = actions.get("selectNextColumn");
        actions.put("selectNextColumn", new MoveRow(list, prev, false));
    }

    private final class WrappedList extends JList<E> {

        private final int initialTimeout = ToolTipManager.sharedInstance().getInitialDelay();
        private final int reshowTimeout = ToolTipManager.sharedInstance().getReshowDelay();
        private final int dismissTimeout = ToolTipManager.sharedInstance().getDismissDelay();
        private JToolTip tip;
        private FontMetrics metrics;

        public WrappedList(E[] listData) {
            super(listData);
            init();
        }

        public WrappedList(ListModel<E> dataModel) {
            super(dataModel);
            init();
        }

        private void init() {
            TooltipManagerJListDispatcher d = new TooltipManagerJListDispatcher();
            //show tooltips immediatly & send events (cheat)
            //show tooltips after scrolling & moving and disable them on mouse exit and focus lost
            addMouseListener(d);
            layer.addMouseWheelListener(d);
            addListSelectionListener(d);
            addFocusListener(d);
            addHierarchyListener(d);

            tip = new CellWideToolTip();
            Font tooltipFont = getFont().deriveFont(getFont().getSize() + 5F);
            metrics = getFontMetrics(tooltipFont);
            tip.setFont(tooltipFont);
            tip.setComponent(this);

            //passthrough the mouse events to registered mouse listeners on the list
            //(needed for heavywheight tooltips)
            TooltipMouseDispatcher md = new TooltipMouseDispatcher();
            tip.addMouseListener(md);
            tip.addMouseWheelListener(md);
            tip.addMouseMotionListener(md);
        }

        @Override
        public JToolTip createToolTip() {
            return tip;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            Point point = event.getPoint();
            int index = this.locationToIndex(point);
            //Get the value of the item in the list
            if (index == -1) {
                return null;
            }
            String s = update.getTooltipText(ImageList.this, metrics, getModel().getElementAt(index));
            return s;
        }

        @Override
        @SuppressWarnings({"unchecked", "cast"})
        public Point getToolTipLocation(MouseEvent event) {
            int mouseIndex = list.locationToIndex(event.getPoint());
            if (mouseIndex == -1) {
                return null;
            }
            ImageFileListCellRenderer cr = (ImageFileListCellRenderer) list.getCellRenderer();
            Rectangle r = getCellBounds(mouseIndex, mouseIndex);
            int textStart = cr.renderer.getBaseline(r.width, r.height)
                    - getFontMetrics(getFont()).getMaxAscent();

            Point scrollPt = SwingUtilities.convertPoint(this, r.x, r.y + textStart, layer);
            Point loc;
            if (scrollPt.y > layer.getHeight()) {
                //good ui's will limit this to the screen height,
                //no need to subtract the tooltip height on that special case.
                loc = SwingUtilities.convertPoint(layer, scrollPt.x, layer.getHeight(), this);
            } else {
                loc = new Point(r.x, r.y + textStart);
            }
            return loc;
        }

        @Override
        public void removeNotify() {
            super.removeNotify();
            Iterator<BufferedImage> savedPictures = memoryCache.values().iterator();
            while (savedPictures.hasNext()) {
                Image picture = savedPictures.next();
                if (picture != null) {
                    savedPictures.remove();
                    picture.flush();
                }
            }
        }

        private final class CellWideToolTip extends JToolTip {

            @Override
            public Dimension getPreferredSize() {
                Dimension ps = super.getPreferredSize();
                if (ps.width < getFixedCellWidth()) {
                    ps.width = getFixedCellWidth();
                }
                return ps;
            }
        }

        /**
         * Listener for events that augment the tooltip manager functionality on JList
         */
        private final class TooltipManagerJListDispatcher implements HierarchyListener, WindowFocusListener, FocusListener, ListSelectionListener, MouseListener, MouseWheelListener {

            private void resetDurations() {
                final ToolTipManager sharedInstance = ToolTipManager.sharedInstance();
                sharedInstance.setInitialDelay(initialTimeout);
                sharedInstance.setReshowDelay(reshowTimeout);
                sharedInstance.setDismissDelay(dismissTimeout);
                //to reset, but not disable the other components
                sharedInstance.setEnabled(false);
                sharedInstance.setEnabled(true);
            }

            private void setupDurations() {
                final ToolTipManager sharedInstance = ToolTipManager.sharedInstance();
                sharedInstance.setInitialDelay(0);
                sharedInstance.setReshowDelay(0);
                sharedInstance.setDismissDelay(Integer.MAX_VALUE);
            }

            @Override
            public void hierarchyChanged(HierarchyEvent e) {

                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == HierarchyEvent.SHOWING_CHANGED && !WrappedList.this.isShowing()) {

                    resetDurations();
                } else if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) == HierarchyEvent.PARENT_CHANGED) {
                    Window w = SwingUtilities.windowForComponent(WrappedList.this);
                    if (w != null) {
                        w.removeWindowFocusListener(this);
                        w.addWindowFocusListener(this);
                    }
                }
            }

            @Override
            public void mouseWheelMoved(final MouseWheelEvent e) {
                //component.getMousePosition always returns null
                //in this component. I dont know why.
                Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
                SwingUtilities.convertPointFromScreen(mouseLocation, WrappedList.this);

                if (mouseLocation == null) {
                    return;
                }
                //"convert" to a mouse motion event to trip the TooltipManager
                MouseEvent phantom = new MouseEvent(WrappedList.this, MouseEvent.MOUSE_MOVED, e.getWhen(), 0, mouseLocation.x, mouseLocation.y, 0, false);
                ToolTipManager.sharedInstance().mouseMoved(phantom);
            }

            @Override
            public void valueChanged(final ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                int index = getSelectedIndex();
                if (index == -1) {
                    return;
                }
                Rectangle r = getCellBounds(index, index);
                if (r == null) {
                    return;
                }
                Point p = r.getLocation();
                //"convert" to a mouse motion event to trip the TooltipManager
                MouseEvent phantom = new MouseEvent(WrappedList.this, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, p.x, p.y, 0, false);
                ToolTipManager.sharedInstance().mouseMoved(phantom);
            }

            @Override
            public void focusGained(FocusEvent e) {
                setupDurations();
            }

            @Override
            public void focusLost(FocusEvent e) {
                resetDurations();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setupDurations();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                //if the point is in the bounds, it "exited" to a popupmenu
                if (!e.getComponent().getBounds().contains(e.getPoint())) {
                    resetDurations();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void windowGainedFocus(WindowEvent e) {
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                resetDurations();
            }
        }

        /**
         * Translator for passing events from the tooltip to eventually the tooltipmanager
         */
        private final class TooltipMouseDispatcher implements MouseListener, MouseWheelListener, MouseMotionListener {

            @Override
            public void mouseClicked(MouseEvent e) {
                list.dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, WrappedList.this));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                list.dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, WrappedList.this));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                list.dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, WrappedList.this));
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                layer.dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, layer));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
                SwingUtilities.convertPointFromScreen(mouseLocation, WrappedList.this);
                if (mouseLocation == null) {
                    return;
                }
                //"convert" to a mouse motion event to trip the TooltipManager
                MouseEvent phantom = new MouseEvent(WrappedList.this, MouseEvent.MOUSE_MOVED, e.getWhen(), 0, mouseLocation.x, mouseLocation.y, 0, false);
                ToolTipManager.sharedInstance().mouseMoved(phantom);
            }
        }
    }

    private final class ImageFileListCellRenderer extends MouseAdapter implements ListCellRenderer<E> {

        public final int MAX_IMAGES_REQUESTS;
        private final DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        private final Icon emptyIcon;
        private final ImageIcon imgIcon = new ImageIcon();
        private final int width, height;
        private int counter;
        public E mouseValue;
        public int mouseIndex = -1;
        public GlowFilter glow = new GlowFilter(0.04F);

        public ImageFileListCellRenderer(final int width, final int height) {
            super();
            this.width = width;
            this.height = height;
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            MAX_IMAGES_REQUESTS = Math.max(30, (screenSize.width / width * screenSize.height / height) * 3);
            emptyIcon = new EmptyIcon(width, height);
            renderer.setHorizontalAlignment(SwingConstants.CENTER);
            renderer.setVerticalAlignment(SwingConstants.BOTTOM);
            renderer.setVerticalTextPosition(JLabel.BOTTOM);
            renderer.setHorizontalTextPosition(JLabel.CENTER);
            list.addMouseMotionListener(this);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends E> list, E value, final int index, boolean isSelected, boolean cellHasFocus) {
            BufferedImage img = memoryCache.get(value);
            //null was put there. No image in cache...yet
            if (img == null && memoryCache.containsKey(value)) {
                return asTextOnly(list, update.getCellText(ImageList.this, value), isSelected);
            }
            if (img == null) {
                //return null until image is not null
                memoryCache.put(value, null);
                //should call repaint later
                update.requestCellImage(ImageList.this, value, width, height);
                //optimization (dont wait for the repaint if the request is put without threading)
                img = memoryCache.get(value);
                if (img == null) {
                    return asTextOnly(list, update.getCellText(ImageList.this, value), isSelected);
                }
            }

            //gc the old images... once in a while
            if (counter++ == MAX_IMAGES_REQUESTS) {
                counter = 0;
                disposeInvisibleImages(list);
            }

            if (mouseValue == value) {
                img = glow.filter(img, null);
            }
            return asTextAndImage(list, update.getCellText(ImageList.this, value), img, isSelected);
        }

        private JLabel asTextOnly(JList list, String text, boolean focused) {
            renderer.getListCellRendererComponent(list, null, -1, focused, false);
            renderer.setIcon(emptyIcon);
            renderer.setText(text);
            return renderer;
        }

        private JLabel asTextAndImage(JList list, String text, BufferedImage img, boolean focused) {
            renderer.getListCellRendererComponent(list, null, -1, focused, false);
            imgIcon.setImage(img);
            renderer.setIcon(imgIcon);
            renderer.setText(text);
            return renderer;
        }

        @SuppressWarnings("unchecked") //just to use the same list
        private void disposeInvisibleImages(JList list) {
            int firstIndex = Math.max(0, list.getFirstVisibleIndex() - 10);
            int lastIndex =  Math.min(list.getModel().getSize()-1, list.getLastVisibleIndex() + 10);
            //A direct index mapping wouldn't work since listmodel can be mutable
            List tmp = new ArrayList();
            ListModel model = list.getModel();
            for (int i = firstIndex; i <= lastIndex; i++) {
                Object key = model.getElementAt(i);
                if (memoryCache.containsKey(key)) {
                    tmp.add(key);
                    tmp.add(memoryCache.remove(key));
                }
            }
            Iterator<BufferedImage> values = memoryCache.values().iterator();
            while (values.hasNext()) {
                Image disposable = values.next();
                if (disposable != null) {
                    values.remove();
                    disposable.flush();
                }
            }
            Iterator savedImgs = tmp.iterator();
            while (savedImgs.hasNext()) {
                E key = (E) savedImgs.next();
                BufferedImage saved = (BufferedImage) savedImgs.next();
                memoryCache.put(key, saved);
            }
        }

        @Override
        public void mouseMoved(MouseEvent event) {
            Point point = event.getPoint();
            int index = list.locationToIndex(point);
            //Get the value of the item in the list
            Rectangle cell = null;
            //this range should always be inside the frame, so not null.
            boolean isInside = index >= 0 && (cell = list.getCellBounds(index, index)).contains(point);
            if (isInside) {
                ListModel<E> model = list.getModel();
                E value;
                if (index != mouseIndex) {
                    list.repaint(cell);
                    Rectangle oldRect = list.getCellBounds(mouseIndex, mouseIndex);
                    //still reachable
                    if (oldRect != null) {
                        list.repaint(oldRect);
                    }
                    mouseValue = model.getElementAt(index);
                    mouseIndex = index;
                } else if (mouseValue != (value = model.getElementAt(index))) {
                    list.repaint(cell);
                    mouseValue = value;
                }
            }

        }
    }

    /**
     * A viewport layout that tries to horizontally center a component in
     * the viewport.
     * @author i30817
     */
    private static final class CenteredViewPortLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
            /*nop*/
        }

        @Override
        public void removeLayoutComponent(Component comp) {
            /*nop*/
        }

        public Dimension maximumLayoutSize(Container target) {
            if (target == null) {
                return new Dimension(0, 0);
            }
            return target.getPreferredSize();
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Component view = ((JViewport) parent).getView();
            if (view == null) {
                return new Dimension(0, 0);
            } else if (view instanceof Scrollable) {
                return ((Scrollable) view).getPreferredScrollableViewportSize();
            } else {
                return view.getPreferredSize();
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(4, 4);
        }

        @Override
        public void layoutContainer(Container parent) {
            JViewport port = (JViewport) parent;
            JScrollPane pane = (JScrollPane) port.getParent();
            Component view = port.getView();

            Dimension maximumViewSize = pane.getSize();
            maximumViewSize.width -= pane.getVerticalScrollBar().getWidth();
            Dimension newViewSize = getCellRowDimension(view, maximumViewSize);
            int justifiedStartX = (maximumViewSize.width - newViewSize.width) / 2;
            /**_________     __________
             * ||YYY|XX|  -> |X|YYYY|X|
             * ||YYY|XX|     |X|YYYY|X|
             */
//            pane.setBackground(Color.CYAN);
//            port.setBackground(Color.MAGENTA);
//            view.setBackground(Color.ORANGE);
            port.setBounds(justifiedStartX, port.getY(), newViewSize.width, port.getHeight());
            port.setViewSize(newViewSize);
        }

        private static Dimension getCellRowDimension(Component view, Dimension maximumViewSize) {
            /**
             * All of the dimensions below are in view coordinates.
             */
            Dimension newViewSize = view.getPreferredSize();

            /**
             * If a JList the preferred size is the preferred size of the
             * sum of cells fitting in a row.
             * Also, the limit is the number of cells.
             */
            if (view instanceof JList) {
                int fixedCellWidth = ((JList) view).getFixedCellWidth();
                int cellNumber = ((JList) view).getModel().getSize();
                if (fixedCellWidth != -1) {
                    newViewSize.width = fixedCellWidth;
                }
                /**
                 * Multiply the cell width until it matches the
                 * n * viewPreferredSize.width + z = viewPort.width
                 * for a z < preferredSize and n <= expandLimit
                 */
                int cellsInRow = maximumViewSize.width / newViewSize.width;
                if (cellsInRow > cellNumber) {
                    cellsInRow = cellNumber;
                }
                newViewSize.width = cellsInRow * newViewSize.width;
            }
            //fill to maximum size if it doesn't fit or nothing there
            if (newViewSize.width == 0) {
                newViewSize.width = maximumViewSize.width;
            }
            return newViewSize;
        }
    }

    private class MoveRow extends AbstractAction {

        final WrappedList list;
        final Action defaultAction;
        final boolean up;

        private MoveRow(WrappedList list, Action prev, boolean up) {
            this.list = list;
            this.up = up;
            defaultAction = prev;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int cellNumber = list.getModel().getSize();
            if (cellNumber == 0) {
                return;
            }
            int index = list.getLeadSelectionIndex();
            if ((up && index == 0) || (!up && index == cellNumber - 1)) {
                return;
            }

            int viewWidth = layer.getWidth();
            //don't cache this... it changes from the constructor
            viewWidth -= layer.getVerticalScrollBar().getWidth();

            int cellsInRow = viewWidth / list.getFixedCellWidth();
            if (cellsInRow > cellNumber) {
                return;
            } else if (cellsInRow == 0) {
                //always at least 1, even if the division up there returns 0
                //(partial cell in viewport)
                cellsInRow = 1;
            }

            int modulusIndex = index % cellsInRow;

            if (up && modulusIndex == 0) {
                list.setSelectedIndex(index - 1);
                list.ensureIndexIsVisible(index - 1);
            } else if (!up && modulusIndex == cellsInRow - 1) {
                list.setSelectedIndex(index + 1);
                list.ensureIndexIsVisible(index + 1);
            } else if (defaultAction != null) {
                defaultAction.actionPerformed(e);
            }
        }
    }
}
