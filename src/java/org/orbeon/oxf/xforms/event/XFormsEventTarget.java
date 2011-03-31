/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event;

import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.LocationData;

/**
 * XFormsEventTarget is implemented by classes that support dispatching of events.
 */
public interface XFormsEventTarget {
    String getId();
    String getEffectiveId();
    XBLContainer getXBLContainer(XFormsContainingDocument containingDocument);
    LocationData getLocationData();
    XFormsEventObserver getParentEventObserver(XBLContainer container);
    void performTargetAction(XBLContainer container, XFormsEvent event);
    void performDefaultAction(XFormsEvent event);
    XFormsContextStack.BindingContext getBindingContext(XFormsContainingDocument containingDocument);
    boolean allowExternalEvent(IndentedLogger indentedLogger, String logType, String eventName);
}
