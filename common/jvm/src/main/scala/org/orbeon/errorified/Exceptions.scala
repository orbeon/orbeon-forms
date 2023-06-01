// Distributed under the MIT license, see: http://www.opensource.org/licenses/MIT
package org.orbeon.errorified


// Exceptions utilities
// Uses reflection to find nested causes when exceptions don't support Java's getCause
object Exceptions {

  // Returns the exception directly nested
  // This first tries reflection and then falls back to the standard getCause
  def getNestedThrowable(t: Throwable): Option[Throwable] = {

    // Create a map of all classes and interfaces implemented by the throwable
    val throwableClasses = {
      def superIterator     = Iterator.iterate[Class[_]](t.getClass)(_.getSuperclass) takeWhile (_ ne null)
      def interfaceIterator = t.getClass.getInterfaces.iterator

      // NOTE: toMap's type parameters are needed with 2.9.2, but not with 2.10
      (superIterator ++ interfaceIterator map (c => c.getName -> c)).toMap[String, Class[_]]
    }

    // Invoke the given getter on t
    def invokeGetter(clazz: Class[_], getter: String): Option[Throwable] =
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

  // Typically for Java callers
  def getNestedThrowableOrNull(t: Throwable) = getNestedThrowable(t) orNull

  // Iterator down a throwable's causes, always non-empty
  def causesIterator(t: Throwable): Iterator[Throwable] =
    Iterator.iterate(t)(getNestedThrowableOrNull).takeWhile(_ ne null)

  // Get the root cause of the throwable
  def getRootThrowable(t: Throwable): Throwable =
    causesIterator(t).toList.last

  val Getters = Seq(
    "javax.xml.transform.TransformerException"              -> "getException",
    "org.xml.sax.SAXException"                              -> "getException",
    "java.lang.reflect.InvocationTargetException"           -> "getTargetException",
    "javax.servlet.ServletException"                        -> "getRootCause",
    "org.apache.batik.transcoder.TranscoderException"       -> "getException",
    "orbeon.apache.xml.utils.WrappedRuntimeException"       -> "getException",
    "org.iso_relax.verifier.VerifierConfigurationException" -> "getCauseException",
    "com.drew.lang.CompoundException"                       -> "getInnerException",
    "com.lowagie.text.ExceptionConverter"                   -> "getException",
    "java.lang.Throwable"                                   -> "getCause"
  )
}
