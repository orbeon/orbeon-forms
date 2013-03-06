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
package org.orbeon.oxf.processor;

import org.dom4j.QName;

import java.util.HashMap;
import java.util.Map;

public class ProcessorFactoryRegistry {

    private static Map nameToFactoryMap = new HashMap();

    public static void bind(String name, ProcessorFactory processorFactory) {
        nameToFactoryMap.put(name, processorFactory);
    }

    public static void bind(QName qName, ProcessorFactory processorFactory) {
        nameToFactoryMap.put(qName, processorFactory);
    }

    public static ProcessorFactory lookup(String name) {
        return (ProcessorFactory) nameToFactoryMap.get(name);
    }

    public static ProcessorFactory lookup(QName qName) {
        return (ProcessorFactory) nameToFactoryMap.get(qName);
    }
}
