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

import org.orbeon.oxf.util.StringBuilderWriter;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OXFException extends RuntimeException {

    private Throwable nestedThrowable;

    private static final String[] exceptionGetters = {
        "javax.servlet.ServletException", "getRootCause",
        "org.apache.batik.transcoder.TranscoderException", "getException",
        "javax.portlet.PortletException", "getCause",
        "orbeon.apache.xml.utils.WrappedRuntimeException", "getException",
        "org.iso_relax.verifier.VerifierConfigurationException", "getCauseException",
        "com.drew.lang.CompoundException", "getInnerException",
        "java.lang.Throwable", "getCause" // Since JDK 1.4, all exceptions may support this
    };

    private static final String[] exceptionFields = {
        "com.sun.msv.verifier.jarv.FactoryImpl$WrapperException", "e" // This doesn't work because the class is private
    };

    /**
     * Return a throwable stack trace as a string.
     *
     * @param throwable throwable
     * @return          stack trace
     */
    public static String throwableToString(Throwable throwable) {
        final StringBuilderWriter sb = new StringBuilderWriter();
        final PrintWriter writer = new PrintWriter(sb);
        throwable.printStackTrace(writer);
        return sb.toString();
    }

    /**
     * Returns the exception directly nested in <code>e</code>.
     */
    public static Throwable getNestedException(Throwable t) {
        Throwable nested = null;
        // First statically check for built-in classes
        if (t instanceof OXFException) {
            nested = ((OXFException) t).getNestedException();
        } else if (t instanceof TransformerException) {
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
            if (nested == null) {
                // Try to find a match with fields
                for (int i = 0; i < exceptionFields.length; i += 2) {
                    final Class c = (Class) throwableClasses.get(exceptionFields[i]);
                    if (c != null) {
                        // Found
                        final String fieldName = exceptionFields[i + 1];
                        try {
                            final Field field = c.getField(fieldName);
                            final Object returnValue = field.get(t);
                            if (returnValue instanceof Throwable)// It should be
                                nested = (Throwable) returnValue;
                            break;
                        } catch (Exception f) {
                            // Ignore. In particular, we ignore because of JDK 1.4's
                            // getCause() which may not be implemented.
                        }
                    }
                }
            }
        }
        return nested;
    }

    private static boolean methodsInitialized;
    private static Method getStackTraceMethod;
    private static Method getClassNameMethod;
    private static Method getMethodNameMethod;
    private static Method getFileNameMethod;
    private static Method getLineNumberMethod;

    /**
     * Return the stack trace of a throwable.
     *
     * This method uses introspection. It will return null on Java versions
     * prior to 1.4.
     */
    public static StackTraceElement[] getStackTraceElements(Throwable t) {
        // TODO: we require 1.5 or later so stop using introspection below
        try {
            if (!methodsInitialized)
                getStackTraceMethod = t.getClass().getMethod("getStackTrace");
            Object returnValue = getStackTraceMethod.invoke(t);
            if (returnValue == null)
                return null;
            if (!(returnValue instanceof Object[]))
                return null; // this should not happen
            Object[] elements = (Object[]) returnValue;
            List<StackTraceElement> result = new ArrayList<StackTraceElement>();
            for (int i = 0; i < elements.length; i++) {
                Object element = elements[i];
                Class elementClass = element.getClass();

                if (!methodsInitialized) {
                    getClassNameMethod = elementClass.getMethod("getClassName");
                    getMethodNameMethod = elementClass.getMethod("getMethodName");
                    getFileNameMethod = elementClass.getMethod("getFileName");
                    getLineNumberMethod = elementClass.getMethod("getLineNumber");
                    methodsInitialized = true;
                }

                result.add(new StackTraceElement((String) getClassNameMethod.invoke(element),
                        (String) getMethodNameMethod.invoke(element),
                        (String) getFileNameMethod.invoke(element),
                        ((Integer) getLineNumberMethod.invoke(element)).intValue()));
            }
            StackTraceElement[] arrayResult = new StackTraceElement[result.size()];
            result.toArray(arrayResult);
            return arrayResult;
        } catch (Throwable f) {
            // Definitely give up if anything is caught
            return null;
        }
    }

    /**
     * Get exception at the source of the problem.
     */
    public static Throwable getRootThrowable(Throwable e) {
        while (true) {
            Throwable nested = getNestedException(e);
            if (nested == null) break;
            e = nested;
        }
        return e;
    }

    public OXFException(String message) {
        super(message);
    }

    public OXFException(Throwable throwable) {
        this(throwable.getMessage(), throwable);
    }

    public OXFException(String message, Throwable throwable) {
        super(message);
        this.nestedThrowable = throwable;
    }

    public Throwable getNestedException() {
        return nestedThrowable;
    }

    public void printStackTrace(java.io.PrintStream s) {
        if (nestedThrowable != null)
            nestedThrowable.printStackTrace(s);
        else
            super.printStackTrace(s);
    }

    public void printStackTrace() {
        if (nestedThrowable != null)
            nestedThrowable.printStackTrace();
        else
            super.printStackTrace();
    }

    public String getMessage() {
        if (nestedThrowable != null)
            return nestedThrowable.getMessage();
        else
            return super.getMessage();
    }

    public static class StackTraceElement {

        private String className;
        private String methodName;
        private String fileName;
        private int lineNumber;

        public StackTraceElement(String className, String methodName, String fileName, int lineNumber) {
            this.className = className;
            this.methodName = methodName;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
        }

        public boolean isNativeMethod() {
            return lineNumber == -2;
        }

        public String toString() {
            return getClassName() + "." + methodName +
                    (isNativeMethod() ? "(Native Method)" :
                    (fileName != null && lineNumber >= 0 ?
                    "(" + fileName + ":" + lineNumber + ")" :
                    (fileName != null ? "(" + fileName + ")" : "(Unknown Source)")));
        }

        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!(obj instanceof StackTraceElement))
                return false;
            StackTraceElement e = (StackTraceElement) obj;
            return e.className.equals(className) && e.lineNumber == lineNumber
                    && eq(methodName, e.methodName) && eq(fileName, e.fileName);
        }

        private static boolean eq(Object a, Object b) {
            return a == b || (a != null && a.equals(b));
        }

        public int hashCode() {
            int result = 31 * className.hashCode() + methodName.hashCode();
            result = 31 * result + (fileName == null ? 0 : fileName.hashCode());
            result = 31 * result + lineNumber;
            return result;
        }

        public String getClassName() {
            return className;
        }

        public String getFileName() {
            return fileName;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getMethodName() {
            return methodName;
        }
    }
}