package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, LangRef}
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.XFormsFunction.{elementAnalysisForSource, resolveOrFindByStaticOrAbsoluteId}
import org.orbeon.oxf.xforms.function.xxforms.XXFormsResourceSupport.{findResourceElementForLang, pathFromTokens, splitResourceName}
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.saxon.function.ProcessTemplateSupport
import org.orbeon.saxon.om
import org.orbeon.xforms.runtime.XFormsObject


object XXFormsLangSupport {

  def resolveXMLangHandleAVTs(containingDocument: XFormsContainingDocument, element: ElementAnalysis): Option[String] =
    element.getLangUpdateIfUndefined match {
      case LangRef.Literal(value) =>
        Some(value)
      case LangRef.AVT(att) =>
        val attributeControl = containingDocument.getControlByEffectiveId(att.staticId).asInstanceOf[XXFormsAttributeControl]
        Option(attributeControl.getExternalValue(EventCollector.Throw))
      case _ =>
        None
    }

  def r(
    resourceKey       : String,
    instanceOpt       : Option[String],
    javaNamedParamsOpt: => Option[List[(String, Any)]],
    fallbackLangOpt   : Option[String] = None
  )(implicit
    xfc               : XFormsFunction.Context
  ): Option[String] = {

    def findInstance: Option[XFormsObject] =
      instanceOpt match {
        case Some(instanceName) => resolveOrFindByStaticOrAbsoluteId(instanceName)
        case None               => resolveOrFindByStaticOrAbsoluteId("orbeon-resources") orElse resolveOrFindByStaticOrAbsoluteId("fr-form-resources")
      }

    def findResourcesElement: Option[om.NodeInfo] =
      findInstance.collect { case instance: XFormsInstance => instance.rootElement }

    def processResourceString(resourceOrTemplate: String): String =
      javaNamedParamsOpt match {
        case Some(javaNamedParams) =>
          ProcessTemplateSupport.processTemplateWithNames(resourceOrTemplate, javaNamedParams)
        case _ =>
          resourceOrTemplate
      }

      for {
        elementAnalysis <- elementAnalysisForSource
        resources       <- findResourcesElement
        requestedLang   <- XXFormsLangSupport.resolveXMLangHandleAVTs(xfc.containingDocument, elementAnalysis)
        resourceRoot    <- findResourceElementForLang(resources, requestedLang, fallbackLangOpt)
        leaf            <- pathFromTokens(resourceRoot, splitResourceName(resourceKey)).headOption
      } yield
        processResourceString(leaf.getStringValue)
  }
}
