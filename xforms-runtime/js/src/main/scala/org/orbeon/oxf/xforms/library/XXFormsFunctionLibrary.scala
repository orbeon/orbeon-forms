package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.XFormsFunction._
import org.orbeon.oxf.xml.{OrbeonFunctionLibrary, SaxonUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.function.CoreSupport
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.saxon.{IndependentRequestFunctions, om}
import org.orbeon.xforms.{Namespaces, XFormsNames}


object XXFormsFunctionLibrary
  extends OrbeonFunctionLibrary
    with XXFormsEnvFunctions
    with IndependentFunctions
    with IndependentRequestFunctions {

  private val XFormsPropertyPrefix = "oxf.xforms."

  lazy val namespaces = List(Namespaces.XXF -> XFormsNames.XXFORMS_SHORT_PREFIX)

  @XPathFunction
  def binding(staticOrAbsoluteId: String)(implicit xpc: XPathContext): Iterable[om.Item] =
    findControlsByStaticOrAbsoluteId(staticOrAbsoluteId, followIndexes = true)
      .headOption.toList.flatMap(_.bindingEvenIfNonRelevant)

  @XPathFunction
  def property(propertyName: String)(implicit xfc: XFormsFunction.Context): Option[AtomicValue] = {

    def fromContainingDocumentProperties =
      propertyName.startsWith(XFormsPropertyPrefix) option {
        val shortName = propertyName.substringAfter(XFormsPropertyPrefix)
        SaxonUtils.convertJavaObjectToSaxonObject(xfc.containingDocument.getProperty(shortName)).asInstanceOf[AtomicValue]
      }

    def fromOtherProperties =
      CoreSupport.property(propertyName)

    fromContainingDocumentProperties orElse fromOtherProperties
  }
}
