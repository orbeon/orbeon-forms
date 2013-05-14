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

import java.util.Map;

public class CacheResourceManagerFactory implements ResourceManagerFactoryFunctor {

    public static final String CACHE_URL = "oxf.resources.cache.url";
    public static final String CACHE_USERNAME = "oxf.resources.cache.username";
    public static final String CACHE_PASSWORD = "oxf.resources.cache.password";

    Map props;

    public CacheResourceManagerFactory(Map props) {
        this.props = props;
    }

    public ResourceManager makeInstance() {
        return new CacheResourceManagerImpl(props);
    }
}
