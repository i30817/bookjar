package i3.parser;



import java.io.BufferedReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.DefaultStyledDocument.ElementSpec;
import javax.swing.text.Element;
import static org.junit.Assert.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ParserDocumentTest {

    public ParserDocumentTest() {
    }

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

    @Test
    public void insertString() throws Exception {
        final String what = "what";
        final String whitespace = " ";
        ParserDocument instance = new ParserDocument();
        ElementSpec[] data = new ElementSpec[3];
        fillData(data, what, instance);
        instance.insert(0, data);
        assertEquals(what, instance.getText(0, instance.getLength()));
        //test a index equal the length of the document (smaller would throw exception).
        fillData(data, whitespace, instance);
        instance.insert(instance.getLength(), data);
        assertEquals(what + whitespace, instance.getText(0, instance.getLength()));
    }

    @Test(expected = BadLocationException.class)
    public void overflowInsert() throws Exception {
        final String what = "what";
        ParserDocument instance = new ParserDocument();
        ElementSpec[] data = new ElementSpec[3];
        fillData(data, what, instance);
        instance.insert(1, data);
    }

    public void impliedNewline() throws Exception {
        ParserDocument instance = new ParserDocument();
        assertEquals(instance.getText(0, instance.getLength()) + "\n", instance.getText(0, instance.getLength() + 1));
        final String what = "what";
        ElementSpec[] data = new ElementSpec[3];
        fillData(data, what, instance);
        instance.insert(0, data);
        assertEquals(instance.getText(0, instance.getLength()) + "\n", instance.getText(0, instance.getLength() + 1));

        DefaultStyledDocument d = new DefaultStyledDocument();
        d.insertString(0, "", null);
        assertEquals(d.getText(0, instance.getLength()) + "\n", d.getText(0, instance.getLength() + 1));
        d.insertString(0, what, null);
        assertEquals(d.getText(0, instance.getLength()) + "\n", d.getText(0, instance.getLength() + 1));
    }

    @Test
    public void overflowGetText() throws Exception {
        DefaultStyledDocument d = new DefaultStyledDocument();
        d.insertString(0, "", null);
        ParserDocument i = new ParserDocument();
        i.insertString(0, "", null);
        //both have implied new line and is accessible by get text
        assertEquals(i.getText(0, 1), d.getText(0, 1));
        d.insertString(0, " ", null);
        i.insertString(0, " ", null);
        assertEquals(i.getText(0, 2), d.getText(0, 2));
    }

    @Test(expected = BadLocationException.class)
    public void overflowGetText2() throws Exception {
        DefaultStyledDocument d = new DefaultStyledDocument();
        d.insertString(0, "", null);
        //more than the implied new line throws a badlocationexception
        d.getText(0, 2);
    }

    @Test(expected = BadLocationException.class)
    public void overflowGetText3() throws Exception {
        ParserDocument d = new ParserDocument();
        d.insertString(0, "", null);
        //more than the implied new line throws a badlocationexception
        d.getText(0, 2);
    }

    @Test
    public void lengthEqual() throws BadLocationException {
        ParserDocument d = new ParserDocument();
        DefaultStyledDocument s = new DefaultStyledDocument();
        assertEquals(s.getLength(), d.getLength());
        d.insertString(0, "", null);
        assertEquals(s.getLength(), d.getLength());
        d.insertString(0, " ", null);
        s.insertString(0, " ", null);
        assertEquals(s.getLength(), d.getLength());
    }

    private void fillData(ElementSpec[] data, String s, ParserDocument instance) {
        data[0] = new ElementSpec(null, ElementSpec.ContentType, s.toCharArray(), 0, s.length());
        data[1] = new ElementSpec(null, ElementSpec.EndTagType);
        //Every non first Element needs a start type so the stack based document tree doesn't go bonkers
        //Paragraph element is aleatory, using the first element to reuse the object.
        Element paragraph = instance.getParagraphElement(0);
        AttributeSet pattr = paragraph.getAttributes();
        data[2] = new ElementSpec(pattr, ElementSpec.StartTagType);
    }

    /**
     * Test of insert method performance and memory
     */
    //@Test
    public void performanceTest() throws Exception {
        List<ElementSpec> l = new ArrayList<>();

        try (BufferedReader r = Files.newBufferedReader(Paths.get("War and Peace.txt"), Charset.forName("us-ascii"))) {
            while (r.ready()) {
                String line = r.readLine();
                l.add(new ElementSpec(null, ElementSpec.StartTagType));
                l.add(new ElementSpec(null, ElementSpec.ContentType, line.toCharArray(), 0, line.length()));
                l.add(new ElementSpec(null, ElementSpec.EndTagType));
            }
        }

        OldTestDocument oldDoc = new OldTestDocument();
        ParserDocument newDoc = new ParserDocument();
        ElementSpec[] arr = (ElementSpec[]) l.toArray(new ElementSpec[0]);
        long time, elapsedTime1, elapsedTime2;
        ThreadMXBean t = ManagementFactory.getThreadMXBean();

        time = t.getCurrentThreadCpuTime();
        oldDoc.insert(0, arr);
        elapsedTime2 = t.getCurrentThreadCpuTime() - time;

        time = t.getCurrentThreadCpuTime();
        newDoc.insert(0, arr);
        elapsedTime1 = t.getCurrentThreadCpuTime() - time;

        assert elapsedTime1 < elapsedTime2 : "old style insert took less time (" + (elapsedTime2) + ") than new style (" + (elapsedTime1) + ")";;
    }

    //@Test
    public void memoryTest() throws Exception {
        List<ElementSpec> l = new ArrayList<>();

        try (BufferedReader r = Files.newBufferedReader(Paths.get("War and Peace.txt"), Charset.forName("us-ascii"))) {
            while (r.ready()) {
                String line = r.readLine();
                l.add(new ElementSpec(null, ElementSpec.StartTagType));
                l.add(new ElementSpec(null, ElementSpec.ContentType, line.toCharArray(), 0, line.length()));
                l.add(new ElementSpec(null, ElementSpec.EndTagType));
            }
        }

        OldTestDocument oldDoc = new OldTestDocument();
        ParserDocument newDoc = new ParserDocument();
        ElementSpec[] arr = (ElementSpec[]) l.toArray(new ElementSpec[0]);
        long mem, memory1= 0, memory2 = 0;
        MemoryMXBean m = ManagementFactory.getMemoryMXBean();
        //using non-heap memory because we are testing the memory impact of the
        //stringbuilder inside DefaultStyledDocument.insert(int offset, ElementSpec[] data)

        m.gc();m.gc();m.gc();m.gc();
        m.gc();m.gc();m.gc();m.gc();
        m.gc();m.gc();m.gc();m.gc();
        m.gc();m.gc();m.gc();m.gc();
        mem = m.getNonHeapMemoryUsage().getUsed();
        oldDoc.insert(0, arr);
        memory2 = m.getNonHeapMemoryUsage().getUsed() - mem;

        m.gc();m.gc();m.gc();m.gc();
        m.gc();m.gc();m.gc();m.gc();
        m.gc();m.gc();m.gc();m.gc();
        m.gc();m.gc();m.gc();m.gc();
        mem = m.getNonHeapMemoryUsage().getUsed();
        newDoc.insert(0, arr);
        memory1 = m.getNonHeapMemoryUsage().getUsed() - mem;
        //don't allow it to free after use
        oldDoc.getClass();
        newDoc.getClass();
        System.out.println("newDocument: "+memory1+" oldDocument: "+memory2);
        assert memory1 < memory2 : "old style insert took less memory (" + (memory2) + ") than new style (" + (memory1) + ")";
    }

    class OldTestDocument extends DefaultStyledDocument {

        @Override
        public void insert(int offset, ElementSpec[] data) throws BadLocationException {
            super.insert(offset, data);
        }
    }
}
