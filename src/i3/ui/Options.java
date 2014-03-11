package i3.ui;

import i3.io.IoUtils;
import i3.main.Bookjar;
import i3.parser.Property;
import i3.swing.LookAndFeels;
import i3.ui.controller.MovingPane;
import i3.ui.option.BooleanOptions;
import i3.ui.option.ColorOptions;
import i3.ui.option.ObjectOptions;
import i3.ui.option.Preview;
import i3.ui.option.ShortcutOptions;
import i3.ui.styles.DocumentStyle;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.plaf.ColorUIResource;
import javax.swing.text.StyleConstants;
import org.netbeans.validation.api.Problem;
import org.netbeans.validation.api.ui.ValidationUI;
import org.netbeans.validation.api.ui.swing.SwingValidationGroup;

public class Options extends javax.swing.JDialog {

    private final MovingPane preview;
    private final Application main;
    private LookAndFeelInfo defaultInfo;
    private final SwingValidationGroup validation;

    /**
     * Creates new form OptionDialog
     */
    public Options(Application main) {
        super(main.frame, true);
        this.main = main;
        preview = new MovingPane();
        String text = "<p>A word can be Spoken, <font color=blue>Blue</font>, <i>Italic</i>, <b>Bold</b>, <u>Underline</u>, <s>Strikethrough</s> and in another <font face='Monospaced'>Font</font>.</p><p> A <a href='#blah'>Reference</a>.</p>";
        Map<Property, Object> props = new EnumMap<>(Property.class);
        props.put(Property.REFORMAT, false);
        props.put(Property.HYPERLINK_COLOR, main.pane.getLinkColor());
        preview.read(text, "text/html", 0, props);
        preview.addPropertyChangeListener(MovingPane.MOUSE_ENTER_HYPERLINK, new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                setCursor(hand);
            }
        });
        preview.addPropertyChangeListener(MovingPane.MOUSE_EXIT_HYPERLINK, new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Cursor cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
                setCursor(cursor);
            }
        });

        initComponents();
        getRootPane().setDefaultButton(okButton);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelAction");
        getRootPane().getActionMap().put("cancelAction", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                cancelButtonPressed(e);
            }
        });

        colorOptions.addPropertyChangeListener("Font Color",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        Color previewColor = (Color) evt.getNewValue();
                        fontColorChanged(previewColor);
                    }
                });

        colorOptions.addPropertyChangeListener("Paper Color",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        Color previewColor = (Color) evt.getNewValue();
                        paperColorChanged(previewColor);
                    }
                });

        colorOptions.addPropertyChangeListener("Voice Color",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        Color previewColor = (Color) evt.getNewValue();
                        voiceColorChanged(previewColor);
                    }
                });

        colorOptions.addPropertyChangeListener("Link Color",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        Color previewColor = (Color) evt.getNewValue();
                        preview.setLinkColor(previewColor);
                    }
                });

        colorOptions.addPropertyChangeListener("Used Link Color",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        Color previewColor = (Color) evt.getNewValue();
                        preview.setUsedLinkColor(previewColor);
                    }
                });

        booleanOptions.addPropertyChangeListener("Italic",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setShowItalic((Boolean) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        booleanOptions.addPropertyChangeListener("Bold",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setShowBold((Boolean) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        booleanOptions.addPropertyChangeListener("Underline",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setShowUnderline((Boolean) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        booleanOptions.addPropertyChangeListener("Strikethrough",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setShowStrikeThrough((Boolean) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        booleanOptions.addPropertyChangeListener("Fonts",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setShowFontFamily((Boolean) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        booleanOptions.addPropertyChangeListener("Font Color",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setShowForeground((Boolean) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        objectOptions.addPropertyChangeListener("Text alignment",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setAlignment((Integer) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        objectOptions.addPropertyChangeListener("Text font",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setFontFamily((String) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        objectOptions.addPropertyChangeListener("Text size",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setFontSize((Integer) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        objectOptions.addPropertyChangeListener("Text indent",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setFirstLineIndent((Float) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        objectOptions.addPropertyChangeListener("Text space",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        preview.getDocumentStyle().setSpaceBelow((Float) evt.getNewValue());
                        preview.getDocumentStyle().change();
                    }
                });

        objectOptions.addPropertyChangeListener("Look and Feel",
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(final PropertyChangeEvent evt) {
                        //give an opportunity to process all pending event before
                        //changing the UI.
                        final LookAndFeelInfo changeLafInfo = (LookAndFeelInfo) evt.getNewValue();
                        EventQueue.invokeLater(new Runnable() {

                            @Override
                            public void run() {
                                if (updateLaf(changeLafInfo, Options.this)) {
                                    //hack to repaint the colors if
                                    //they are uiresource (changes with laf)
                                    //or systemColors (fallback to uiresource colors)
                                    Color c = colorOptions.get("Paper Color");
                                    if (c instanceof ColorUIResource) {
                                        paperColorChanged(UIManager.getColor("EditorPane.background"));
                                    }
                                    c = colorOptions.get("Font Color");
                                    if (c instanceof ColorUIResource) {
                                        fontColorChanged(UIManager.getColor("EditorPane.foreground"));
                                    }
                                    //voice color is buggy and always needs to be updated
                                    c = colorOptions.get("Voice Color");
                                    if (c instanceof ColorUIResource) {
                                        voiceColorChanged(UIManager.getColor("EditorPane.selectionBackground"));
                                    } else {
                                        voiceColorChanged(c);
                                    }
                                    preview.getView().repaint();
                                    Options.this.pack();
                                }
                            }
                        });
                    }
                });

        ValidationUI okUI = new ValidationUI() {

            @Override
            public void clearProblem() {
                okButton.setEnabled(true);
            }

            @Override
            public void showProblem(Problem prblm) {
                okButton.setEnabled(false);
            }
        };

        validation = SwingValidationGroup.create(okUI);
        shortcutOptions.setupValidation(validation);

        configureOptions();
        booleanOptions.updateSize();
        colorOptions.updateSize();
        objectOptions.updateSize();
        shortcutOptions.updateSize();
        pack();
    }

    private void configureOptions() {
        MovingPane pane = main.pane;
        DocumentStyle outerStyle = pane.getDocumentStyle();
        //show current/possible values in views
        preview.getDocumentStyle().copyFrom(outerStyle);
        shortcutOptions.putAll(main.actions);
        //copied from many places these are the default colors.
        //some laf have no default colors for these keys.
        //If so, use the same.
        Color previousColor = outerStyle.getForeground();
        Color defaultColor = UIManager.getColor("EditorPane.foreground");
        colorOptions.addColorOption("Font Color", previousColor, defaultColor == null ? previousColor : defaultColor);
        previousColor = pane.getView().getBackground();
        defaultColor = UIManager.getColor("EditorPane.background");
        colorOptions.addColorOption("Paper Color", previousColor, defaultColor == null ? previousColor : defaultColor);
        previousColor = pane.getSelectionColor();
        defaultColor = UIManager.getColor("EditorPane.selectionBackground");
        colorOptions.addColorOption("Voice Color", previousColor, defaultColor == null ? previousColor : defaultColor);
        colorOptions.addColorOption("Link Color", pane.getLinkColor(), Color.blue);
        colorOptions.addColorOption("Used Link Color", pane.getUsedLinkColor(), Color.magenta);
        String description = "Let the italic of the original text show.";
        booleanOptions.addBooleanOption("Italic", outerStyle.getShowItalic(), description);
        description = "Let the bold of the original text show.";
        booleanOptions.addBooleanOption("Bold", outerStyle.getShowBold(), description);
        description = "Let the underline of the original text show.";
        booleanOptions.addBooleanOption("Underline", outerStyle.getShowUnderline(), description);
        description = "Let the strikethrough of the original text show.";
        booleanOptions.addBooleanOption("Strikethrough", outerStyle.getShowStrikeThrough(), description);
        description = "Let the fonts of the original text show. If the text defines fonts for all the text (as many do) you'll not be able to see the font you choose in the text options.";
        booleanOptions.addBooleanOption("Fonts", outerStyle.getShowFontFamily(), description);
        description = "Let the font color of the original text show. BookJar replaces the Black color (normally the most common) by your selection in the Text tab so this doesn't work for that color.";
        booleanOptions.addBooleanOption("Font Color", outerStyle.getShowForeground(), description);
        description = "Let the clock show or not. Saves a bit of battery.";
        booleanOptions.addBooleanOption("Show clock", main.showClock, description);

        description = "Set the look and feel of the application. This requires a application restart on pressing ok.";
        Object[] options = LookAndFeels.commonInstalledLookAndFeels();
        defaultInfo = LookAndFeels.getCurrentLookAndFeelInfo();
        objectOptions.addObjectOption("Look and Feel", defaultInfo, options, description);
        description = "Set the alignment of the text.";
        options = new Object[]{StyleConstants.ALIGN_LEFT, StyleConstants.ALIGN_RIGHT, StyleConstants.ALIGN_CENTER, StyleConstants.ALIGN_JUSTIFIED};
        String[] optionsNames = new String[]{"Left align", "Right align", "Center align", "Justified"};
        objectOptions.addObjectOption("Text alignment", outerStyle.getAlignment(), options, optionsNames, description);
        description = "Set the font family of the text.";
        //asList list is immutable.
        List<String> fontList = new ArrayList<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.getDefault())));
        fontList.remove("aakar"); //bugged linux fonts. Makes the vm crash. Eliminate, eliminate!
        fontList.remove("Rekha");
        options = fontList.toArray();
        objectOptions.addObjectOption("Text font", outerStyle.getFontFamily(), options, description);
        description = "Set the font size.";
        options = new Object[]{10, 11, 12, 13, 14, 16, 18, 22, 26, 28, 32, 36, 42};
        objectOptions.addObjectOption("Text size", outerStyle.getFontSize(), options, description);
        description = "Set the first line indent of paragraphs.";
        options = new Object[]{(float) 0, (float) 5, (float) 10, (float) 15, (float) 20, (float) 25};
        objectOptions.addObjectOption("Text indent", outerStyle.getFirstLineIndent(), options, description);
        description = "Set the space between paragraphs.";
        options = new Object[]{(float) 0, (float) 5, (float) 10, (float) 15, (float) 20, (float) 25};
        objectOptions.addObjectOption("Text space", outerStyle.getSpaceBelow(), options, description);
    }

    private void fontColorChanged(Color previewColor) {
        preview.getDocumentStyle().setForeground(previewColor);
        preview.getDocumentStyle().change();
    }

    private void paperColorChanged(Color previewColor) {
        preview.getView().setBackground(previewColor);
    }

    private void voiceColorChanged(Color previewColor) {
        preview.setSelectionColor(previewColor);
        //isShowing is false and this operation apparently
        //needs it to be true. Do it later.
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                preview.highlight(14, 20);
                preview.getView().repaint();
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        okButton = new JButton();
        cancelButton = new JButton();
        optionsTabPane = new JTabbedPane();
        colorOptions = new ColorOptions();
        booleanOptions = new BooleanOptions();
        objectOptions = new ObjectOptions();
        shortcutOptions = new ShortcutOptions("Press button to set the ", " shortcut.");
        preview1 = new Preview(preview.getView());

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        okButton.setText("Ok");
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                okButtonPressed(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                cancelButtonPressed(evt);
            }
        });

        optionsTabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        optionsTabPane.addTab("Colors", colorOptions);
        optionsTabPane.addTab("Visibility", booleanOptions);
        optionsTabPane.addTab("Looks", objectOptions);
        optionsTabPane.addTab("Shortcuts", shortcutOptions);

        preview1.setMinimumSize(new Dimension(0, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(Alignment.LEADING)
            .addGroup(Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(Alignment.TRAILING)
                    .addComponent(preview1, Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 740, Short.MAX_VALUE)
                    .addComponent(optionsTabPane, Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 740, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(okButton)
                        .addPreferredGap(ComponentPlacement.RELATED, 630, Short.MAX_VALUE)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(optionsTabPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(preview1, javax.swing.GroupLayout.DEFAULT_SIZE, 227, Short.MAX_VALUE)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton))
                .addContainerGap())
        );

        optionsTabPane.getAccessibleContext().setAccessibleName("");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonPressed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonPressed
        setVisible(false);
        dispose();
        updateLaf(defaultInfo, this);
    }

    private boolean notCurrent(LookAndFeelInfo toUse) {
        return !LookAndFeels.getCurrentLookAndFeelInfo().getClassName().equals(toUse.getClassName());
    }

    private boolean updateLaf(LookAndFeelInfo toUse, Component c) {
        if (notCurrent(toUse)) {
            try {
                UIManager.setLookAndFeel(toUse.getClassName());
                SwingUtilities.updateComponentTreeUI(c);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                throw new AssertionError("Error instantiating look and feel", ex);
            }
            return true;
        }
        return false;
    }//GEN-LAST:event_cancelButtonPressed

    private void okButtonPressed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonPressed
        MovingPane pane = main.pane;
        pane.getView().setBackground(colorOptions.get("Paper Color"));
        pane.setSelectionColor(colorOptions.get("Voice Color"));
        pane.setLinkColor(colorOptions.get("Link Color"));
        pane.setUsedLinkColor(colorOptions.get("Used Link Color"));
        pane.getDocumentStyle().copyFrom(preview.getDocumentStyle());
        main.actions.install(shortcutOptions);
        main.showClock = booleanOptions.get("Show clock");
        main.clock.setVisible(main.showClock);
        setVisible(false);
        dispose();
        if (notCurrent(defaultInfo)) {
            IoUtils.restart(Bookjar.class);
        }
    }//GEN-LAST:event_okButtonPressed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private BooleanOptions booleanOptions;
    private JButton cancelButton;
    private ColorOptions colorOptions;
    private ObjectOptions objectOptions;
    private JButton okButton;
    private JTabbedPane optionsTabPane;
    private Preview preview1;
    private ShortcutOptions shortcutOptions;
    // End of variables declaration//GEN-END:variables
}
