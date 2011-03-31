/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control;

import org.orbeon.oxf.util.PropertyContext;

import java.util.List;

/**
 * Interface for all container controls.
 */
public interface XFormsContainerControl {
    /**
     * Add a child control.
     *
     * @param XFormsControl control
     */
    void addChild(XFormsControl XFormsControl);

    /**
     * Get all the direct children controls.
     *
     * @return  List<XFormsControl>
     */
    List<XFormsControl> getChildren();

    /**
     * Number of direct children control.
     *
     * @return  number
     */
    int getSize();

    /**
     * Notify container control that all its children have been added.
     *
     */
    void childrenAdded();

    /**
     * Update this container control's effective id, e.g. after a change of repeat iteration.
     */
    public void updateEffectiveId();
}
