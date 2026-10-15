package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Ce que le pom.xml racine dit de la clé Sonar : groupId:artifactId, la clé que sonar-maven-plugin
 * prend quand on ne lui en donne pas.
 *
 * <p>Seuls les enfants directs de <project> comptent — un <groupId> de dépendance ou de plugin
 * n'est pas celui du projet —, et le groupId absent s'hérite du <parent>, comme Maven le fait. Une
 * valeur qui contient encore ${…} n'est pas une clé : on la rend dans rawKey, jamais dans key.
 */
record Pom(@Nullable String key, String rawKey, @Nullable String name) {

  static @Nullable Pom parse(String xml) {
    Element project = root(xml);
    if (project == null || !"project".equals(local(project))) return null;
    String group = child(project, "groupId");
    if (group == null) {
      Element parent = childElement(project, "parent");
      if (parent != null) group = child(parent, "groupId");
    }
    String artifact = child(project, "artifactId");
    if (group == null || artifact == null) return null;
    String raw = group + ":" + artifact;
    return new Pom(raw.contains("${") ? null : raw, raw, child(project, "name"));
  }

  /** L'élément racine, ou null pour un XML illisible ou refusé. */
  private static @Nullable Element root(String xml) {
    try {
      var f = DocumentBuilderFactory.newInstance();
      // Un pom vient d'un dépôt qu'on audite, pas qu'on contrôle :
      // pas de DTD, pas d'entité externe.
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setExpandEntityReferences(false);
      f.setXIncludeAware(false);
      return f.newDocumentBuilder()
          .parse(new InputSource(new StringReader(xml)))
          .getDocumentElement();
    } catch (ParserConfigurationException | SAXException | IOException e) {
      return null;
    }
  }

  private static @Nullable String child(Element parent, String name) {
    Element e = childElement(parent, name);
    if (e == null) return null;
    String v = e.getTextContent().trim();
    return v.isEmpty() ? null : v;
  }

  private static @Nullable Element childElement(Element parent, String name) {
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && name.equals(local(e))) return e;
    }
    return null;
  }

  private static String local(Element e) {
    String n = e.getNodeName();
    return n.substring(n.indexOf(':') + 1);
  }
}
