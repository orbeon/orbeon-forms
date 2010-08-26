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
    synchronized public static void init(Map props) {

        // Create resource factory according to properties
        final String factoryImp = (String) props.get(FACTORY_PROPERTY);
        if (factoryImp == null) {
            throw new OXFException("Declaration of resource factory missing: no value declared for property '" 
                    + FACTORY_PROPERTY + "'");
        }
        try {
            final Class c = Class.forName(factoryImp);
            final Constructor constructor = c.getConstructor(Map.class);
            factory = (ResourceManagerFactoryFunctor) constructor.newInstance(props);
            instance = null;
        } catch (ClassNotFoundException e) {
            throw new OXFException("class " + factoryImp + "not found", e);
        } catch (Exception e) {
            throw new OXFException("Can't instanciate factory " + factoryImp, e);
        }
    }


    /**
     * Returns an instance of ResourceManager
     */
    synchronized public static ResourceManager instance() {
        if (instance == null) {
            instance = (factory == null) ? makeInstance() : factory.makeInstance();
        }
        return instance;
    }

    /**
     * Sets a new factory. Nulls the current instance so a new one will be created
     */
    synchronized public static void setFactory(ResourceManagerFactoryFunctor factory) {
        ResourceManagerWrapper.factory = factory;
        instance = null;
    }

    /**
     * Sets the current Singleton instance.
     * You can set this to null to force a new instance to be created the
     * next time instance() is called.
     * @param instance The Singleton instance to use.
     */
    synchronized public static void setInstance(ResourceManager instance) {
        ResourceManagerWrapper.instance = instance;
    }


    /**
     * Calls the factory to create a ResourceManager instance
     */
    private static ResourceManager makeInstance() {
        return factory.makeInstance();
    }
}
