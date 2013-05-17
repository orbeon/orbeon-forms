/*
 * Copyright 2002, 2003 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials
 *   provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT OF OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THIS SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */
/*
 * The original source code by Sun Microsystems has been modifed by Orbeon,
 * Inc.
 */
package org.orbeon.faces.components.demo.renderkit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.orbeon.faces.components.demo.components.GraphComponent;
import org.orbeon.faces.components.demo.components.GraphEvent;
import org.orbeon.faces.components.demo.model.Graph;
import org.orbeon.faces.components.demo.model.Node;

import javax.faces.FacesException;
import javax.faces.component.UIComponent;
import javax.faces.component.UIForm;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * <p>Render our current value (which must be a <code>Graph</code>) as a menu
 * bar, where the children of the root node are treated as individual menus, and
 * grandchildren of the root node are the items on the main menus. A real
 * application would display things as hyperlinks to expand and contract items,
 * including recursive submenus.</p>
 */
public class MenuBarRenderer extends BaseRenderer {

    public static final String URL_PREFIX = "/faces";
    public final static String FORM_NUMBER_ATTR =
            "com.sun.faces.FormNumber";

    private static Log log = LogFactory.getLog(MenuBarRenderer.class);

    protected String treeClass = null;
    protected String selectedClass = null;
    protected String unselectedClass = null;
    protected String clientId = null;
    protected UIComponent component = null;
    protected FacesContext context = null;

    public boolean supportsComponentType(UIComponent component) {
        return (component instanceof GraphComponent);
    }


    public boolean supportsComponentType(String componentType) {
        return (componentType.equals(GraphComponent.TYPE));
    }


    public void decode(FacesContext context, UIComponent component)
            throws IOException {

        // if a node was clicked queue an GraphEvent.
        Map requestParameterMap = context.getExternalContext().
                getRequestParameterMap();
        String path = (String) requestParameterMap.
                get(component.getClientId(context));
        if (path != null && path.length() != 0) {
            GraphEvent event = createEvent(((GraphComponent) component), path);
            if (log.isTraceEnabled()) {
                log.trace("Adding event " + event);
            }
            context.addFacesEvent(event);
        }
        component.setValid(true);
    }


    public void encodeBegin(FacesContext context, UIComponent component)
            throws IOException {
        ;
    }


    public void encodeChildren(FacesContext context, UIComponent component)
            throws IOException {
        ;
    }


