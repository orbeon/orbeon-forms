package org.orbeon.faces11;

import com.sun.faces.el.PropertyResolverImpl;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XPathUtils;

/**
 *
 */
public class PropertyResolver extends PropertyResolverImpl {
    public Class getType(Object o, int i) {
        System.out.println("getType: " + o + ", " + i);
        return super.getType(o, i);
    }

    public Class getType(Object o, Object o1) {
        System.out.println("getType: " + o + ", " + o1);

        if (o instanceof XMLDocument || o instanceof XMLElement) {
            return String.class;
        }

        return super.getType(o, o1);
    }

    public Object getValue(Object o, int i) {
        System.out.println("getValue: " + o + ", " + i);
        return super.getValue(o, i);
    }

    public Object getValue(Object o, Object o1) {
        System.out.println("getValue: " + o + ", " + o1);

        if (o instanceof XMLDocument) {
            Element element = (Element) XPathUtils.selectSingleNode(((XMLDocument) o).document, "/*[local-name() = '" + o1.toString() + "']");
            return (element != null) ? new XMLElement(element) : null;
        } else if (o instanceof XMLElement) {
            Element element = (Element) XPathUtils.selectSingleNode(((XMLElement) o).element, "*[local-name() = '" + o1.toString() + "']");
            return (element != null) ? new XMLElement(element) : null;
        }

        return super.getValue(o, o1);
    }

    public boolean isReadOnly(Object o, int i) {
        System.out.println("isReadOnly: " + o + ", " + i);
        return super.isReadOnly(o, i);
    }

    public boolean isReadOnly(Object o, Object o1) {
        System.out.println("isReadOnly: " + o + ", " + o1);

        if (o instanceof XMLDocument || o instanceof XMLElement) {
            return false;
        }

        return super.isReadOnly(o, o1);
    }

    public void setValue(Object o, int i, Object o1) {
        System.out.println("setValue: " + o + ", " + i + ", " + o1);
        super.setValue(o, i, o1);
    }

    public void setValue(Object o, Object o1, Object o2) {
        System.out.println("setValue: " + o + ", " + o2 + ", " + o2);

        if (o instanceof XMLDocument ) {
            // CHECK: can this be called?
        } else if (o instanceof XMLElement) {
            Object value = getValue(o, o1);
            if (value != null) {
                Element element = ((XMLElement) o).element;
                if (element.elements().isEmpty())
                    throw new OXFException("Element must not contain children elements when setting value");
                element.setText(o2.toString());
            } else {
                throw new OXFException("Element not found when setting value");
            }
        }

        super.setValue(o, o1, o2);
    }

    public static class XMLDocument {
        public Document document;

        public XMLDocument(Document document) {
            this.document = document;
        }

        public String toString() {
            return (document != null) ? XPathUtils.selectStringValue(document, "/*") : "";
        }
    }

    public static class XMLElement {
        public Element element;

        public XMLElement(Element element) {
            this.element = element;
        }

        public String toString() {
            return (element != null) ? XPathUtils.selectStringValue(element, ".") : "";
        }
    }
}
