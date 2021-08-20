package org.orbeon.oxf.fr.library

import org.orbeon.scaxon.SimplePath._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.saxon.om
import org.orbeon.saxon.value._
import org.orbeon.dom.QName
import org.orbeon.oxf.fr.{AppForm, FormRunner, FormRunnerParams, Names}
import org.orbeon.oxf.xforms.analysis.{PartAnalysisForStaticMetadataAndProperties, model}
import org.orbeon.oxf.xforms.function.xxforms.XXFormsComponentParam
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import shapeless.syntax.typeable._


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

  // https://github.com/orbeon/orbeon-forms/issues/4919
  def formAttachmentVersion(implicit p: FormRunnerParams): Int =
    if (FormRunner.isDesignTime || p.mode == "test") {
      // The form definition is not published yet and we need to figure out the library form version

      val appsIt =
        for {
          staticControl <- XFormsFunction.context.container.associatedControlOpt.flatMap(_.staticControlOpt).iterator
          ancestor      <- ElementAnalysis.ancestorsIterator(staticControl, includeSelf = false)
          if ancestor.isInstanceOf[ComponentControl]
          app           <- FormRunner.findAppFromSectionTemplateUri(ancestor.element.getNamespaceURI)
        } yield
          app

      val appOpt = appsIt.nextOption()

      if (appOpt.isDefined) {

        val libraryVersionOpt =
          for {
            sourceControl                     <- XFormsFunction.context.container.associatedControlOpt
            part                              = sourceControl.container.partAnalysis
            metadata                          <- FRComponentParamSupport.findConstantMetadataRootElem(part)
            (orbeonVersionOpt, appVersionOpt) = findLibraryVersions(metadata)
            libraryVersion                    <- if (appOpt.contains(Names.GlobalLibraryAppName)) orbeonVersionOpt else appVersionOpt
          } yield
            libraryVersion

        libraryVersionOpt map (_.toInt) getOrElse 1

      } else {
        p.formVersion
      }
    } else {
      // All static attachments are copied to the published form definition and therefore use the form definition version
      p.formVersion
    }

  def findLibraryVersions(metadataRootElem: om.NodeInfo): (Option[Int], Option[Int]) = {

    val libraryVersionsOpt =
      for
        (libraryVersions <- metadataRootElem firstChildOpt Names.LibraryVersionsElemName)
      yield
        (
          libraryVersions elemValueOpt Names.GlobalLibraryVersionElemName map (_.toInt),
          libraryVersions elemValueOpt Names.AppLibraryVersionElemName    map (_.toInt)
        )

    libraryVersionsOpt match {
      case Some(versions) => versions
      case None           => (None, None)
    }
  }
}