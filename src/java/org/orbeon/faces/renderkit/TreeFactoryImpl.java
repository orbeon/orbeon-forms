package org.orbeon.faces.renderkit;

import javax.faces.tree.TreeFactory;
import javax.faces.tree.Tree;
import javax.faces.context.FacesContext;
import javax.faces.FacesException;

public class TreeFactoryImpl extends TreeFactory {

    public TreeFactoryImpl() {
    }

    /**
     * Just use Sun's implementation, but override the RenderKit. This is done
     * because in EA 4, setting the RenderKit in faces-config.xml doesn't work.
     */
    public Tree getTree(FacesContext facesContext, String treeId) throws FacesException {
        Tree tree = new com.sun.faces.tree.SimpleTreeImpl(facesContext, treeId);
        tree.setRenderKitId("org.orbeon.faces.renderkit.xml");
        return tree;
    }
}
