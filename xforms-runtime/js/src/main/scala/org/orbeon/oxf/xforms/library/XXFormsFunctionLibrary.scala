package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.xforms.function.XFormsFunction._
import org.orbeon.saxon.IndependentRequestFunctions
import org.orbeon.oxf.xml.{OrbeonFunctionLibrary, SaxonUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.xforms.{Namespaces, XFormsNames}
import org.orbeon.saxon.om
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.saxon.function.CoreSupport


object XXFormsFunctionLibrary
  extends OrbeonFunctionLibrary
    with IndependentRequestFunctions {

  private val XFormsPropertyPrefix = "oxf.xforms."

  lazy val namespaces = List(Namespaces.XXF -> XFormsNames.XXFORMS_SHORT_PREFIX)

  @XPathFunction
  def binding(staticOrAbsoluteId: String)(implicit xpc: XPathContext): Iterable[om.Item] =
    findControlsByStaticOrAbsoluteId(staticOrAbsoluteId, followIndexes = true)
      .headOption.toList.flatMap(_.bindingEvenIfNonRelevant)

  @XPathFunction
  def property(propertyName: String): Option[AtomicValue] =
    CoreSupport.property(propertyName)

}
