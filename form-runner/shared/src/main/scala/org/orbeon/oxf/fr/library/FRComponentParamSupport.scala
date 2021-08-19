package org.orbeon.oxf.fr.library

import cats.syntax.option._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.oxf.xforms.analysis.{PartAnalysisForStaticMetadataAndProperties, model}
import shapeless.syntax.typeable._
import org.orbeon.saxon.om
import org.orbeon.saxon.value._
import org.orbeon.dom.QName
import org.orbeon.oxf.fr.{AppForm, Names}
import org.orbeon.oxf.xforms.analysis.{PartAnalysisForStaticMetadataAndProperties, model}
import org.orbeon.oxf.xforms.function.xxforms.XXFormsComponentParam
import org.orbeon.oxf.util.CollectionUtils._


object FRComponentParamSupport {

  import org.orbeon.scaxon.SimplePath._

  def findConstantMetadataRootElem(part: PartAnalysisForStaticMetadataAndProperties): Option[om.NodeInfo] = {

    // Tricky: When asking for the instance, the instance might not yet have been indexed, while the
    // `ElementAnalysis` has been. So we get the element instead of using `findInstanceInScope`.
    val instancePrefixedId = part.startScope.prefixedIdForStaticId(Names.MetadataInstance)

    for {
      elementAnalysis <- part.findControlAnalysis(instancePrefixedId)
      instance        <- elementAnalysis.narrowTo[model.Instance]
      constantContent <- instance.constantContent
    } yield
      constantContent.rootElement
  }

  // Find in a hierarchical structure, such as:
  //
  // <metadata>
  //   <xbl>
  //     <fr:number>
  //       <decimal-separator>'</decimal-separator>
  //     </fr:number>
  //   </xbl>
  // </metadata>
  //
  def findHierarchicalElem(directNameOpt: Option[QName], paramName: QName, rootElem: om.NodeInfo): Option[String] =
    for {
      directName    <- directNameOpt
      xblElem       <- rootElem.firstChildOpt(XXFormsComponentParam.XblLocalName)
      componentElem <- xblElem.firstChildOpt(directName)
      paramValue    <- componentElem.attValueOpt(paramName)
    } yield
      paramValue

  // Instead of using `FormRunnerParams()`, we use the form definition metadata.
  // This also allows support of the edited form in Form Builder.
  def appFormFromMetadata(constantMetadataRootElem: om.NodeInfo): Option[AppForm] =
    for {
      appName                  <- constantMetadataRootElem elemValueOpt Names.AppName
      formName                 <- constantMetadataRootElem elemValueOpt Names.FormName
    } yield
      AppForm(appName, formName)

  def fromMetadataAndProperties(
    partAnalysis  : PartAnalysisForStaticMetadataAndProperties,
    directNameOpt : Option[QName],
    paramName     : QName
  ): Option[AtomicValue] = {

    def iterateMetadataInParts =
      partAnalysis.ancestorOrSelfIterator flatMap findConstantMetadataRootElem

    def fromMetadataInstance: Option[StringValue] =
      iterateMetadataInParts                                flatMap
        (findHierarchicalElem(directNameOpt, paramName, _)) map
        StringValue.makeStringValue                         nextOption()

    def fromPropertiesWithSuffix: Option[AtomicValue] =
      iterateMetadataInParts flatMap
        appFormFromMetadata  flatMap
        (appForm => XXFormsComponentParam.fromProperties(paramName, appForm.toList, directNameOpt)) nextOption()

    def fromPropertiesWithoutSuffix: Option[AtomicValue] =
      XXFormsComponentParam.fromProperties(paramName, Nil, directNameOpt)

    fromMetadataInstance       orElse
      fromPropertiesWithSuffix orElse
      fromPropertiesWithoutSuffix
  }
}