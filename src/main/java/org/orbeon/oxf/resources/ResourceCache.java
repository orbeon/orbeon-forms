/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.resources;

import org.w3c.dom.Node;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ResourceCache {

    /**
     * Stores keys in the form
     *  /one/two/three --> xml document
     */
    protected Map cache = null;
    protected Map lastModified = null;

    public ResourceCache() {
        cache = new HashMap();
        lastModified = new HashMap();
    }

    public void addDocument(String key, Node doc, Date last) {
        cache.put(key, doc);
        lastModified.put(key, last);
    }

    public Node getDocument(String key) {
        return (Node) cache.get(key);
    }

    public void removeDocument(String key) {
        cache.remove(key);
        lastModified.remove(key);
    }

    public void deleteItemOlder(String key, Date date) {
        Date last = (Date) lastModified.get(key);
        if (date.after(last)) {
            lastModified.remove(key);
            cache.remove(key);
        }
    }

    public Date getLastModified(String key) {
        return (Date) lastModified.get(key);
    }


}
