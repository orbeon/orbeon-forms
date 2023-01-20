package org.orbeon.oxf.xforms.function.xxforms

import shapeless.syntax.typeable._
import org.orbeon.dom.QName
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.control.{Controls, XFormsComponentControl}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.value.{AtomicValue, StringValue}
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.{XFormsId, XFormsNames}


object ComponentParamSupport {

  val XblLocalName = XFormsNames.XBL_XBL_QNAME.localName

  def findSourceComponent(sourceComponentIdOpt: Option[String])(implicit xfc: XFormsFunction.Context): Option[XFormsComponentControl] = {

    def fromParamOpt: Option[XFormsComponentControl] =
      sourceComponentIdOpt flatMap { sourceComponentId =>
        Controls.resolveControlsById(
          containingDocument       = XFormsFunction.context.containingDocument,
          sourceControlEffectiveId = XFormsFunction.context.sourceEffectiveId,
          targetStaticId           = sourceComponentId,
          followIndexes            = false
        ).headOption
      } collect {
        case c: XFormsComponentControl => c
        case _ => throw new IllegalArgumentException
      }

    def fromCurrentComponent: Option[XFormsComponentControl] = {

      val prefixedId              = XFormsId.getPrefixedId(xfc.sourceEffectiveId)
      val containerForSourceScope = xfc.container.findScopeRoot(prefixedId)

      containerForSourceScope.associatedControlOpt flatMap (_.narrowTo[XFormsComponentControl])
    }

    fromParamOpt orElse fromCurrentComponent
  }

  // NOTE: In the future, we would like constant values to be available right away, and
  // AVTs to support dependencies. Those should probably be stored lazily at the control
  // level.
  def fromElemAlsoTryAvt(
     atts            : QName => Option[String],
     evaluateAvt     : String => String,
     paramName       : QName
   ): Option[StringValue] =
     atts(paramName) map evaluateAvt map stringToStringValue

  def fromElem(
    atts            : QName => Option[String],
    paramName       : QName
  ): Option[AtomicValue] =
    atts(paramName) map stringToStringValue

  def fromProperties(
    paramName       : QName,
    paramSuffix     : List[String],
    directNameOpt   : Option[QName],
    property        : String => Option[AtomicValue]
  ): Option[AtomicValue] = {

    val propertyNameOpt =
      findPropertyParts(directNameOpt, paramName) map { parts =>
        "oxf" :: "xforms" :: parts ::: paramSuffix mkString "."
      }

    // NOTE: We currently don't have a way, besides removing a property entirely, to indicate that a property is
    // `null` or `None`. For properties like `number.digits-after-decimal`, `number.prefix`, etc., we do need
    // such a way. So if the value is a blank string (which means the value is actually a blank `xs:string` or maybe
    // `xs:anyURI`), consider the property missing. We could revise this in the future to make a distinction between
    // a blank or empty string and a missing property.
    propertyNameOpt flatMap property filter (_.getStringValue.nonAllBlank)
  }

  // For example `xbl.fr.number.decimal-separator`
  private def findPropertyParts(directNameOpt: Option[QName], paramName: QName): Option[List[String]] =
    directNameOpt map { qName =>
      XblLocalName :: qName.namespace.prefix :: qName.localName :: paramName.localName :: Nil
    }

}
