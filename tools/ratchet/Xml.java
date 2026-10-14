package ratchet;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** DOM helpers that never fetch a DTD from the network. */
final class Xml {

  private Xml() {}

  static Document parse(String xml) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("cannot parse XML: " + e.getMessage(), e);
    }
  }

  static List<Element> elements(Document doc, String tag) {
    return elements(doc.getElementsByTagName(tag));
  }

  static List<Element> elements(Element parent, String tag) {
    return elements(parent.getElementsByTagName(tag));
  }

  /** Direct children of {@code parent} with the given tag. */
  static List<Element> children(Element parent, String tag) {
    List<Element> list = new ArrayList<>();
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element e && e.getTagName().equals(tag)) {
        list.add(e);
      }
    }
    return list;
  }

  private static List<Element> elements(NodeList nodes) {
    List<Element> list = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); i++) {
      list.add((Element) nodes.item(i));
    }
    return list;
  }
}
