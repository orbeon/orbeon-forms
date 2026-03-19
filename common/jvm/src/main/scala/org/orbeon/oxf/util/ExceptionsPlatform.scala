// Distributed under the MIT license, see: http://www.opensource.org/licenses/MIT
package org.orbeon.oxf.util


trait ExceptionsPlatform extends ExceptionsTrait {

  Exceptions =>

  // Returns the exception directly nested
  // This first tries reflection and then falls back to the standard getCause
  def findNestedThrowable(t: Throwable): Option[Throwable] = {

    // Create a map of all classes and interfaces implemented by the throwable
    val throwableClasses = {
      def superIterator     = Iterator.iterate[Class[?]](t.getClass)(_.getSuperclass) takeWhile (_ ne null)
      def interfaceIterator = t.getClass.getInterfaces.iterator

      // NOTE: toMap's type parameters are needed with 2.9.2, but not with 2.10
      (superIterator ++ interfaceIterator map (c => c.getName -> c)).toMap[String, Class[?]]
    }

    // Invoke the given getter on t
    def invokeGetter(clazz: Class[?], getter: String): Option[Throwable] =
      try {
        val method = clazz.getMethod(getter)
        val result = method.invoke(t)
        Option(result.asInstanceOf[Throwable])
      } catch {
        case _: Throwable => None
      }

    // Try to find a match
    Getters find
      { case (clazz, _)      => throwableClasses.contains(clazz) } flatMap
      { case (clazz, getter) => invokeGetter(throwableClasses(clazz), getter) }
  }

  val Getters: Seq[(String, String)] = Seq(
    "javax.xml.transform.TransformerException"              -> "getException",
    "org.xml.sax.SAXException"                              -> "getException",
    "java.lang.reflect.InvocationTargetException"           -> "getTargetException",
    "javax.servlet.ServletException"                        -> "getRootCause",
    "jakarta.servlet.ServletException"                      -> "getRootCause",
    "org.apache.batik.transcoder.TranscoderException"       -> "getException",
    "orbeon.apache.xml.utils.WrappedRuntimeException"       -> "getException",
    "org.iso_relax.verifier.VerifierConfigurationException" -> "getCauseException",
    "com.drew.lang.CompoundException"                       -> "getInnerException",
    "com.lowagie.text.ExceptionConverter"                   -> "getException",
    "java.lang.Throwable"                                   -> "getCause"
  )
}
