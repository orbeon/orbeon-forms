package org.orbeon.faces.renderkit;

import org.orbeon.faces.renderkit.xml.XmlRenderKit;

import javax.servlet.ServletContextEvent;
import javax.faces.render.RenderKitFactory;
import javax.faces.FactoryFinder;
import java.util.Properties;

public class ServletContextListener implements javax.servlet.ServletContextListener {
    public ServletContextListener() {
    }

    public void contextInitialized(ServletContextEvent e) {
        e.getServletContext().log("XML Renderkit ServletContextListener called");

        // Create and add XML RenderKit
        RenderKitFactory renderKitFactory = (RenderKitFactory) FactoryFinder.getFactory(FactoryFinder.RENDER_KIT_FACTORY);
        renderKitFactory.addRenderKit(XmlRenderKit.XML_RENDERKIT_ID, new XmlRenderKit());

        // Set tree factory property

        // NOTE: We do this so that we can explicitly set the XML RenderKit on
        // all the trees created. This is a temporary hack due to the fact that
        // in the JSF RI EA 4, declaratively setting a RenderKit does not work.
        Properties properties = System.getProperties();
        properties.setProperty("javax.faces.tree.TreeFactory", "org.orbeon.faces.renderkit.TreeFactoryImpl");
    }

    public void contextDestroyed(ServletContextEvent e) {
    }
}
