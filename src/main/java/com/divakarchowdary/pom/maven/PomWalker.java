package com.divakarchowdary.pom.maven;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class PomWalker {

    private static final String MAVEN_NS = "http://maven.apache.org/POM/4.0.0";

    private PomWalker() {}

    @Data
    public static class VersionChange {
        private final String oldVersion;
        private final String newVersion;
    }

    @Data
    public static class UpdateResult {
        private boolean changed;
        private Map<String, VersionChange> updated = new LinkedHashMap<>();
    }

    public static UpdateResult updateAllProperties(File pomFile,
                                                   Map<String, String> versionMap,
                                                   Map<String, String> dependencyPropertyMap,
                                                   Map<String, String> pluginPropertyMap) throws Exception {

        UpdateResult result = new UpdateResult();

        if (!pomFile.exists()) {
            log.error("POM file not found: {}", pomFile.getAbsolutePath());
            return result;
        }

        log.info("Processing: {}", pomFile.getAbsolutePath());

        Document doc = parseDocument(pomFile);
        Element project = doc.getDocumentElement();
        String ns = project.getNamespaceURI();

        Map<String, String> remaining = new LinkedHashMap<>(versionMap);

        // 1. Update <parent> version
        if (remaining.containsKey("parent")) {
            updateParentVersion(project, remaining.remove("parent"), result);
        }

        // 2. Update <project><version>
        if (remaining.containsKey("project.version")) {
            updateProjectVersion(project, remaining.remove("project.version"), result);
        }

        // 3. Find or create <properties>
        Element properties = findOrCreateProperties(doc, project, ns);

        // 4. Extract hardcoded dependency versions into properties
        Map<String, String> extractedProps = new LinkedHashMap<>();

        Element dependencies = findChild(project, "dependencies");
        if (dependencies != null) {
            extractVersionsIntoProperties(dependencies, "dependency",
                    dependencyPropertyMap, extractedProps, result);
        }

        Element depMgmt = findChild(project, "dependencyManagement");
        if (depMgmt != null) {
            Element depMgmtDeps = findChild(depMgmt, "dependencies");
            if (depMgmtDeps != null) {
                extractVersionsIntoProperties(depMgmtDeps, "dependency",
                        dependencyPropertyMap, extractedProps, result);
            }
        }

        // 5. Extract hardcoded plugin versions into properties
        Element build = findChild(project, "build");
        if (build != null) {
            Element plugins = findChild(build, "plugins");
            if (plugins != null) {
                extractVersionsIntoProperties(plugins, "plugin",
                        pluginPropertyMap, extractedProps, result);
            }
            Element pluginMgmt = findChild(build, "pluginManagement");
            if (pluginMgmt != null) {
                Element mgmtPlugins = findChild(pluginMgmt, "plugins");
                if (mgmtPlugins != null) {
                    extractVersionsIntoProperties(mgmtPlugins, "plugin",
                            pluginPropertyMap, extractedProps, result);
                }
            }
        }

        // 6. Add extracted properties to <properties>
        for (Map.Entry<String, String> entry : extractedProps.entrySet()) {
            if (findChild(properties, entry.getKey()) == null) {
                appendProperty(doc, properties, ns, entry.getKey(), entry.getValue());
                log.info("[property added] {} = {}", entry.getKey(), entry.getValue());
            }
        }

        // 7. Update existing properties from version map
        updateExistingProperties(properties, remaining, result);

        // 8. Add any still-missing version map entries as new properties
        for (Map.Entry<String, String> entry : remaining.entrySet()) {
            if (findChild(properties, entry.getKey()) == null) {
                appendProperty(doc, properties, ns, entry.getKey(), entry.getValue());
                result.setChanged(true);
                result.getUpdated().put(entry.getKey(), new VersionChange("(none)", entry.getValue()));
                log.info("[property created] {} = {}", entry.getKey(), entry.getValue());
            }
        }

        if (result.isChanged()) {
            writeDocument(doc, pomFile);
            log.info("Updated {} version(s) in {}", result.getUpdated().size(), pomFile.getName());
        } else {
            log.info("No changes needed in {}", pomFile.getName());
        }

        return result;
    }
    

    private static Document parseDocument(File pomFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(pomFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private static Element findOrCreateProperties(Document doc, Element project, String ns) {
        Element properties = findChild(project, "properties");
        if (properties == null) {
            properties = findByNamespace(doc, MAVEN_NS, "properties");
        }
        if (properties == null) {
            properties = ns != null
                    ? doc.createElementNS(ns, "properties")
                    : doc.createElement("properties");

            Element insertBefore = findChild(project, "dependencies");
            if (insertBefore == null) insertBefore = findChild(project, "dependencyManagement");
            if (insertBefore == null) insertBefore = findChild(project, "build");

            if (insertBefore != null) {
                project.insertBefore(properties, insertBefore);
            } else {
                project.appendChild(properties);
            }
            log.info("Created new <properties> section");
        }
        return properties;
    }

    private static void extractVersionsIntoProperties(Element container, String childTag,
                                                      Map<String, String> gaToPropertyMap,
                                                      Map<String, String> extractedProps,
                                                      UpdateResult result) {
        NodeList items = container.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE || !childTag.equals(elementName(node))) continue;

            Element el = (Element) node;
            String key = childText(el, "groupId") + ":" + childText(el, "artifactId");

            if (!gaToPropertyMap.containsKey(key)) continue;

            Element versionEl = findChild(el, "version");
            if (versionEl == null) continue;

            String current = versionEl.getTextContent().trim();
            if (current.startsWith("${") && current.endsWith("}")) continue;

            String propName = gaToPropertyMap.get(key);
            String propRef = "${" + propName + "}";

            extractedProps.putIfAbsent(propName, current);
            versionEl.setTextContent(propRef);
            result.setChanged(true);
            result.getUpdated().put(key, new VersionChange(current, propRef));

            log.info("[{}] {} : {} → {}", childTag, key, current, propRef);
        }
    }

    private static void updateExistingProperties(Element properties, Map<String, String> remaining,
                                                  UpdateResult result) {
        List<String> matched = new ArrayList<>();
        NodeList children = properties.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            String key = elementName(node);
            if (!remaining.containsKey(key)) continue;

            String newValue = remaining.get(key);
            String oldValue = node.getTextContent().trim();

            if (!oldValue.equals(newValue)) {
                node.setTextContent(newValue);
                result.setChanged(true);
                result.getUpdated().put(key, new VersionChange(oldValue, newValue));
                log.info("[property updated] {} : {} → {}", key, oldValue, newValue);
            }
            matched.add(key);
        }
        matched.forEach(remaining::remove);
    }

    private static void updateParentVersion(Element project, String newVersion, UpdateResult result) {
        Element parent = findChild(project, "parent");
        if (parent == null) return;
        Element versionEl = findChild(parent, "version");
        if (versionEl == null) return;
        String old = versionEl.getTextContent().trim();
        if (!old.equals(newVersion)) {
            versionEl.setTextContent(newVersion);
            result.setChanged(true);
            result.getUpdated().put("parent.version", new VersionChange(old, newVersion));
            log.info("[parent.version] {} → {}", old, newVersion);
        }
    }

    private static void updateProjectVersion(Element project, String newVersion, UpdateResult result) {
        Element versionEl = findChild(project, "version");
        if (versionEl == null) return;
        String old = versionEl.getTextContent().trim();
        if (!old.equals(newVersion)) {
            versionEl.setTextContent(newVersion);
            result.setChanged(true);
            result.getUpdated().put("project.version", new VersionChange(old, newVersion));
            log.info("[project.version] {} → {}", old, newVersion);
        }
    }

    private static void appendProperty(Document doc, Element properties, String ns,
                                       String name, String value) {
        Element el = ns != null ? doc.createElementNS(ns, name) : doc.createElement(name);
        el.setTextContent(value);
        properties.appendChild(doc.createTextNode("\n        "));
        properties.appendChild(el);
    }

    private static void writeDocument(Document doc, File file) throws TransformerException, IOException {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try { tf.setAttribute("indent-number", 4); } catch (IllegalArgumentException ignored) {}

        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        removeWhitespaceNodes(doc);

        try (OutputStream os = new FileOutputStream(file);
             Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            t.transform(new DOMSource(doc), new StreamResult(writer));
        }
    }

    private static void removeWhitespaceNodes(Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                node.removeChild(child);
            } else {
                removeWhitespaceNodes(child);
            }
            child = next;
        }
    }

    private static Element findChild(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(elementName(n))) {
                return (Element) n;
            }
        }
        return null;
    }

    private static Element findByNamespace(Document doc, String ns, String localName) {
        NodeList list = doc.getElementsByTagNameNS(ns, localName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static String childText(Element parent, String tag) {
        Element child = findChild(parent, tag);
        return child != null ? child.getTextContent().trim() : "";
    }

    private static String elementName(Node node) {
        return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
    }
}
