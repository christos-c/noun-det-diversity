package determiners.readers;

import edu.illinois.cs.cogcomp.core.io.IOUtils;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A reader/annotator for the XML version of the CHILDES Brown corpus. Performs the following cleanup:
 * <ul>
 *     <li>&lt;pause&gt;: <b>convert to ','</b></li>
 *     <li>&lt;t type="X"&gt;: <b>convert to '.' if X=p, '?' if X=q, '!' if X=e else remove</b></li>
 *     <li>&lt;g&gt;&lt;w&gt;&lt;/w&gt;&lt;w&gt;&lt;/w&gt;...&lt;/g&gt;: <b>use &lt;w&gt;(s) under &lt;g&gt;</b>
 *     <br/> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp; <b>UNLESS &lt;g&gt; contains child &lt;k type="retracing"&gt;</b>
 *     </li>
 *     <li>&lt;w untranscribed="...": <b>ignore</b></li>
 *     <li>&lt;w&gt;...&lt;replacement&gt;&lt;w&gt;...&lt;/w&gt;: <b>use &lt;w&gt; under &lt;replacement&gt;</b></li>
 *     <li>&lt;w&gt;...&lt;shortening&gt;...&lt;/shortening&gt;: <b>concat &lt;w&gt;'s text with &lt;shortening&gt;'s</b></li>
 *     <li>&lt;w&gt;...&lt;wk type="cmp"&gt;...&lt;/wk&gt;: <b>concat &lt;w&gt;'s text with &lt;wk&gt;'s</b></li>
 *     <li>&lt;w&gt;...&lt;p type="drawl"&gt;...&lt;/p&gt;: <b>concat &lt;w&gt;'s text with &lt;p&gt;'s</b></li>
 *     <li><i>String cleanup</i>: '_' and 'z_': <b>replaced with ' '</b></li>
 * </ul>
 *
 * @author Christos Christodoulopoulos
 */
public class XMLCorpusCleaner {
    private static final String dataFolder = "data";
    private static final String child = "Nina";

    /** The folder containing the individual data for each child */
    private static final String subFolder = dataFolder + "/xml-files/Suppes/Nina/";

    /** Which speaker to collect data for. Can be CHI (children) or MOT (mothers) */
    private static final String WHO = "CHI";

    private static NodeList utterances;
    private static int counter;

    public static void main (String[] args) throws IOException {
        List<String> uttLines = new ArrayList<>();
        for (String file : IOUtils.ls(subFolder)) {
            XMLCorpusCleaner reader = new XMLCorpusCleaner(subFolder + file);
            String utt;
            while ((utt = reader.nextUtterance()) != null) {
                if (utt.trim().equals(".")) continue;
                uttLines.add(utt);
            }
        }
        String mot = (WHO.equals("MOT")) ? "-mot" : "";
        LineIO.write(dataFolder + File.separator + child + mot + ".utterances.txt", uttLines);
    }

    public XMLCorpusCleaner(String filename) {
        Document doc = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new File(filename));
        }
        catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        utterances = doc.getElementsByTagName("u");
        counter = 0;
    }

    public String nextUtterance() {
        if (counter >= utterances.getLength()) return null;
        Node utterance = utterances.item(counter);
        String who = utterance.getAttributes().getNamedItem("who").getNodeValue();
        if (!who.equals(WHO)) {
            counter++;
            return nextUtterance();
        }
        String utteranceText = "";
        // Ignore everything other than <w>, <pause>, <t>, <g>
        for (Node node : getFilteredChildren(utterance, "w", "pause", "t", "g")) {
            String nodeText = "";
            String nodeName = node.getNodeName();
            switch (nodeName) {
                case "pause":
                    nodeText = ",";
                    break;
                case "t":
                    nodeText = getPunct(node);
                    break;
                case "g":
                    counter++;
                    return nextUtterance();
                case "w":
                    if (isClean(node))
                        nodeText = getWord(node);
                    else {
                        counter++;
                        return nextUtterance();
                    }
                    break;
            }
            utteranceText += nodeText + " ";
        }
        utteranceText = utteranceText.trim();
        counter++;
        return utteranceText;
    }

    public List<Node> getFilteredChildren(Node node, String... filters) {
        List<Node> list = new ArrayList<>();
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            if (Arrays.asList(filters).contains(child.getNodeName()))
                list.add(child);
        }
        return list;
    }

    private String getPunct(Node node) {
        String punctString;
        String type = node.getAttributes().getNamedItem("type").getNodeValue();
        switch (type) {
            case "p":
                punctString = ".";
                break;
            case "q":
                punctString = "?";
                break;
            case "e":
                punctString = "!";
                break;
            default:
                punctString = "";
        }
        return punctString;
    }

    private boolean isClean(Node node) {
        if (node.getAttributes() != null) {
            Node type = node.getAttributes().getNamedItem("type");
            if (type != null && type.getNodeValue().equals("retracing"))
                return false;
            if (node.getAttributes().getNamedItem("untranscribed") != null)
                return false;
        }
        Node replacementNode = getSubnode("replacement", node);
        if (replacementNode != null) return false;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals("wk")
                    && child.getAttributes().getNamedItem("type").getNodeValue().equals("cmp"))
                return false;
            else if (child.getNodeName().equals("p")
                    && child.getAttributes().getNamedItem("type").getNodeValue().equals("drawl"))
                return false;
            else if (child.getNodeName().equals("shortening"))
                return false;
        }
        return true;
    }

    private String getWord(Node node) {
        String wordString="";
        if (node.getAttributes() != null) {
            Node type = node.getAttributes().getNamedItem("type");
            if (type != null && type.getNodeValue().equals("retracing"))
                return wordString;
            Node untranscribed = node.getAttributes().getNamedItem("untranscribed");
            if (untranscribed != null)
                return wordString;
        }
        Node replacementNode = getSubnode("replacement", node);
        if (replacementNode != null) {
            return getWord(getSubnode("w", replacementNode));
        }
        // At this point <w> will have some text and potentially either a <shortening>, <wk> or <p type="drawl">
        if (node.getFirstChild().getNodeType() == Node.TEXT_NODE)
            wordString += node.getFirstChild().getTextContent();
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals("wk")
                    && child.getAttributes().getNamedItem("type").getNodeValue().equals("cmp"))
                wordString += child.getNextSibling().getTextContent();
            else if (child.getNodeName().equals("p")
                    && child.getAttributes().getNamedItem("type").getNodeValue().equals("drawl"))
                if (child.getNextSibling() != null)
                    wordString += child.getNextSibling().getTextContent();
            else if (child.getNodeName().equals("shortening"))
                wordString += child.getFirstChild().getTextContent();
            else if (child.getNodeType() == Node.TEXT_NODE) {
                if (child.getPreviousSibling() != null && child.getPreviousSibling().getNodeName().equals("shortening"))
                    wordString += child.getTextContent();
            }
        }
        return wordString.replaceAll("z*_", " ").trim();
    }

    private Node getSubnode(String name, Node node) {
        if (! node.hasChildNodes()) return null;

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            if (child.getNodeName().equals(name))
                return child;
        }
        return null;
    }
}
