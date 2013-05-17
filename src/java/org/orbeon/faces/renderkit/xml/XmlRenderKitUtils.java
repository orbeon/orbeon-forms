/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is XML RenderKit for JSF.
 *
 * The Initial Developer of the Original Code is
 * Orbeon, Inc (info@orbeon.com)
 * Portions created by the Initial Developer are Copyright (C) 2002
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 */
package org.orbeon.faces.renderkit.xml;

import javax.faces.component.UIComponent;
import javax.faces.component.UIParameter;
import javax.faces.context.FacesContext;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.MissingResourceException;

/**
 * Utilities for the XML RenderKit.
 */
public class XmlRenderKitUtils {

    public static final String JSF_XML_NAMESPACE_URI = "http://orbeon.org/oxf/xml/jsf-output";
    public static final String JSF_XML_NAMESPACE_PREFIX = "jsf";

    public static String getOutputNamespacePrefix() {
        return JSF_XML_NAMESPACE_PREFIX;
    }

    public static String getOutputNamespaceURI() {
        return JSF_XML_NAMESPACE_URI;
    }

    public static String getStartElement(String name) {
        return "<" + JSF_XML_NAMESPACE_PREFIX + ":" + name;
    }

    public static String getEndElement(String name) {
        return "</" + JSF_XML_NAMESPACE_PREFIX + ":" + name + ">";
    }

    public static String getAttribute(String attributeName, String attributeValue) {
        return (attributeValue != null) ? (" " + attributeName + "=\"" + attributeValue + "\"") : "";
    }

    public static void outputStartElement(Writer writer, String name) throws IOException {
        writer.write(getStartElement(name));
    }

    public static void outputEndElement(Writer writer, String name) throws IOException {
        writer.write(getEndElement(name));
    }

    public static void outputAttribute(Writer writer, String attributeName, String attributeValue) throws IOException {
        writer.write(getAttribute(attributeName, attributeValue));
    }

    public static boolean isSaveStateInClient(FacesContext context) {
        return "true".equals(context.getExternalContext().getInitParameter("com.sun.faces.saveStateInClient"));
    }

    public static boolean checkParams(FacesContext context, UIComponent component) {
        if (context == null || component == null)
            throw new NullPointerException();
        return component.isRendered();
    }

    private static String booleanHtmlAttributes[] = { "disabled", "readonly", "ismap" };
    private static String htmlAttributes[] = {
        "accept", "accept-charset", "accesskey", "alt", "bgcolor", "border", "cellpadding",
        "cellspacing", "cols", "dir", "enctype", "frame", "height", "lang", "longdesc", "maxlength",
        "onblur", "onchange", "onclick", "ondblclick", "onfocus", "onkeydown", "onkeypress",
        "onkeyup", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup",
        "onreset", "onselect", "onsubmit", "onunload", "rows", "rules", "size", "style", "summary",
        "tabindex", "target", "title", "usemap", "width"
    };

    /**
     * Render other HTML attributes.
     */
    public static void outputHtmlAttributes(Writer writer, FacesContext context, UIComponent component) throws IOException {
        writer.write(getHtmlAttributes(context, component));
    }

    public static String getHtmlAttributes(FacesContext context, UIComponent component) {
        StringBuffer result = new StringBuffer();
        // Regular attributes
        for (int i = 0; i < htmlAttributes.length; i++) {
            String value = (String) component.getAttribute(htmlAttributes[i]);
            if (value != null) {
                if (result.length() == 0)
                    result.append(' ');
                result.append(getAttribute(htmlAttributes[i], value));
            }
        }
        // Boolean attributes
        for (int i = 0; i < booleanHtmlAttributes.length; i++) {
            String value = (String) component.getAttribute(booleanHtmlAttributes[i]);
            if (value != null) {
                if (result.length() == 0)
                    result.append(' ');
                if (Boolean.valueOf(value).booleanValue())
                    result.append(getAttribute(booleanHtmlAttributes[i], booleanHtmlAttributes[i]));
            }
        }
        return result.toString();
    }

    /**
     * Recursively encode a set of components.
     */
    public static void encodeRecursive(FacesContext context, UIComponent component) throws IOException {
        component.encodeBegin(context);
        if (component.getRendersChildren()) {
            // If component handles its children automatically
            component.encodeChildren(context);
        } else {
            // Otherwise render its children
            for (Iterator children = component.getChildren(); children.hasNext(); )
                encodeRecursive(context, (UIComponent) children.next());
        }
        component.encodeEnd(context);
    }

    // Create a dummy delegate component so we can just use some of Sun's methods for the resources
    private static class DummyHtmlBasicRenderer extends com.sun.faces.renderkit.html_basic.HtmlBasicRenderer {
        public String lookupResource(FacesContext context, UIComponent component, String keyAttr) throws MissingResourceException {
            return getKeyAndLookupInBundle(context, component, keyAttr);
        }
        public void encodeBegin(FacesContext facescontext, UIComponent uicomponent) throws IOException {
        }
        public void encodeChildren(FacesContext facescontext, UIComponent uicomponent) throws IOException {
        }
    }

    private static DummyHtmlBasicRenderer dummyHtmlBasicRenderer = new DummyHtmlBasicRenderer();

    /**
     * Get a resource or a label on a component.
     */
    public static String getResourceOrLabel(FacesContext context, UIComponent component) {
        try {
            return dummyHtmlBasicRenderer.lookupResource(context, component, "key");
        } catch (MissingResourceException e) {
            return (String) component.getAttribute("label");
        }
    }

    /**
     * Get a resource, null if not found.
     */
    public static String getResource(FacesContext context, UIComponent component) {
        try {
            return dummyHtmlBasicRenderer.lookupResource(context, component, "key");
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /**
     * Return the list of children UIParameter.
     */
    public static List findParameters(FacesContext context, UIComponent component) {
        List parameters = new ArrayList();
        // Iterate through all UIParameter children
        for (Iterator children = component.getChildren(); children.hasNext();) {
            Object child = children.next();
            if (child instanceof UIParameter)
                parameters.add(child);
        }
        return parameters;
    }
}