    public void encodeEnd(FacesContext context, UIComponent component)
            throws IOException {
        Graph graph = null;
        // Acquire the root node of the graph representing the menu
        graph = (Graph) ((GraphComponent) component).currentValue(context);
        if (graph == null) {
            throw new FacesException("Graph could not be located");
        }

        Node root = graph.getRoot();
        if (root == null) {
            throw new FacesException("Graph has no root node");
        }
        if (root.getChildCount() < 1) {
            return; // Nothing to render
        }

        this.component = component;
        this.context = context;
        clientId = component.getClientId(context);

        treeClass = (String) component.getAttribute("menuClass");
        selectedClass = (String) component.getAttribute("selectedClass");
        unselectedClass = (String) component.getAttribute("unselectedClass");

        // Render the menu bar for this graph
        Iterator menus = null;
        ResponseWriter writer = context.getResponseWriter();
        writer.write("<table border=\"0\" cellspacing=\"3\" cellpadding=\"0\"");
        if (treeClass != null) {
            writer.write(" class=\"");
            writer.write(treeClass);
            writer.write("\"");
        }
        writer.write(">");
        writer.write("\n");
        writer.write("<tr>"); // For top level menu bar
        menus = root.getChildren();
        while (menus.hasNext()) {
            Node menu = (Node) menus.next();
            writer.write("<th bgcolor=\"silver\" align=\"left\">");
            // The image links of the nodes that have children behave like
            // command buttons causing the form to be submitted so the state of
            // node can be toggled
            if (menu.isEnabled()) {
                writer.write("<a href=\"");
                writer.write(getSubmitScript(menu.getPath()));
                writer.write(" >");
                writer.write(menu.getLabel());
                writer.write("</a>");
            } else {
                writer.write(menu.getLabel());
            }
            writer.write("</th>");
        }
        writer.write("</tr>");

        writer.write("<tr>"); // For any expanded menu(s)
        menus = root.getChildren();
        while (menus.hasNext()) {
            Node menu = (Node) menus.next();
            writer.write("<td bgcolor=\"silver\" align=\"left\" valign=\"top\">");

            if (menu.isExpanded()) {
                writer.write("<ul>");
                Iterator items = menu.getChildren();
                while (items.hasNext()) {
                    Node node = (Node) items.next();
                    writer.write("<li>");
                    // Render the label for this node (if any) as a
                    // hyperlink is the node is enabled.
                    if (node.getLabel() != null) {
                        writer.write("   ");
                        String labelStyle = null;
                        if (node.isSelected() && (selectedClass != null)) {
                            labelStyle = selectedClass;
                        } else if (!node.isSelected() && (unselectedClass != null)) {
                            labelStyle = unselectedClass;
                        }
                        if (node.isEnabled()) {
                            writer.write("<a href=\"");
                            // Note: we assume that the links do not act as
                            // command button, meaning they do not cause the
                            // form to be submitted.
                            writer.write(href(node.getAction()));
                            writer.write("\"");
                            if (labelStyle != null) {
                                writer.write(" class=\"");
                                writer.write(labelStyle);
                                writer.write("\"");
                            }
                            writer.write(">");
                        } else if (labelStyle != null) {
                            writer.write("<span class=\"");
                            writer.write(labelStyle);
                            writer.write("\">");
                        }
                        writer.write(node.getLabel());
                        if (node.getLabel() != null) {
                            writer.write("</a>");
                        } else if (labelStyle != null) {
                            writer.write("</span>");
                        }
                    }
                    writer.write("</li>");
                    // PENDING - marker for submenu
                    // PENDING - expanded submenu?
                }
                writer.write("</ul>");
            } else {
                writer.write("&nbsp;");
            }
            writer.write("</td>");
        }
        writer.write("<input type=\"hidden\" name=\"" + clientId + "\" />");
        writer.write("</table>");

    }

    /**
     * Creates and returns an <code>ActionEvent</code>
     */
    protected GraphEvent createEvent(GraphComponent component, String path) {
        return (new GraphEvent(component, path, false));
    }

    /**
     * Returns a string that is rendered as the value of
     * onmousedown attribute. onmousedown event handler is used
     * the track the node that was clicked using a hidden field, then submits
     * the form so that we have the state information to reconstitute the tree.
     */
    protected String getSubmitScript(String path) {
        int formNumber = 0;
        formNumber = getMyFormNumber(getMyForm());
        StringBuffer sb = new StringBuffer();
        sb.append("#\" onmousedown=\"document.forms[" + formNumber + "]." +
                clientId + ".value='" + path +
                "';document.forms[" + formNumber + "].submit()\"");
        return sb.toString();
    }

    /**
     * Returns the parent form of graph component.
     */
    protected UIForm getMyForm() {
        UIComponent parent = component.getParent();
        while (parent != null) {
            if (parent instanceof UIForm) {
                break;
            }
            parent = parent.getParent();
        }
        return (UIForm) parent;
    }

    /**
     * Returns the form number of the parent form of graph component.
     */
    protected int getMyFormNumber(UIForm form) {
        // If we don't have a form, return 0
        if (null == form) {
            return 0;
        }
        Integer formsInt = (Integer)
                form.getAttribute(FORM_NUMBER_ATTR);
        // Assert.assert_it(null != formsInt);
        return formsInt.intValue();
    }

    /**
     * Returns a string that is rendered as the value of
     * href attribute.
     */
    protected String href(String action) {
        // if action doesn't start with "faces", just return
        if (action != null) {
            if (!(action.startsWith(URL_PREFIX))) {
                return action;
            }
        }
        String contextPath = context.getExternalContext().getRequestContextPath();
        StringBuffer sb = new StringBuffer(contextPath);
        sb.append(URL_PREFIX);
        // need to make sure the rendered string contains where we
        // want to go next (target).
        action = action.substring(URL_PREFIX.length());
        sb.append(action);
        return (context.getExternalContext().encodeURL(sb.toString()));
    }
}
