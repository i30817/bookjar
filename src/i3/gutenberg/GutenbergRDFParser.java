package i3.gutenberg;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.digester.Digester;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.xml.sax.SAXException;

/**
 * This class parses and stuffs some attributes from the Project Gutenberg RDF
 * file into a Lucene database (backed by SOLR), to allow offline searches of
 * the pg catalog. The attributes are : Authors and Collaborators (searchable)
 * Title (searchable) File extent, mimetype and download link (not searchable)
 *
 * It is not thread safe and you can't reuse it, so don't even try. Always new.
 *
 * @author paulo
 */
class GutenbergRDFParser {

    private IndexWriter index;
    //the first file id in the gutenberg rdf.
    private long currentFileId = 1L;
    private List<File> bestFileCandidates = new ArrayList<>();
    //Not threadsafe obviously.
    public final static StringBuilder auxString = new StringBuilder();
    //Alas, this needs to be kept in memory because the PG rdf is crazyyyy.
    //(it has two distinct sections with a foreign key).
    private static Map<Long, Book> crazyMemoryLeak = new HashMap<>();
    //help a little with the memory: a map of Master strings for authors, languages and LCSH.
    private static Map<String, String> interned = new HashMap<>();

    public GutenbergRDFParser(IndexWriter lucene) {
        this.index = lucene;
    }

    public void parse(InputStream xml) throws IOException {

        try {
            //XMLReaderFactory.createXMLReader().
            Digester d = new Digester();

            d.push(this);

            d.addObjectCreate("rdf:RDF/pgterms:etext", Book.class);
            //addSetProperty is not working (bug in version 2.1 of digester)
            d.addSetProperties("rdf:RDF/pgterms:etext", "rdf:ID", "id");
            /**
             * When there are two kinds of tags, one which is singular
             * ("Literal") another that holds a collection, unfortunately, due
             * to the way Digester works, if one is a prefix of the other, you
             * will get a duplicate call in the larger string for the smaller
             * string callback. In this case the passed strings are null, and
             * that is the only case that happens, so you can test and ignore
             * null in a unified callback.
             */
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:creator", "setCreator", 0);
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:creator/rdf:Bag/rdf:li", "setCreator", 0);
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:contributor", "setContributor", 0);
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:contributor/rdf:Bag/rdf:li", "setContributor", 0);
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:title", "setTitle", 0);
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:title/rdf:Bag/rdf:li", "setTitle", 0);

            //not affected by the effect commented above (first not a subtring of the second)
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:language/dcterms:ISO639-2/rdf:value", "setLanguage", 0);
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:language/rdf:Bag/rdf:li/dcterms:ISO639-2/rdf:value", "setLanguage", 0);
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:subject/dcterms:LCSH/rdf:value", "setLCSH", 0);
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:subject/rdf:Bag/rdf:li/dcterms:LCSH/rdf:value", "setLCSH", 0);
            /**
             * If it has the type tag, it is certainly not a book. However not
             * all not-a-book have that tag. You need to check the mimetypes in
             * the download later. Much love to shitty xml (rdf whatever).
             */
            d.addCallMethod("rdf:RDF/pgterms:etext/dc:type", "setIsNotBook");
            d.addSetNext("rdf:RDF/pgterms:etext", "saveIfABook");

            d.addObjectCreate("rdf:RDF/pgterms:file", File.class);
            d.addSetProperties("rdf:RDF/pgterms:file", "rdf:about", "downloadSuffix");
            d.addCallMethod("rdf:RDF/pgterms:file/dc:format/dcterms:IMT/rdf:value", "setMimeType", 0);
            d.addCallMethod("rdf:RDF/pgterms:file/dcterms:extent", "setExtent", 0);
            d.addSetProperties("rdf:RDF/pgterms:file/dcterms:isFormatOf", "rdf:resource", "id");
            d.addSetNext("rdf:RDF/pgterms:file", "writeBookToLucene");
            d.parse(xml);
            //flush the last.
            writeBookAux();
        } catch (SAXException ex) {
            throw new IOException("Project Gutenberg datafile is corrupt", ex);
        } finally {
            interned = new HashMap<>();
            crazyMemoryLeak = new HashMap<>();
        }
    }

