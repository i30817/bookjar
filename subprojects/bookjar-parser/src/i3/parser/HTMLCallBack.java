package i3.parser;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTML.Attribute;
import javax.swing.text.html.HTML.Tag;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Parses html to get the text and images
 * saves the a list of the current names of <a name="names"></a>
 * on the attribute HTML.Attribute.NAME for the revelant attributesets
 * The locations for inside links are inside the document properties.
 *
 * So if you want to a link - access the clicked attributeset,
 * get the name element of the list (the one you want):
 * Object key = att.getAttribute(HTML.Attribute.HREF);
 * Then get the correct index :
 * Integer i = (Integer) doc.getProperty(key);
 *
 * @author i30817
 *
 */
public class HTMLCallBack implements ContentHandler {

    private final BufferedStyledDocumentBuilder builder;
    private final Color linkColor;
    private final MutableAttributeSet memory = new SimpleAttributeSet();
    private final List<String> hrefList = new LinkedList<>();
    private final Map<String, Integer> nameMap = new HashMap<>();
    private boolean linkVisited = false, notFoundBodyOnce = true;

    /**
     * Link color can be mutable by subclassing Color.
     * @param document to fill
     * @param linkColor a color for the links.
     */
    public HTMLCallBack(DefaultStyledDocument doc, Color linkColor) {
        super();
        builder = new BufferedStyledDocumentBuilder(doc);
        this.linkColor = linkColor == null ? Color.BLACK : linkColor;
    }

    public DefaultStyledDocument getDocument() {
        return builder.getDocument();
    }

    private void commitNames() {
        for (String hrefName : hrefList) {
            Integer index = nameMap.get(hrefName);
            if (index == null) {
                continue;
            }

            Element element = builder.getDocument().getCharacterElement(index);
            AttributeSet set = element.getAttributes();
            //The lists are needed since there is a weird edge case
            //when two hrefs from hreflist can point to the same place.
            //because the names have no "real" text between them
            //so they shadow. Then if you move the text around
            //it will break when rebuilding.
            if (set.isDefined(Attribute.NAME)) {
                ((List) set.getAttribute(Attribute.NAME)).add(hrefName);
            } else {
                SimpleAttributeSet indexAttributes = new SimpleAttributeSet();
                List list = new LinkedList();
                list.add(hrefName);
                indexAttributes.addAttribute(Attribute.NAME, list);
                builder.getDocument().setCharacterAttributes(element.getStartOffset(), 1, indexAttributes, false);
            }
            builder.getDocument().putProperty(hrefName, index);
        }
    }

    private void setMemory(Tag tag, Attributes attributes) {
        String attribute = attributes.getValue("id");
        addNameAttribute(attribute);
        if (notFoundBodyOnce && tag == Tag.BODY) {
            builder.clear();
            notFoundBodyOnce = false;
        } else if (tag == Tag.I || tag == Tag.EM) {
            StyleConstants.setItalic(memory, true);
        } else if (tag == Tag.B) {
            StyleConstants.setBold(memory, true);
        } else if (tag == Tag.U) {
            StyleConstants.setUnderline(memory, true);
        } else if (tag == Tag.S || tag == Tag.STRIKE) {
            StyleConstants.setStrikeThrough(memory, true);
        } else if (tag == Tag.FONT) {
            attribute = attributes.getValue("face");
            if (attribute != null) {
                StyleConstants.setFontFamily(memory, attribute);
            }
            Color color = decodeColor(attributes.getValue("color"));
            if (color != null) {
                StyleConstants.setForeground(memory, color);
            }
        } else if (tag == Tag.A) {
            attribute = attributes.getValue("name");
            addNameAttribute(attribute);
            attribute = attributes.getValue("href");
            if (attribute == null) {
                return;
            }
            //Don't allow internet links
            int index = attribute.indexOf('#');
            if ((index == 0 && attribute.length() > 1) || attribute.startsWith((String) getDocument().getProperty("filename"))) {
                //inside file link
                attribute = attribute.substring(index + 1);
                hrefList.add(attribute);
                memory.addAttribute(Attribute.HREF, attribute);
                StyleConstants.setUnderline(memory, true);
                StyleConstants.setForeground(memory, linkColor);
                linkVisited = true;
            }
        }
    }

    private void eraseMemory(Tag tag) {
        if (tag == Tag.I || tag == Tag.EM) {
            memory.removeAttribute(StyleConstants.Italic);
        } else if (tag == Tag.B) {
            memory.removeAttribute(StyleConstants.Bold);
        } else if (tag == Tag.U) {
            memory.removeAttribute(StyleConstants.Underline);
        } else if (tag == Tag.S || tag == Tag.STRIKE) {
            memory.removeAttribute(StyleConstants.StrikeThrough);
        } else if (tag == Tag.FONT) {
            memory.removeAttribute(StyleConstants.FontFamily);
            memory.removeAttribute(StyleConstants.Foreground);
        }
    }

    private void addNameAttribute(String name) {
        //only save the first time we encounter a name...
        if (name == null || nameMap.containsKey(name)) {
            return;
        }
        nameMap.put(name, builder.getLength());
    }

    private Color decodeColor(String code) {
        if (code != null) {
            code = code.trim();
            try {
                /*Try as a normal String, public fields, so it should not get a security exception*/
                return (Color) Color.class.getField(code.toLowerCase(Locale.ENGLISH)).get(Color.RED);
            } catch (Exception e) {
                try {
                    /*Try as a number*/
                    return Color.decode(code);
                } catch (NumberFormatException f) { /*Give up*/
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    public void endDocument() throws SAXException {
        builder.commit();
        commitNames();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        Tag tag = HTML.getTag(qName);
        if (tag == null) {
            return;
        }

        if (tag == Tag.IMG) {
            String alt = atts.getValue("alt");
            //couldn't care less about images that are not links
            if (alt != null && memory.isDefined(Attribute.HREF)) {
                char[] altChars = alt.toCharArray();
                characters(altChars, 0, altChars.length);
                characters(new char[]{' '}, 0, 1);
            }
        } else {
            setMemory(tag, atts);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        Tag tag = HTML.getTag(qName);
        if (tag == null) {
            return;
        }

        eraseMemory(tag);
        if (tag.isBlock() && !tag.breaksFlow()) {
            builder.appendSpace(memory);
        }
        if (tag.breaksFlow()) {
            builder.appendEnd(memory);
        }
    }

    @Override
    public void characters(char[] ch, int index, int length) throws SAXException {
//    System.out.println("handleText("+new String(ch, index, length)+")");

        //the parser reuses the array at the same indexes - copy it.
        char[] copy = Arrays.copyOfRange(ch, index, index + length);
        builder.append(copy, memory);
        //Only erase <a> attributes if we encountered real text.
        //This means that we can't start and end a link on whitespace.
        if (linkVisited) {
            boolean needsCleanup = false;

            for (int i = ch.length - 1; i >= index; i--) {
                if (ch[i] != ' ') {
                    needsCleanup = true;
                    break;
                }
            }
            if (needsCleanup) {
                memory.removeAttribute(StyleConstants.Underline);
                memory.removeAttribute(Attribute.HREF);
                memory.removeAttribute(StyleConstants.Foreground);
                linkVisited = false;
            }
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    }

    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
    }

    @Override
    public void setDocumentLocator(Locator locator) {
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
    }
}
