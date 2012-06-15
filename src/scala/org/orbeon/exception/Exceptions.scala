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
package org.orbeon.exception

object Exceptions {

    // Returns the exception directly nested
    // This first tries reflection and then falls back to the standard getCause
    def getNestedThrowable(t: Throwable): Throwable = {

        // Create a map of all classes and interfaces implemented by the throwable
        val throwableClasses = {
            def superIterator     = Iterator.iterate[Class[_]](t.getClass)(_.getSuperclass) takeWhile (_ ne null)
            def interfaceIterator = t.getClass.getInterfaces.iterator

            // NOTE: toMap's type parameters are needed with 2.9.2, but not with 2.10
            (superIterator ++ interfaceIterator map (c ⇒ c.getName → c)).toMap[String, Class[_]]
        }

        // Invoke the given getter on t
        def invokeGetter(clazz: Class[_], getter: String): Option[Throwable] =
            try {
                val method = clazz.getMethod(getter)
                val result = method.invoke(t)
                Option(result.asInstanceOf[Throwable])
            } catch {
                case _ ⇒ None
            }

        // Try to find a match with getters, or else fallback to getCause
        Getters find
            { case (clazz, _)      ⇒ throwableClasses.contains(clazz) } flatMap
            { case (clazz, getter) ⇒ invokeGetter(throwableClasses(clazz), getter) } getOrElse
            t.getCause
    }

    // Iterator down a throwable's causes
    def causesIterator(t: Throwable) = Iterator.iterate(t)(getNestedThrowable(_)).takeWhile(_ ne null)

    // Get the root cause of the throwable
    def getRootThrowable(t: Throwable): Throwable = causesIterator(t).toList.lastOption.orNull

    val Getters = Seq(
        "javax.xml.transform.TransformerException"              → "getException",
        "org.xml.sax.SAXException"                              → "getException",
        "java.lang.reflect.InvocationTargetException"           → "getTargetException",
        "javax.servlet.ServletException"                        → "getRootCause",
        "org.apache.batik.transcoder.TranscoderException"       → "getException",
        "orbeon.apache.xml.utils.WrappedRuntimeException"       → "getException",
        "org.iso_relax.verifier.VerifierConfigurationException" → "getCauseException",
        "com.drew.lang.CompoundException"                       → "getInnerException",
        "java.lang.Throwable"                                   → "getCause"
    )
}