    public void saveIfABook(Book book) {
        if (book.isPossiblyABook) {
            crazyMemoryLeak.put(book.ID, book);
        }
    }

    public void writeBookToLucene(File candidateFile) throws IOException {
        if (candidateFile.isForABook()) {
            if (currentFileId != candidateFile.id) {
                //time to flush
                currentFileId = candidateFile.id;
                writeBookAux();
            }
            bestFileCandidates.add(candidateFile);
        }
    }

    private void writeBookAux() throws CorruptIndexException, IOException {
        //after collecting all editions of a book (they are consecutive)
        //we choose the best for us.
        File bestFile = Collections.max(bestFileCandidates);
        bestFileCandidates.clear();
        Book bk = crazyMemoryLeak.remove(bestFile.id);

        if (bk == null) {
            return;
        }
        String title = bk.joinTitles();
        String languages = bk.joinLanguages();
        String creators = bk.joinCreators();
        String contributors = bk.joinContributors();
        String subjects = bk.joinSubjects();
        String properties = bestFile.joinProperties();
//All of these except the title == null and the last two might happen in the corpus
//        assert (!languages.isEmpty()) : "Added a empty language string, title: " + bk.titles + " id: " + bestFile.id;
//        assert (!creators.isEmpty()) : " Added a empty creators string, title: " + bk.titles + " id: " + bestFile.id;
//        assert (!title.isEmpty()) : "Added a empty title, id: " + bestFile.id;
//        assert (title != null) : "Added a null title, id: " + bestFile.id;
//        assert (properties.split(GutenbergSearch.MULTIPLE_DATA_SEPARATOR).length == 3) : "Added a invalid file, title: " + bk.titles + " id: " + bestFile.id + " properties divided: " + java.util.Arrays.toString(properties.split(GutenbergSearch.MULTIPLE_DATA_SEPARATOR));
//        assert (!properties.split(GutenbergSearch.MULTIPLE_DATA_SEPARATOR)[2].equals("null")) : "Added a invalid file, title: " + bk.titles + " id: " + bestFile.id + " properties: " + properties;

        if (languages.isEmpty() || creators.isEmpty() || title.isEmpty()) {
            return;
        }

        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("creator", creators, Field.Store.YES));
        doc.add(new TextField("contributor", contributors, Field.Store.YES));
        doc.add(new TextField("subject", subjects, Field.Store.YES));
        doc.add(new TextField("language", languages, Field.Store.YES));
        doc.add(new StoredField("metadata", properties));
        index.addDocument(doc);
    }

    /**
     * This class, unlike Book bellow, is short lived. It is used for Digester
     * to put in properties and to sort the most relevant file
     */
    public static class File implements Comparable<File> {

        public String mimeType, downloadSuffix;
        public boolean isCompressed;
        public long extent, id;
        public int sortValue;

        public void setExtent(String extent) {
            assert (!extent.isEmpty()) : "Shouldn't be empty";
            this.extent = Long.parseLong(extent);
        }

        public void setMimeType(String mimeType) {
            assert (!mimeType.isEmpty()) : "Shouldn't be empty";
            if (this.mimeType != null) {
                //i've found that in two mimetypes in the same
                //download file, the second is always zip
                assert ("application/zip".equals(mimeType));
                isCompressed = true;
            } else {
                this.mimeType = mimeType;
                calculateSortValue();
            }
        }

        public void setDownloadSuffix(String url) {
            //remove http://www.gutenberg.org/dirs/ from the uri
            assert (!url.isEmpty()) : "Shouldn't be empty";
            downloadSuffix = url.substring(30);
        }

        public void setId(String ID) {
            assert (!ID.isEmpty()) : "Shouldn't be empty";
            this.id = Long.parseLong(ID.substring(6));
        }

        public String joinProperties() {
            auxString.setLength(0);
            auxString.append(mimeType);
            auxString.append(GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
            auxString.append(extent);
            auxString.append(GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
            auxString.append(downloadSuffix);
            return auxString.toString();
        }

        private void calculateSortValue() {
            //skip the "text/"
            String format = mimeType.substring(5);
            if (format.startsWith("html")) {
                sortValue = 6;
            } else if (format.startsWith("rtf")) {
                sortValue = 5;
            } else if (format.startsWith("plain")) {
                //some preferable charsets for text files
                //(assuming html and rtf has good ones)
                int indexOfCharset = format.indexOf('"');
                if (indexOfCharset != -1) {
                    format = format.substring(indexOfCharset + 1);
                    if (format.startsWith("utf-8")) {
                        sortValue = 4;
                    } else if (format.startsWith("iso-8859")) {
                        sortValue = 3;
                    } else {
                        sortValue = 2;
                    }
                } else {
                    sortValue = 1;
                }
            } else {
                //don't know anything else
                sortValue = 0;
            }
        }

        @Override
        public int compareTo(File o) {
            // 0-6 domain, can subtract.
            return sortValue - o.sortValue;
        }

        private boolean isForABook() {
            return isCompressed && mimeType.startsWith("text");
        }
    }

    public static class Book {

        public long ID;
        public List<String> languages = new LinkedList<>();
        public List<String> creators = new LinkedList<>();
        public List<String> contributors = new LinkedList<>();
        public List<String> subjects = new LinkedList<>();
        public List<String> titles = new LinkedList<>();
        public boolean isPossiblyABook = true;

        public String joinTitles() {
            return joinAux(titles, GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
        }

        public String joinCreators() {
            return joinAux(creators, GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
        }

        public String joinContributors() {
            return joinAux(contributors, GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
        }

        public String joinSubjects() {
            return joinAux(subjects, GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
        }

        public String joinLanguages() {
            return joinAux(languages, GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
        }

        private String joinAux(List<String> joinList, String seperator) {
            if (joinList.isEmpty()) {
                return "";
            }
            auxString.setLength(0);
            Iterator<String> i = joinList.iterator();
            auxString.append(i.next());
            while (i.hasNext()) {
                auxString.append(seperator).append(i.next());
            }
            return auxString.toString();
        }

        //in the set methods, in the next version of digester, hopefully the
        //isEmpty calls can go away, and only the asserts remain. They are a
        //workaround for a problem of the substrings in xml tags with callbacks.
        //(bug report DIGESTER-143)
        public void setCreator(String creator) {
            if (creator.isEmpty()) {
                return;
            }
            assert (!creator.isEmpty()) : "Shouldn't be empty " + ID;
            this.creators.add(getInternedString(creator));
        }

        public void setContributor(String contributor) {
            if (contributor.isEmpty()) {
                return;
            }
            assert (!contributor.isEmpty()) : "Shouldn't be empty " + ID;
            this.contributors.add(getInternedString(contributor));
        }

        public void setLanguage(String language) {
            if (language.isEmpty()) {
                return;
            }
            assert (!language.isEmpty()) : "Shouldn't be empty " + ID;
            this.languages.add(getInternedString(language));
//            if (languages.size() > 1) {
//                System.out.println("Double lang: " + titles);
//            }
        }

        public void setTitle(String title) {
            if (title.isEmpty() || !titles.isEmpty()) {
                //don't care about alternate titles.
                return;
            }
            assert (!title.isEmpty()) : "Shouldn't be empty " + ID;
            this.titles.add(title.replace("\n", " "));
//            if (titles.size() > 1) {
//                System.out.println("Double title: " + titles);
//            }
        }

        public void setLCSH(String lcsh) {
            assert (!lcsh.isEmpty()) : "Shouldn't be empty " + ID;
            subjects.add(getInternedString(lcsh));
        }

        public void setId(String ID) {
            assert (!ID.isEmpty()) : "Shouldn't be empty " + ID;
            this.ID = Long.parseLong(ID.substring(5));
        }

        public void setIsNotBook() {
            isPossiblyABook = false;
        }

        public static String getInternedString(String author) {
            String canonical = interned.get(author);

            if (canonical == null) {
                interned.put(author, author);
                return author;
            }
            return canonical;
        }

        @Override
        public String toString() {
            return "Book{" + "ID=" + ID + "title=" + titles + "language=" + languages + "creator=" + creators + "subjects=" + subjects + "isABook=" + isPossiblyABook + '}';
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Book other = (Book) obj;
            if (this.ID != other.ID) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + (int) (this.ID ^ (this.ID >>> 32));
            return hash;
        }
    }
}
