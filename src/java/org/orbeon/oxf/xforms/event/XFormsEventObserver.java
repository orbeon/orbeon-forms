/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event;

import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.List;

/**
 * Represents an event observer. Implemented in particular by controls, xforms:model, xforms:instance, xforms:submission.
 */
public interface XFormsEventObserver extends XFormsEventTarget {
    String getId();
    String getEffectiveId();
    XFormsEventObserver getParentEventObserver(XBLContainer container);
    void addListener(String eventName, EventListener listener);
    void removeListener(String eventName, EventListener listener);
    List<EventListener> getListeners(String eventName);
}
