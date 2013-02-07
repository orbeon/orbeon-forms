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
package org.orbeon.oxf.resources;

import org.orbeon.oxf.common.OXFException;

import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * This is the main interface the to the Resource Manager system. Users must call the static <code>instance()</code>
 * method to get the current Resource Manager singleton.
 */
final public class ResourceManagerWrapper {

    public static final String FACTORY_PROPERTY = "oxf.resources.factory";

    private static ResourceManager instance = null;
    private static ResourceManagerFactoryFunctor factory = null;

    /**
     *  Initialize the factory
     */
    synchronized public static void init(Map<String, Object> props) {

        // Create resource factory according to properties
        final String factoryImpl = (String) props.get(FACTORY_PROPERTY);
        if (factoryImpl == null) {
            throw new OXFException("Declaration of resource factory missing: no value declared for property '" 
                    + FACTORY_PROPERTY + "'");
        }
        try {
            final Class<ResourceManagerFactoryFunctor> factoryClass = (Class<ResourceManagerFactoryFunctor>) Class.forName(factoryImpl);
            final Constructor<ResourceManagerFactoryFunctor> constructor = factoryClass.getConstructor(Map.class);
            factory = constructor.newInstance(props);
        } catch (ClassNotFoundException e) {
            throw new OXFException("Class " + factoryImpl + "not found", e);
        } catch (Exception e) {
            throw new OXFException("Can't instantiate factory " + factoryImpl, e);
        }
    }

    /**
     * Returns an instance of ResourceManager
     */
    synchronized public static ResourceManager instance() {

        if (factory == null)
            throw new IllegalStateException("ResourceManagerWrapper not initialized");

        if (instance == null)
            instance = factory.makeInstance();

        return instance;
    }
}
