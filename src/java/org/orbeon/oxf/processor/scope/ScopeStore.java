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
package org.orbeon.oxf.processor.scope;

import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.xml.SAXStore;

import java.io.Serializable;

/**
 * Instances of this class are stored in the application, session, or request
 * attributes.
 */
public class ScopeStore implements Serializable {

    private SAXStore saxStore;
    private OutputCacheKey key;
    private Object validity;

    public ScopeStore(SAXStore saxStore, OutputCacheKey key, Object validity) {
        this.saxStore = saxStore;
        this.key = key;
        this.validity = validity;
    }

    public SAXStore getSaxStore() {
        return saxStore;
    }

    public OutputCacheKey getKey() {
        return key;
    }

    public Object getValidity() {
        return validity;
    }
}
