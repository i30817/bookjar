package i3.ui.parsers;

import java.util.EnumMap;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import i3.parser.BookLoader;
import i3.parser.HtmlLoader;
import i3.parser.Property;
import i3.parser.RtfLoader;
import i3.parser.TxtLoader;

/**
 *
 * @author Owner
 */
public class ReparserTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    private EnumMap helper(boolean reparse) {
        EnumMap p = new EnumMap(Property.class);
        p.put(Property.REFORMAT, reparse);
        return p;
    }

    /**
     * Test of reParse method, of class IReparser.
     */
    @Test
    public synchronized void reParse() throws BadLocationException {
        System.out.println("reParse");
        //Easier to use the document factory to create and reparse the document
        BookLoader instance = new TxtLoader();

        String test = "";
        System.err.println("Testing empty document file");
        StyledDocument result = instance.create(test, helper(true));
        assertEquals("length "+0+" expected", 0, result.getLength());
        String text = result.getText(0, result.getLength());
        assertEquals("", text);

        test = "A";
        System.err.println("Testing 1 char document file");
        result = instance.create(test, helper(true));
        text = result.getText(0, result.getLength());

        assertEquals("A\n", text);

        test = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<!DOCTYPE html PUBLIC><html><head></head><body>" + "<p class=MsoNormal>\t\r\n </p>" +//whitespace
                //these nonbreaking white spaces are not currently parsed
                //&nbsp;\u2027\u2007\u202F</p>"//they slow things.
                "<p class=MsoNormal>wasn’t</p>" + "\n\n\n" + "<p class=MsoNormal>- difficult. Busi­ness    is good</p>" + "</body></html>";
        System.err.println("Testing forbidden char \\\\U00" + Integer.toHexString((int) '­').toUpperCase());
        System.err.println("Testing html space and paragraph merging");
        instance = new HtmlLoader();
        result = instance.create(test, helper(true));
        text = result.getText(0, result.getLength());

        assertEquals("wasn’t - difficult. Business is good\n", text);

        test = "{\\rtf1\\fbidis\\ansi\\ansicpg1252\\deff0\\deflang3084\\deflangfe3084{" + "\\fonttbl{\\f0\\froman\\fprq2\fcharset0 Fournier MT Std Regular;}}" + "\\viewkind4\\uc1\\pard\\ltrpar\\fi227\\qr\\lang1033\\kerning16\\f0\\fs18\\par" + "\\pard\\ltrpar\\fi227\\sb160\\sa120\\qr Saint Augustine has seen that one" + "\\par labors in uncertainty at sea\\par and in battles and in " + "all the rest,\\par but he has not seen the rules of the game.\\par}";
        instance = new RtfLoader();
        result = instance.create(test, helper(true));
        text = result.getText(0, result.getLength());
        System.err.println("Testing rtf space and paragraph merging");
        assertEquals("Saint Augustine has seen that one labors in uncertainty at " + "sea and in battles and in all " + "the rest, but he has not seen the rules of the game.\n", text);


        test = "{\\rtf1\\fbidis\\ansi\\ansicpg1252\\deff0\\deflang3084\\deflangfe3084{" + "\\fonttbl{\\f0\\froman\\fprq2\fcharset0 Fournier MT Std Regular;}}" + "\\viewkind4\\uc1\\pard\\ltrpar\\fi227\\qr\\lang1033\\kerning16\\f0\\fs18\\par" + "\\pard\\ltrpar\\fi227\\sb160\\sa120\\qr Saint Augustine has seen that one" + "\\line labors in uncertainty at sea\\line and in battles and in " + "all the rest,\\line but he has not seen the rules of the game.\\par}";


        result = instance.create(test, helper(true));
        text = result.getText(0, result.getLength());
        System.err.println("Testing rtf space and line merging");
        assertEquals("Saint Augustine has seen that one labors in uncertainty at sea and in battles and in all " + "the rest, but he has not seen the rules of the game.\n", text);
    }
}
