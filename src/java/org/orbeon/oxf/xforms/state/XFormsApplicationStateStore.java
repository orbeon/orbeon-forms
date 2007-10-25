/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
 * This store keeps XFormsState instances into the application scope.
 */
public class XFormsApplicationStateStore extends XFormsStateStore {

    private static final String APPLICATION_STATE_CACHE_APPLICATION_KEY = "oxf.xforms.state.store.application-key";

    public synchronized static XFormsStateStore instance(ExternalContext externalContext) {
        {
            final XFormsStateStore existingStateStore
                    = (XFormsStateStore) externalContext.getAttributesMap().get(APPLICATION_STATE_CACHE_APPLICATION_KEY);

            if (existingStateStore != null)
                return existingStateStore;

        }
        {
            final XFormsStateStore newStateStore = new XFormsApplicationStateStore();
            externalContext.getAttributesMap().put(APPLICATION_STATE_CACHE_APPLICATION_KEY, newStateStore);
            return newStateStore;
        }
    }

    protected int getMaxSize() {
        return XFormsProperties.getApplicationCacheSize();
    }

    protected String getStoreDebugName() {
        return "application";
    }
}
