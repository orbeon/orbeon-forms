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
package org.orbeon.faces.components.demo.taglib;

import org.orbeon.faces.components.demo.model.Graph;
import org.orbeon.faces.components.demo.model.Node;
import org.orbeon.faces.components.demo.renderkit.Util;

import javax.faces.context.FacesContext;
import javax.faces.webapp.FacesBodyTag;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * <B>GraphMenuNodeTag</B> builds the graph as the nodes are processed.
 * This tag creates a node with specified properties. Locates the parent of
 * this node by using the node name from its immediate parent tag of the
 * type GraphTreeNodeTag. If the parent could not be located, then the created
 * node is assumed to be root.
 */
public class GraphTreeNodeTag extends FacesBodyTag {

    private String name = null;
    private String icon = null;
    private String label = null;
    private String action = null;
    private boolean expanded;
    private boolean enabled = true;

    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }


    public boolean getExpanded() {
        return (this.expanded);
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }


    public String getIcon() {
        return (this.icon);
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getLabel() {
        return (this.label);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean getEnabled() {
        return (this.enabled);
    }

    public String getAction() {
        return (this.action);
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComponentType() {
        return null;
    }

    public String getRendererType() {
        return null;
    }

    public int doStartTag() throws JspException {

        FacesContext context = FacesContext.getCurrentInstance();

        Graph graph = (Graph)
                ((Util.getValueBinding("sessionScope.graph_tree").getValue(context)));
        // In the postback case, graph and the node exist already.So make sure
        // it doesn't created again.
        if (graph.findNodeByName(getName()) != null) {
            return BodyTag.EVAL_BODY_BUFFERED;
        }
        Node node = new Node(getName(), getLabel(), getAction(), getIcon(),
                getEnabled(), getExpanded());

        // get the immediate ancestor/parent tag of this tag.
        GraphTreeNodeTag parentNode = null;
        try {
            parentNode = (GraphTreeNodeTag) TagSupport.findAncestorWithClass(this,
                    GraphTreeNodeTag.class);
        } catch (Exception e) {
            System.out.println("Exception while locating GraphTreeNodeTag.class");
        }
        // if this tag has no parent that is a node tag,
        if (parentNode == null) {
            // then this should be root
            graph.setRoot(node);
        } else {
            // add the node to its parent node.
            Node nodeToAdd = graph.findNodeByName(parentNode.getName());
            // this node should exist
            if (nodeToAdd != null) {
                nodeToAdd.addChild(node);
            }
        }

        return BodyTag.EVAL_BODY_BUFFERED;
    }

    public int doEndTag() throws JspException {
        return (EVAL_PAGE);
    }
}


