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
package org.orbeon.faces.components.demo.components;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.faces.component.UIComponent;
import javax.faces.component.UIComponentBase;
import javax.faces.context.FacesContext;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.FacesEvent;
import javax.faces.event.FacesListener;
import javax.faces.event.PhaseId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * <p>Component designed to contain child components (and possibly other
 * layout in a JSP environment) for things like a tabbed pane control.
 */
public class PaneComponent extends UIComponentBase {


    private static Log log = LogFactory.getLog(PaneComponent.class);

    protected List listeners[] = null;

    // creates and adds a listener;
    public PaneComponent() {
        PaneSelectedListener listener = new PaneSelectedListener();
        addPaneSelectedListener(listener);
    }

    // Component type for this component
    public static final String TYPE = "PaneComponent";

    public String getComponentType() {
        return (TYPE);
    }


    // Does this component render its own children?
    public boolean getRendersChildren() {
        return (true);
    }


    // The currently selected state of this component
    public boolean isSelected() {
        Boolean selected = (Boolean) getAttribute("selected");
        if (selected != null) {
            return (selected.booleanValue());
        } else {
            return (false);
        }
    }

    public void setSelected(boolean selected) {
        if (selected) {
            setAttribute("selected", Boolean.TRUE);
        } else {
            setAttribute("selected", null);
        }
    }

    // Ignore update model requests
    public void updateModel(FacesContext context) {
    }

    // adds a listener
    public void addPaneSelectedListener(PaneSelectedListener listener) {

        if (listener == null) {
            throw new NullPointerException();
        }
        if (listeners == null) {
            listeners = new List[PhaseId.VALUES.size()];
        }
        int ordinal = listener.getPhaseId().getOrdinal();
        if (listeners[ordinal] == null) {
            listeners[ordinal] = new ArrayList();
        }
        listeners[ordinal].add(listener);
    }

    // invokes listener method passing event as argument
    public boolean broadcast(FacesEvent event, PhaseId phaseId)
            throws AbortProcessingException {

        if ((event == null) || (phaseId == null)) {
            throw new NullPointerException();
        }
        if (phaseId.equals(PhaseId.ANY_PHASE)) {
            throw new IllegalStateException();
        }
        if (event instanceof PaneSelectedEvent) {
            if (listeners == null) {
                return (false);
            }
            PaneSelectedEvent aevent = (PaneSelectedEvent) event;
            int ordinal = phaseId.getOrdinal();
            broadcast(aevent, listeners[PhaseId.ANY_PHASE.getOrdinal()]);
            broadcast(aevent, listeners[ordinal]);
            for (int i = ordinal + 1; i < listeners.length; i++) {
                if ((listeners[i] != null) && (listeners[i].size() > 0)) {
                    return (true);
                }
            }
            return (false);
        } else {
            throw new IllegalArgumentException();
        }
    }

    protected void broadcast(PaneSelectedEvent event, List list) {

        if (list == null) {
            return;
        }
        Iterator listeners = list.iterator();
        while (listeners.hasNext()) {
            PaneSelectedListener listener =
                    (PaneSelectedListener) listeners.next();
            listener.processPaneSelectedEvent(event);
        }
    }

    /**
     * <p>Faces Listener implementation which sets the selected tab
     * component;</p>
     */
    public class PaneSelectedListener implements FacesListener {

        public PaneSelectedListener() {
        }

        // This listener will process events after the phase specified.

        public PhaseId getPhaseId() {
            return PhaseId.ANY_PHASE;
        }

        // process the event..

        public void processPaneSelectedEvent(FacesEvent event) {
            UIComponent source = event.getComponent();
            PaneSelectedEvent pevent = (PaneSelectedEvent) event;
            String id = pevent.getId();

            boolean paneSelected = false;

            // Find the parent tab control so we can set all tabs
            // to "unselected";
            UIComponent tabControl = findParentForRendererType(
                    source, "Tabbed");
            int n = tabControl.getChildCount();
            for (int i = 0; i < n; i++) {
                PaneComponent pane = (PaneComponent) tabControl.getChild(i);
                if (pane.getComponentId().equals(id)) {
                    pane.setSelected(true);
                    paneSelected = true;
                } else {
                    pane.setSelected(false);
                }
            }

            if (!paneSelected) {
                log.warn("Cannot select pane for id=" + id + "," +
                        ", selecting first pane");
                ((PaneComponent) tabControl.getChild(0)).setSelected(true);
            }
        }
    }

    private UIComponent findParentForRendererType(
            UIComponent component, String rendererType) {
        Object facetParent = null;
        UIComponent currentComponent = component;
        // PENDING (visvan) remove commented out code once the app has been
        // tested.
        /*   facetParent = currentComponent.getAttribute(
               UIComponent.FACET_PARENT_ATTR);
           while (facetParent != null) {
               currentComponent = (UIComponent) facetParent;
               facetParent = currentComponent.getAttribute(
                   UIComponent.FACET_PARENT_ATTR);
               if (currentComponent.getRendererType().equals(rendererType)) {
                   return currentComponent;
               }
           } */
        // Search for an ancestor that is the specified renderer type;
        // search includes the facets.
        while (null != (currentComponent = currentComponent.getParent())) {
            /*  facetParent = currentComponent.getAttribute(
                  UIComponent.FACET_PARENT_ATTR);
              if (facetParent != null) {
                  currentComponent = (UIComponent) facetParent;
              } */
            if (currentComponent.getRendererType().equals(rendererType)) {
                break;
            }
        }
        return currentComponent;
    }
}
