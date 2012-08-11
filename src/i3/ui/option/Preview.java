package i3.ui.option;

import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * A preview class for a component
 * @author  Owner
 */
public class Preview extends JPanel {

    private JComponent component;

    /** Stub constructor for matisse*/
    public Preview() {
        super();
        component = new JTextArea("MOCKUP");
        initComponents();
    }

    public Preview(JComponent wrapped) {
        super();
        component = wrapped;
        initComponents();
    }

    public JComponent getWrappedComponent() {
        return component;
    }

    private void initComponents() {
        setBorder(javax.swing.BorderFactory.createTitledBorder("Preview"));
        setFocusable(false);

        component.setMinimumSize(new java.awt.Dimension(0, 0));

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup().addGroup(layout.createSequentialGroup().addContainerGap().addComponent(component, GroupLayout.DEFAULT_SIZE, 473, Short.MAX_VALUE).addContainerGap()));
        layout.setVerticalGroup(
                layout.createParallelGroup().addGroup(layout.createSequentialGroup().addComponent(component, GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE).addContainerGap()));
    }
}
