package org.orbeon.oxf.xforms

import cats.syntax.option.*
import org.orbeon.dom.saxon.TypedNodeWrapper
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.debug
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.util.control.NonFatal


object NonFatalCheckTypedValueException {
  def unapply(throwable: Throwable)(implicit logger: IndentedLogger): Option[(Throwable, Boolean)] =
    XFormsCrossPlatformSupport.getRootThrowable(throwable) match {
      case t: TypedNodeWrapper.TypedValueException =>
        // Consider type validation errors as ignorable. The rationale is that if the function (the XPath
        // expression) works on inputs that are not valid (hence the validation error), then the function cannot
        // produce a meaningful result. We think that it is worth handling this condition slightly differently
        // from other dynamic and static errors, so that users can just write expression without constant checks
        // with `castable as` or `instance of`.
        debug("typed value exception", List(
          "node name"     -> t.nodeName,
          "expected type" -> t.typeName,
          "actual value"  -> t.nodeValue
        ))
        (throwable -> true).some
      case NonFatal(_) =>
        (throwable -> false).some
      case _               =>
        None
    }
  }