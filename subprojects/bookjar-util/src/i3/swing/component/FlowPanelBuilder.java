package i3.swing.component;

import javax.swing.Action;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * A view factory for a horizontal panel
 * @author i30817
 */
public class FlowPanelBuilder {

    private JPanel view;
    private SequentialGroup sg;
    private ParallelGroup pg;

    /**
     * If you want to use a plain jpanel
     * as the view.
     * @param viewClass
     */
    public FlowPanelBuilder() {
        view = new JPanel();
        init(view);
    }

    /**
     * If you want to use another class than plain jpanel
     * as the view.
     * @param viewClass
     */
    public FlowPanelBuilder(JPanel view) {
        this.view = view;
        JPanel viewAux = new JPanel();
        view.add(viewAux);
        init(viewAux);
    }

    private void init(JComponent view) {
        view.setInheritsPopupMenu(true);
        GroupLayout l = new GroupLayout(view);
        view.setLayout(l);
        l.setAutoCreateContainerGaps(true);
        l.setAutoCreateGaps(true);
        sg = l.createSequentialGroup();
        pg = l.createBaselineGroup(true, true);
        l.setHorizontalGroup(sg);
        l.setVerticalGroup(pg);
    }

    /**
     * Configuration of the size of the
     * added component
     */
    public enum SizeConfig {

        /**
         * The component is added at its preferred size and doesn't grow
         */
        PreferredSize,
        /**
         * The component divides the size with the other components until it's maximum size.
         */
        FillSize,
    }

    /**
     * Add a label (if name in component != null) & a component to
     * the horizontal panel, left to right.
     * @param labelName label name
     * @param c the component.
     * @param sizeConfig size configuration option
     * @throws IllegalArgumentException if labelName or c or size config is null
     * @return this.
     */
    public FlowPanelBuilder addLabelAndComponent(String labelName, JComponent c, SizeConfig sizeConfig) {
        if (c == null || sizeConfig == null || labelName == null) {
            throw new IllegalArgumentException("Null argument");
        }

        JLabel label = new JLabel(labelName);
        label.setLabelFor(c);
        sg = sg.addComponent(label);
        pg = pg.addComponent(label);
        add(c, sizeConfig);
        return this;
    }

    /**
     * Add a component to the horizontal panel, left to right.
     * @param c the component.
     * @param sizeConfig size configuration option
     * @throws IllegalArgumentException if c or size config is null
     * @return this.
     */
    public FlowPanelBuilder add(JComponent c, SizeConfig sizeConfig) {
        if (c == null) {
            throw new IllegalArgumentException("Null component");
        }
        if (SizeConfig.PreferredSize == sizeConfig) {
            sg = sg.addComponent(c, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
            pg = pg.addComponent(c, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
        } else if (SizeConfig.FillSize == sizeConfig) {
            sg = sg.addComponent(c);
            pg = pg.addComponent(c);
        } else {
            throw new IllegalArgumentException("Null size config");
        }
        return this;
    }

    /**
     * Adds a action on the view when escape is pressed
     * throws illegalargument exception if called
     * with a null value
     * @param a
     * @return
     */
    public FlowPanelBuilder addEscapeAction(Action a) {
        if (a == null) {
            throw new IllegalArgumentException("Close action can't be null");
        }
        view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "escape");
        view.getActionMap().put("escape", a);
        return this;
    }

    public JPanel getView() {
        return view;
    }
}
