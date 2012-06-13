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
package org.orbeon.oxf.common;

import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class OXFException extends RuntimeException {

    public OXFException(String message) {
        super(message);
    }

    public OXFException(Throwable throwable) {
        super(throwable);
    }

    public OXFException(String message, Throwable cause) {
        super(message, cause);
    }

    private static final String[] exceptionGetters = {
        "javax.servlet.ServletException", "getRootCause",
        "org.apache.batik.transcoder.TranscoderException", "getException",
        "orbeon.apache.xml.utils.WrappedRuntimeException", "getException",
        "org.iso_relax.verifier.VerifierConfigurationException", "getCauseException",
        "com.drew.lang.CompoundException", "getInnerException",
        "java.lang.Throwable", "getCause" // Since JDK 1.4, all exceptions may support this
    };

    /**
     * Returns the exception directly nested in <code>e</code>.
     */
    public static Throwable getNestedThrowable(Throwable t) {
        Throwable nested = null;
        // First statically check for built-in classes
        if (t instanceof TransformerException) {
            nested = ((TransformerException) t).getException();
        } else if (t instanceof SAXException) {
            nested = ((SAXException) t).getException();
        } else if (t instanceof InvocationTargetException) {
            nested = ((InvocationTargetException) t).getTargetException();
        } else {
            // Try to get a nested throwable by using introspection
            // Create a map of all classes and interfaces implemented by the throwable
            final Map<String, Class> throwableClasses = new HashMap<String, Class>();
            final Class throwableClass = t.getClass();
            throwableClasses.put(throwableClass.getName(), throwableClass);
            Class superClass = throwableClass;
            while ((superClass = superClass.getSuperclass()) != null) {
                throwableClasses.put(superClass.getName(), superClass);
            }
            final Class[] interfaces = throwableClass.getInterfaces();
            for (int i = 0; i < interfaces.length; i++) {
                throwableClasses.put(interfaces[i].getName(), interfaces[i]);
            }

            // Try to find a match with getters
            for (int i = 0; i < exceptionGetters.length; i += 2) {
                final Class c = (Class) throwableClasses.get(exceptionGetters[i]);
                if (c != null) {
                    // Found
                    final String getterName = exceptionGetters[i + 1];
                    try {
                        final Method method = c.getMethod(getterName);
                        final Object returnValue = method.invoke(t);
                        if (returnValue instanceof Throwable)// It should be
                            nested = (Throwable) returnValue;
                        break;
                    } catch (Exception f) {
                        // Ignore. In particular, we ignore because of JDK 1.4's
                        // getCause() which may not be implemented.
                    }
                }
            }

            // Fallback to
            if (nested == null)
                nested = t.getCause();
        }
        return nested;
    }

    /**
     * Get exception at the source of the problem.
     */
    public static Throwable getRootThrowable(Throwable e) {
        while (true) {
            Throwable nested = getNestedThrowable(e);
            if (nested == null) break;
            e = nested;
        }
        return e;
    }
}