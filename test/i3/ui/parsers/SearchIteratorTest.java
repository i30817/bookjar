package i3.ui.parsers;

import i3.swing.SearchIterator;
import java.util.EnumMap;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import i3.parser.Property;
import i3.parser.TxtLoader;

/**
 *
 * @author bio-aulas
 */
public class SearchIteratorTest {

    private SearchIterator it;
    private StyledDocument testDoc;

    private EnumMap helper(boolean reparse) {
        EnumMap p = new EnumMap(Property.class);
        p.put(Property.REFORMAT, reparse);
        return p;
    }

    public SearchIteratorTest() {
        TxtLoader l = new TxtLoader();
        testDoc = l.create("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus vel urna sit amet mauris molestie sollicitudin vitae at eros. Aliquam at mi ut eros placerat malesuada. Morbi rutrum leo nec nunc porta ullamcorper. Integer mi magna, tristique eu vulputate ut, porta eu ipsum. Aliquam et congue urna. Duis tortor mauris, luctus sit amet vehicula eget, tempus nec tortor. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Etiam bibendum nisl a lacus laoreet ac molestie urna pharetra. In sollicitudin mi at lorem pharetra id blandit enim malesuada. Etiam nisi sapien, ultrices eget consequat quis, facilisis eget odio." + "In sapien leo, suscipit nec fringilla ut, sollicitudin in elit. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Proin sed hendrerit tortor. Nulla facilisi. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Vestibulum faucibus feugiat tincidunt. Morbi cursus tellus nec justo egestas a imperdiet felis blandit. Praesent sed lobortis magna. Aliquam fringilla, tortor quis aliquet ornare, nulla nisl congue risus, quis dictum dui justo eget massa. Praesent malesuada vehicula velit vitae iaculis. Cras condimentum sodales turpis eu ornare. Proin arcu leo, blandit eu euismod eu, accumsan ac purus. Proin felis sapien, eleifend ultrices commodo ac, malesuada sit amet velit. Integer eget sapien urna. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae;" + "Fusce elementum lacinia nisi id hendrerit. Pellentesque sollicitudin, leo eu congue placerat, orci orci fermentum odio, sed adipiscing orci nulla at risus. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur placerat lorem sit amet velit dapibus egestas. Vivamus vel libero nisi. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Curabitur sit amet elit sed ligula varius sodales. Sed sit amet elit mi, et ornare enim. Quisque diam mauris, tincidunt a tincidunt ac, gravida vel leo. Quisque fringilla viverra enim. Sed sit amet nulla nisl. In ipsum lectus, lacinia ut vulputate vitae, pharetra nec massa. Quisque sit amet lacus metus, id pharetra magna. Nunc justo metus, pretium in gravida quis, fermentum eget libero. Fusce ut nisl sit amet libero interdum aliquet. In varius pellentesque vehicula. Aliquam erat volutpat." + "Phasellus id libero mi, eget ornare velit. Aenean ullamcorper hendrerit gravida. Nullam tempus euismod mauris, non aliquam urna dignissim aliquet. Donec odio augue, egestas quis aliquam varius, aliquam non quam. Duis at orci eu urna dapibus malesuada. Etiam tellus odio, vulputate non dapibus a, vestibulum sed erat. Vivamus suscipit odio id tortor viverra lacinia. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Curabitur tempor hendrerit nisl ac consectetur. Sed quam tellus, elementum eget venenatis in, mattis vel lectus. Cras in velit sem, non ultrices nibh. Proin facilisis massa in enim aliquam vel venenatis nulla elementum. Cras pellentesque neque sed ante molestie feugiat. Sed mattis ultricies diam, vel venenatis lacus vestibulum a. Nam interdum enim ut justo blandit commodo." + "Nullam rutrum ullamcorper augue ut lacinia. Suspendisse potenti. Donec neque dolor, vehicula at sagittis a, ultrices vitae dui. Etiam id enim magna. Proin vitae elementum turpis. Suspendisse potenti. Cras et iaculis felis. Vestibulum eget fringilla odio. Nunc rutrum arcu id massa dignissim eu cursus sapien accumsan. Ut et ante et dui fermentum bibendum nec id arcu. Pellentesque non enim pretium est tincidunt lacinia. Quisque dignissim libero eget mi ullamcorper laoreet. Aliquam erat volutpat. Nunc magna nisi, rutrum eget euismod at, dictum bibendum eros. Aenean sem sapien, ultrices id laoreet sed, fermentum venenatis nulla." + "Nam mi quam, pellentesque euismod faucibus id, semper vel massa. Nunc sem mauris, ultricies eget dignissim eget, laoreet a massa. In magna magna, dictum sit amet viverra ac, sollicitudin vitae augue. Morbi eleifend, sem sit amet convallis ultrices, odio felis pharetra magna, at fringilla ligula nulla vel quam. Ut eu dictum metus. Sed a magna at urna scelerisque sollicitudin et a mauris. Morbi sit amet nunc orci, dictum tincidunt urna. Nulla nec dui nunc. Vivamus non libero vel nunc eleifend dictum eu vitae odio. Integer imperdiet malesuada mi quis tincidunt." + "Mauris egestas quam sed arcu adipiscing elementum. Aliquam lacinia eros nec ligula lacinia eget rhoncus nunc venenatis. Donec a arcu magna. Duis vitae diam sem, et adipiscing lorem. Sed et velit orci. Mauris semper aliquet dolor ut luctus. Sed ligula quam, ornare a interdum ultricies, tempus ut felis. Aenean ac tellus sapien. Phasellus neque velit, tempor sit amet ultricies sit amet, lacinia hendrerit lacus. Donec facilisis, elit ac scelerisque eleifend, arcu mauris tristique nisl, a dignissim massa est vitae turpis. Quisque condimentum, risus ac rhoncus suscipit, justo leo ornare erat, at dictum magna ante vel leo. Phasellus id ante fringilla mauris mollis lacinia et vel nibh. Praesent eget nisi purus, eget posuere augue. Sed non ornare arcu. Morbi quis velit tellus, eget scelerisque velit. Vivamus id justo massa. Aliquam tortor ipsum, pharetra vel sagittis ut, pulvinar in tellus. Nam vitae lectus sit amet lacus pretium ultricies. Donec vehicula dapibus auctor. Aenean ipsum metus, convallis euismod aliquet eget, accumsan ac leo." + "Cras augue mi, tincidunt sit amet venenatis dictum, adipiscing quis felis. Pellentesque venenatis lacus mattis purus dignissim ornare. Etiam sit amet molestie dolor. Curabitur condimentum libero id mauris ornare sit amet gravida lacus egestas. Sed eget sapien nunc. Nam urna nibh, tristique ac imperdiet placerat, mollis ut tellus. Suspendisse potenti. Phasellus tincidunt rhoncus aliquam. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. In hac habitasse platea dictumst." + "Curabitur posuere varius quam condimentum pretium. Nulla nec diam quis augue dignissim tempus at et sapien. Sed turpis neque, viverra at auctor non, vehicula nec tellus. Suspendisse pellentesque sem sit amet purus cursus ac gravida massa fringilla. Etiam mattis sem sollicitudin metus tempor in tempus neque ultricies. Aliquam quis risus vel lectus tempor pellentesque. Phasellus vestibulum libero vel elit euismod porta. Praesent tempus turpis in nisl imperdiet eu egestas metus feugiat. Nulla est nisl, pellentesque scelerisque mattis nec, tincidunt ac nunc. Ut accumsan dui et quam adipiscing eget laoreet justo volutpat. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Vestibulum iaculis, risus sit amet euismod fringilla, dui quam suscipit risus, vel rutrum quam ligula nec nunc. Aliquam ac augue non nisi lacinia varius a sed libero. Etiam tincidunt arcu vitae odio condimentum venenatis. Curabitur ac nibh nec urna bibendum fermentum a at quam. Sed faucibus faucibus pharetra." + "Morbi nec erat lacus, id bibendum erat. Vivamus enim ligula, dapibus vel tempor vitae, convallis a augue. Fusce sed ante ut augue convallis facilisis. Nullam ante sapien, tristique quis sagittis ut, sodales at felis. Curabitur vel magna nec nunc aliquam vulputate tristique et massa. Ut at nulla vitae mauris ornare auctor. Pellentesque felis quam, pharetra sed accumsan sit amet, dictum rutrum eros. Aliquam placerat ligula non orci facilisis eget pharetra diam eleifend. Praesent egestas dui ornare orci scelerisque venenatis. Morbi et posuere sapien. Praesent ac consectetur sapien. Mauris id purus nisl. Nullam a metus diam. Nullam volutpat pharetra purus, a aliquam urna luctus sed. Sed ligula augue, porta eu facilisis vitae, gravida id lorem. Maecenas dapibus sollicitudin arcu ut pellentesque. Integer est velit, fringilla sed accumsan ut, malesuada id erat. Etiam at porttitor turpis. Cras a ornare mi. ", helper(false));
        it = new SearchIterator("", testDoc, 0);
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
    public void testSearch() throws BadLocationException {
        System.out.println("hasNext");
        testFindWordFromStart("amet", 23);
        testFindWordFromStart("Lorem", 2);
        testFindWordFromStart("lorem", 4);
        testFindWordFromStart("sapien", 12);
        //must be simmetrical (so that words don't get counted twice)
        testFindWordFromEnd("amet", 23);
        testFindWordFromEnd("Lorem", 2);
    }

    public void testFindWordFromStart(String word, int expectedFinds) throws BadLocationException {
        it.setSearchText(word);
        it.setSearchIndex(0);
        int count = 0;
        int len = word.length();
        while (it.hasNext()) {
            count++;
            int place = it.next();
            assertEquals(word, testDoc.getText(place, len));
        }
        assertEquals("Searching " + word, expectedFinds, count);
        count = 0;
        while (it.hasPrevious()) {
            count++;
            int place = it.previous();
            assertEquals(word, testDoc.getText(place, len));
        }
        //same as expected - 1 since the current doesn't count
        assertEquals("Backwards searching " + word, expectedFinds - 1, count);
    }

    private void testFindWordFromEnd(String word, int expectedFinds) throws BadLocationException {
        it.setSearchText(word);
        it.setSearchIndex(testDoc.getLength());
        int count = 0;
        int len = word.length();

        while (it.hasPrevious()) {
            count++;
            int place = it.previous();
            assertEquals(word, testDoc.getText(place, len));
        }
        assertEquals("Backwards searching " + word, expectedFinds, count);

        count = 0;
        while (it.hasNext()) {
            count++;
            int place = it.next();
            assertEquals(word, testDoc.getText(place, len));
        }

        //same as expected - 1 since the current doesn't count
        assertEquals("Searching " + word, expectedFinds - 1, count);
    }
}
