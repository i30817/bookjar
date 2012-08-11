package i3.ui.parsers;

import java.util.EnumMap;
import java.util.LinkedList;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTML;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import i3.parser.HtmlLoader;
import i3.parser.Property;

/**
 *
 * @author Owner
 */
public class DocumentFactoryTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void createWithMyDocument() throws BadLocationException {
        System.out.println("create");
        HtmlLoader instance = new HtmlLoader();
        doTest(instance, false);
//        doTest(instance, true);
    }

    private EnumMap helper(boolean reparse) {
        EnumMap p = new EnumMap(Property.class);
        p.put(Property.REFORMAT, reparse);
        return p;
    }

//    @Test
    public void duplicateBodyCrashesSunParser(){
        System.out.println("duplicateBodyCrashesSunParser");
        HtmlLoader instance = new HtmlLoader();
        final String string = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<!DOCTYPE html PUBLIC><html><head></head><body>" +
                replicate("<p>Hur dur!</p>", 60000)+
                "<p></body><h1>It's a bug! kill it</h1></body></html>";
        StyledDocument result = instance.create(string, helper(false));
        //no exception means i fixed this...
    }

    private void doTest(HtmlLoader instance, boolean reparse) throws BadLocationException {
        final String ref = "ref";
        final String ref2 = "ref2";
        final String wierd1 = "wierd1";
        final String wierd2 = "wierd2";

        final String string =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<!DOCTYPE html PUBLIC><html><head></head><body>" +
                "<a href=#ref><p>Link failure</p></a>" +
                "<p>No Link</p>" +
                "<a href=#ref2><p>Link</p></a>" +
                "<a name=ref2><p>Real    Link</p></a>" +
                "<a name=wierd1></a>" +
                "<a name=wierd2></a>" +
                "<p>Only one Link?</p>" +
                "<a href=#wierd1><p>Edge Case 1</p></a>" +
                "<a href=#wierd2><p>Edge Case 2</p></a>" +
                "</body></html>";

        StyledDocument result = instance.create(string, helper(reparse));
        assertNotSame(result.getLength(), 0);
        String text;
        Element e = result.getCharacterElement(0);
        //paragraph 1 - Link Failure - the href exists but no name
        e = checkElementAndReturnNext(result, e, ref, null);
        //\n
        e = checkElementAndReturnNext(result, e, null, null);
        //paragraph 2 - No Link - has no href
        e = checkElementAndReturnNext(result, e, null, null);
        //\n
        e = checkElementAndReturnNext(result, e, null, null);
        //paragraph 3 - Link - the href exists
        e = checkElementAndReturnNext(result, e, ref2, null);
        //\n
        e = checkElementAndReturnNext(result, e, null, null);
        //paragraph 4 - Real    Link - the name exists
        LinkedList names = new LinkedList();
        names.add(ref2);
        e = checkElementAndReturnNext(result, e, null, names);
        //\n
        e = checkElementAndReturnNext(result, e, null, null);
        //paragraph 5 - Only one Link? - broken up by htmlCallback
        //to set the links here.
        e = checkElementAndReturnNext(result, e, null, null);
        //paragraph 5 - Only one Link? - second part
        names.clear();
        names.add(wierd1);
        names.add(wierd2);
        e = checkElementAndReturnNext(result, e, null, names);
    }


    private Element checkElementAndReturnNext(StyledDocument doc, Element e, String urlLink, Object urlNames) throws BadLocationException{
        AttributeSet s = e.getAttributes();
        String text = doc.getText(e.getStartOffset(), e.getEndOffset() - e.getStartOffset());
        if(urlLink == null){
            assertNull("Element in \""+text+"\" was supposed null href, but wasn't "+s.getAttribute(HTML.Attribute.HREF), s.getAttribute(HTML.Attribute.HREF));
        }else{
            assertNotNull("Element in \""+text+"\" has null href", s.getAttribute(HTML.Attribute.HREF));
            assertEquals(urlLink, s.getAttribute(HTML.Attribute.HREF));
        }
        if(urlNames == null){
            assertNull("Element in \""+text+"\" was supposed null name, but wasn't"+s.getAttribute(HTML.Attribute.NAME), s.getAttribute(HTML.Attribute.NAME));
        }else{
            assertNotNull("Element in \""+text+"\" has null href", s.getAttribute(HTML.Attribute.NAME));
            assertEquals(urlNames, s.getAttribute(HTML.Attribute.NAME));
        }
        return doc.getCharacterElement(e.getEndOffset());
    }

    private String replicate(String string, int e) {
        StringBuilder b = new StringBuilder();

        for(int i = 0; i < e; i++)
            b.append(string);
        return b.toString();
    }
}
