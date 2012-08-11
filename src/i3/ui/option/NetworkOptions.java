/*
 * AuthentificationPanel.java
 *
 * Created on 16 de Julho de 2007, 14:07
 */
package i3.ui.option;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.net.ProxySelector;
import java.text.NumberFormat;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import i3.net.AuthentificationProxySelector;
import i3.net.AuthentificationProxySelector.ProxyMode;

/**
 *
 * @author  i30817
 */
public class NetworkOptions extends JPanel {

    private AuthentificationProxySelector selector;

    public NetworkOptions() {
        this(new AuthentificationProxySelector(true));
    }

    /** Creates new form AuthentificationPanel
     *  This class fills a AuthentificationProxySelector.
     *  If the current ProxySelector is already an AuthentificationProxySelector
     *  it clones it. Its the calling class responsability to install it.
     */
    public NetworkOptions(AuthentificationProxySelector proxySelector) {
        super();
        initComponents();

        if (proxySelector == null) {
            throw new IllegalArgumentException("Given proxySelector cannot be null");
        }

        setCustomProxySelector(proxySelector);

        proxyField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusGained(FocusEvent e) {
                proxyField.selectAll();
            }
        });

        portField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusGained(FocusEvent e) {
                portField.selectAll();
            }
        });
        noProxyForField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusGained(FocusEvent e) {
                noProxyForField.selectAll();
            }
        });

        usernameField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusGained(FocusEvent e) {
                usernameField.selectAll();
            }
        });

        passwordField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusGained(FocusEvent e) {
                passwordField.selectAll();
            }
        });


        proxyField.getDocument().addDocumentListener(new DocumentAdapter() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                selector.useGlobalProxy(proxyField.getText());
            }
        });


        noProxyForField.getDocument().addDocumentListener(new DocumentAdapter() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                selector.setNoProxyFor(noProxyForField.getText());
            }
        });

        portField.getDocument().addDocumentListener(new DocumentAdapter() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                Number n = (Number) portField.getValue();
                selector.useGlobalPort(n.intValue());
            }
        });

        usernameField.getDocument().addDocumentListener(new DocumentAdapter() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                selector.setUserName(usernameField.getText());
            }
        });

        passwordField.getDocument().addDocumentListener(new DocumentAdapter() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                selector.setPassword(passwordField.getPassword());
            }
        });
    }

    private static abstract class DocumentAdapter implements DocumentListener {

        @Override
        public void removeUpdate(DocumentEvent e) {
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
        }
    }

    public ProxySelector getCustomProxySelector() {
        return selector;
    }

    public void setCustomProxySelector(AuthentificationProxySelector proxySelector) {
        if (proxySelector != null) {
            selector = proxySelector;
            proxyField.setText(selector.getHttpProxy());
            portField.setValue(selector.getHttpPort());
            noProxyForField.setText(selector.getNoProxyFor());
            usernameField.setText(selector.getUserName());
            passwordField.setText(String.valueOf(selector.getPassword()));
            askAuth.setSelected(selector.isProbing());

            //doClick instead of setSelected to invoke the actionlisteners
            switch (selector.getProxyMode()) {
                case SYSTEM_PROXY:
                    systemProxyRadio.doClick();
                    break;
                case MANUAL_PROXY:
                    manualProxyRadio.doClick();
                    break;
            }
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        systemProxyRadio = new javax.swing.JRadioButton();
        manualProxyRadio = new javax.swing.JRadioButton();
        jLabel1 = new javax.swing.JLabel();
        proxyLabel = new javax.swing.JLabel();
        proxyField = new javax.swing.JTextField();
        portLabel = new javax.swing.JLabel();
        usernameLabel = new javax.swing.JLabel();
        passwordLabel = new javax.swing.JLabel();
        usernameField = new javax.swing.JTextField();
        passwordField = new javax.swing.JPasswordField();
        askAuth = new javax.swing.JCheckBox();
        noProxyForLabel = new javax.swing.JLabel();
        noProxyForField = new javax.swing.JTextField();
        portField = new JFormattedTextField(NumberFormat.getIntegerInstance());

        buttonGroup1.add(systemProxyRadio);
        systemProxyRadio.setText("Use System Proxy Settings");
        systemProxyRadio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        systemProxyRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                systemProxyRadioActionPerformed(evt);
            }
        });

        buttonGroup1.add(manualProxyRadio);
        manualProxyRadio.setText("Use Manual Proxy Settings");
        manualProxyRadio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        manualProxyRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manualProxyRadioActionPerformed(evt);
            }
        });

        jLabel1.setText("Proxy Settings");

        proxyLabel.setText("Global Proxy:");

        portLabel.setText("Port:");

        usernameLabel.setText("Username:");
        usernameLabel.setEnabled(false);

        passwordLabel.setText("Password:");
        passwordLabel.setEnabled(false);

        usernameField.setEnabled(false);

        passwordField.setEnabled(false);

        askAuth.setSelected(true);
        askAuth.setText("Ask for authentification if needed");
        askAuth.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        askAuth.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                askAuthActionPerformed(evt);
            }
        });

        noProxyForLabel.setText("No Proxy For:");

        portField.setText("1080");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(manualProxyRadio)
                            .addComponent(systemProxyRadio))
                        .addGap(234, 234, 234))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(proxyLabel)
                            .addComponent(noProxyForLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(noProxyForField, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
                            .addComponent(proxyField, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(portLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(portField, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(askAuth)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(passwordLabel)
                                    .addComponent(usernameLabel))
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(passwordField, javax.swing.GroupLayout.DEFAULT_SIZE, 301, Short.MAX_VALUE)
                                    .addComponent(usernameField, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 301, Short.MAX_VALUE))))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(systemProxyRadio)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(manualProxyRadio)
                        .addGap(6, 6, 6)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(portLabel)
                            .addComponent(proxyLabel)
                            .addComponent(proxyField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(portField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(noProxyForLabel)
                            .addComponent(noProxyForField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(askAuth)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(usernameLabel)
                            .addComponent(usernameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(passwordLabel)
                            .addComponent(passwordField, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jLabel1))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void systemProxyRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_systemProxyRadioActionPerformed
        usernameField.setEnabled(false);
        passwordField.setEnabled(false);
        usernameLabel.setEnabled(false);
        passwordLabel.setEnabled(false);
        askAuth.setEnabled(false);
        proxyLabel.setEnabled(false);
        portLabel.setEnabled(false);
        proxyField.setEnabled(false);
        portField.setEnabled(false);
        noProxyForField.setEnabled(false);
        noProxyForLabel.setEnabled(false);

        selector.setProxyMode(ProxyMode.SYSTEM_PROXY);
    }//GEN-LAST:event_systemProxyRadioActionPerformed

    private void manualProxyRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manualProxyRadioActionPerformed
        usernameField.setEnabled(!askAuth.isSelected());
        passwordField.setEnabled(!askAuth.isSelected());
        usernameLabel.setEnabled(!askAuth.isSelected());
        passwordLabel.setEnabled(!askAuth.isSelected());
        askAuth.setEnabled(true);
        proxyLabel.setEnabled(true);
        portLabel.setEnabled(true);
        proxyField.setEnabled(true);
        portField.setEnabled(true);
        noProxyForField.setEnabled(true);
        noProxyForLabel.setEnabled(true);

        selector.setProxyMode(ProxyMode.MANUAL_PROXY);
    }//GEN-LAST:event_manualProxyRadioActionPerformed

    private void askAuthActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_askAuthActionPerformed
        usernameField.setEnabled(!askAuth.isSelected());
        passwordField.setEnabled(!askAuth.isSelected());
        usernameLabel.setEnabled(!askAuth.isSelected());
        passwordLabel.setEnabled(!askAuth.isSelected());
        selector.setProbing(askAuth.isSelected());
    }//GEN-LAST:event_askAuthActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox askAuth;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JRadioButton manualProxyRadio;
    private javax.swing.JTextField noProxyForField;
    private javax.swing.JLabel noProxyForLabel;
    private javax.swing.JPasswordField passwordField;
    private javax.swing.JLabel passwordLabel;
    private javax.swing.JFormattedTextField portField;
    private javax.swing.JLabel portLabel;
    private javax.swing.JTextField proxyField;
    private javax.swing.JLabel proxyLabel;
    private javax.swing.JRadioButton systemProxyRadio;
    private javax.swing.JTextField usernameField;
    private javax.swing.JLabel usernameLabel;
    // End of variables declaration//GEN-END:variables
}
