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
package org.orbeon.oxf.xforms.state;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.XFormsProperties;

/**
 * This store keeps XFormsState instances into a session.
 */
public class XFormsSessionStateStore extends XFormsStateStore {

    private static final String SESSION_STATE_STORE_SESSION_KEY = "oxf.xforms.state.store.session-key";

    public synchronized static XFormsStateStore instance(ExternalContext externalContext, boolean createSessionIfNeeded) {
        final ExternalContext.Session session = externalContext.getSession(createSessionIfNeeded);
        if (session != null) {
            {
                final XFormsStateStore existingStateStore
                        = (XFormsStateStore) session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE).get(SESSION_STATE_STORE_SESSION_KEY);

                if (existingStateStore != null)
                    return existingStateStore;

            }
            {
                final XFormsStateStore newStateStore = new XFormsSessionStateStore(false);
                newStateStore.debug("created new store.");
                session.getAttributesMap().put(SESSION_STATE_STORE_SESSION_KEY, newStateStore);
                return newStateStore;
            }
        } else {
            return null;
        }
    }

    private XFormsSessionStateStore(boolean isPersistent) {
        super(isPersistent);
    }

    protected int getMaxSize() {
        return XFormsProperties.getSessionStoreSize();
    }

    protected String getStoreDebugName() {
        return "session";
    }
}
