package i3.ui.styles;

import java.awt.Color;
import java.io.Serializable;
import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * DenialSet class that can deny the existance of StyleConstants in any of its
 * showX(boolean) methods.
 *
 * @author Owner
 *
 */
final class DenialSet implements Serializable {

    private static final long serialVersionUID = 752647115562277465L;
    /**
     * The attribute set that holds the Caracter StyleConstants that we
     * want to deny. The allowed ones are represented by the static functions
     * in this class.
     */
    private final SimpleAttributeSet deniability = new SimpleAttributeSet();
    /**
     * The key that will hold this object in a attributeset
     */
    private static final String DENIAL_KEY = "DENY";
    /**
     * Tells if we are seeing embedded components in the document now.
     */
    private boolean viewComponents = true;
    /**
     * A set of character styles that are always removed from the character
     * styles (even if nominally allowed or not mentioned).
     */
    private transient Object[] deny = new Object[0];

    /**
     * If you're using the returned atribute set in documentSetCharactersAttributes,
     * you need to set the replace flag to false (jdk bug).
     * @param attributes
     * @param parent
     * @return
     */
    public void replaceAttributesInLock(MutableAttributeSet attributes, AttributeSet parent) {
        attributes.setResolveParent(parent);

        for (Object aDeny : deny) {
            attributes.removeAttribute(aDeny);
        }
        attributes.removeAttribute(DENIAL_KEY);

        /*No need to cache in the 0 case.*/
        if (attributes.getAttributeCount() != 0) {

            attributes.addAttribute(DENIAL_KEY, attributes.copyAttributes());//storeAttributes(attributes));
            //if there is a component here all the other attributes are excluded
            //( the reason the else code is not outside)
            if (attributes.isDefined(StyleConstants.ComponentAttribute)) {
                StyleConstants.getComponent(attributes).setVisible(viewComponents);
            } else {
                attributes.removeAttributes(deniability.getAttributeNames());
            }
        }
    }

    /**
     * Returns the given attributeset. This in conjuntion with
     * deprocessAttributes has the effect of denying or allowing attributes
     * existing when the method is invoked. If you do this more than once the
     * old state is lost. It also removes any denied attributes from the function
     * denyCharacterAttributes(Object ...). This state is also lost.
     */
    public AttributeSet replaceAttributes(AttributeSet attributes, AttributeSet parent) {
        MutableAttributeSet delegate = new SimpleAttributeSet(attributes);
        delegate.setResolveParent(parent);

        for (Object aDeny : deny) {
            delegate.removeAttribute(aDeny);
        }
        delegate.removeAttribute(DENIAL_KEY);

        /*No need to cache in this case.*/
        if (delegate.getAttributeCount() == 0) {
            return SimpleAttributeSet.EMPTY;
        } else {
            delegate.addAttribute(DENIAL_KEY, attributes.copyAttributes());
            if (delegate.isDefined(StyleConstants.ComponentAttribute)) {
                StyleConstants.getComponent(delegate).setVisible(viewComponents);
            } else {
                delegate.removeAttributes(deniability.getAttributeNames());
            }

            return delegate;
        }
    }

    /**
     * Restores the state based on the inicial values given in processAttributes
     * and the current denial state. The current state is lost.
     */
    public AttributeSet processAttributes(AttributeSet attributes) {
        AttributeSet cache = (AttributeSet) attributes.getAttribute(DENIAL_KEY);

        if (cache == null || cache.getAttributeCount() == 0) {
            return SimpleAttributeSet.EMPTY;
        } else {
            SimpleAttributeSet delegate = new SimpleAttributeSet(cache);

            for (Object aDeny : deny) {
                delegate.removeAttribute(aDeny);
            }

            delegate.setResolveParent(attributes.getResolveParent());

            if (delegate.isDefined(StyleConstants.ComponentAttribute)) {
                StyleConstants.getComponent(delegate).setVisible(viewComponents);
            } else {
                delegate.removeAttributes(deniability.getAttributeNames());
            }

            delegate.addAttribute(DENIAL_KEY, cache);
            return delegate;
        }
    }

    /**
     * Sets the character attributes that are always removed
     * form the registered attributesets
     * @param o
     */
    public void denyCharacterAttributes(final Object[] o) {
        if (o != null) {
            deny = o;
        }
    }

    /**
     * A way to see if the class is allowing the showing of
     * a constant. Normally they are StyleConstants.
     */
    public boolean isShowing(final Object constant) {
        if (constant.equals(StyleConstants.ComponentAttribute)) {
            return viewComponents;
        }
        boolean isShowing = !deniability.isDefined(constant);
        for (int i = 0; i < deny.length && isShowing; i++) {
            isShowing = !deny[i].equals(constant);
        }
        return isShowing;
    }

    public void showBold(final boolean showBold) {
        if (showBold) {
            deniability.removeAttribute(StyleConstants.Bold);
        } else {
            StyleConstants.setBold(deniability, true);
        }
    }

    public void showComponents(final boolean showComponent) {
        viewComponents = showComponent;
    }

    public void showFonts(final boolean showFonts) {
        if (showFonts) {
            deniability.removeAttribute(StyleConstants.FontFamily);
        } else {
            StyleConstants.setFontFamily(deniability, "");
        }
    }

    public void showForeground(final boolean showForeground) {
        if (showForeground) {
            deniability.removeAttribute(StyleConstants.Foreground);
        } else {
            StyleConstants.setForeground(deniability, Color.BLACK);
        }
    }

    public void showItalic(final boolean showItalic) {
        if (showItalic) {
            deniability.removeAttribute(StyleConstants.Italic);
        } else {
            StyleConstants.setItalic(deniability, true);
        }
    }

    public void showStrikeThrough(final boolean showStrikeThrough) {
        if (showStrikeThrough) {
            deniability.removeAttribute(StyleConstants.StrikeThrough);
        } else {
            StyleConstants.setStrikeThrough(deniability, true);
        }
    }

    public void showUnderline(final boolean showUnderline) {
        if (showUnderline) {
            deniability.removeAttribute(StyleConstants.Underline);
        } else {
            StyleConstants.setUnderline(deniability, true);
        }
    }
}
