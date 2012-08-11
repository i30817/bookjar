package i3.parser;

import javax.swing.text.DefaultStyledDocument;

public interface ParserListener {

    void startDocument(DefaultStyledDocument doc);

    void endDocument(DefaultStyledDocument doc);

}
