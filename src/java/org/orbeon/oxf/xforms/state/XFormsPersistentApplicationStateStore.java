/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.state;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.XFormsProperties;

/**
 * This store keeps XFormsState instances into an application store and persists data going over a given size.
 */
public class XFormsPersistentApplicationStateStore extends XFormsStateStore {

    private static final String PERSISTENT_STATE_STORE_APPLICATION_KEY = "oxf.xforms.state.store.persistent-application-key";

    public synchronized static XFormsStateStore instance(ExternalContext externalContext) {
        {
            final XFormsStateStore existingStateStore
                    = (XFormsStateStore) externalContext.getAttributesMap().get(PERSISTENT_STATE_STORE_APPLICATION_KEY);

            if (existingStateStore != null)
                return existingStateStore;

        }
        {
            final XFormsStateStore newStateStore = new XFormsPersistentApplicationStateStore(true);
            newStateStore.debug("created new store.");
            externalContext.getAttributesMap().put(PERSISTENT_STATE_STORE_APPLICATION_KEY, newStateStore);
            return newStateStore;
        }
    }


    private XFormsPersistentApplicationStateStore(boolean isPersistent) {
        super(isPersistent);
    }

    protected int getMaxSize() {
        return XFormsProperties.getApplicationStateStoreSize();
    }

    protected String getStoreDebugName() {
        return "global application";
    }
}
