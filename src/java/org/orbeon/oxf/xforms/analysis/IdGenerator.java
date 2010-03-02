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
package org.orbeon.oxf.xforms.analysis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class IdGenerator {

    private static final String AUTOMATIC_ID_PREFIX = "xf-";

    private boolean isCheckDuplicates = true;
    private int currentId = 1;
    private final Set<String> ids;

    public IdGenerator() {
        this.ids = new HashSet<String>();
    }

    public IdGenerator(int currentId) {
        this();
        this.currentId = currentId;
    }

    public IdGenerator(Set<String> ids) {
        this.ids = ids;
    }

    public boolean isDuplicate(String id) {
        return isCheckDuplicates && ids.contains(id);
    }

    public void add(String id) {
        ids.add(id);
    }

    public String getNextId() {
        // Skip existing ids to handle these cases:
        // o user uses attribute of the form xf-*
        // o XBL copies id attributes from bound element, so within template the id may be of the form xf-*
        String newId = AUTOMATIC_ID_PREFIX + currentId;
        while (ids.contains(newId)) {
            currentId++;
            newId = AUTOMATIC_ID_PREFIX + currentId;
        }

        currentId++;

        return newId;
    }

    public Iterator<String> iterator() {
        return ids.iterator();
    }

    public int getCurrentId() {
        return currentId;
    }

    public void setCheckDuplicates(boolean check) {
        this.isCheckDuplicates = check;
    }
}
