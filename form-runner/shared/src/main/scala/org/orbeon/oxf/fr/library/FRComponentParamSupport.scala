package org.orbeon.oxf.fr.library

import cats.syntax.option.*
import org.orbeon.dom.QName
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, PartAnalysisForStaticMetadataAndProperties, model}
import org.orbeon.oxf.xforms.control.Controls.AncestorOrSelfIterator
import org.orbeon.oxf.xforms.control.{ControlXPathSupport, XFormsComponentControl, XFormsControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.xxforms.{ComponentParamSupport, EvaluateSupport}
import org.orbeon.saxon.om
import org.orbeon.saxon.value.*
import org.orbeon.scaxon.Implicits.*
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable.*


object FRComponentParamSupport {

  import org.orbeon.scaxon.SimplePath.*

  // Implementation of `fr:component-param-value()`
  def componentParamValue(
    paramName            : QName,
    sourceComponentIdOpt : Option[String],
    property             : String => Option[AtomicValue])(implicit
    xfc                  : XFormsFunction.Context
  ): Option[AtomicValue] =
    ComponentParamSupport.findSourceComponent(sourceComponentIdOpt) flatMap { sourceComponent =>

      val staticControl   = sourceComponent.staticControl
      val concreteBinding = staticControl.bindingOrThrow

      def fromAttributes: Option[AtomicValue] =
        ComponentParamSupport.fromElem(
          atts        = concreteBinding.boundElementAtts.lift,
          paramName   = paramName
        )

      def fromMetadataAndProperties: Option[AtomicValue] =
        FRComponentParamSupport.fromMetadataAndProperties(
          partAnalysis  = sourceComponent.container.partAnalysis,
          directNameOpt = staticControl.commonBinding.directName,
          paramName     = paramName,
          property      = property
        )

      fromAttributes orElse fromMetadataAndProperties flatMap {
        case paramValue: StringValue => sourceComponent.evaluateAvt(paramValue.getStringValue, EventCollector.Throw).map(stringToStringValue)
        case paramValue              => paramValue.some
      }
    }

  // Implementation of `fr:component-param-value-by-type()`
  // 2024-11-06: undocumented and no callers.
  def componentParamValueByType(
    paramName  : QName,
    directName : QName,
    property   : String => Option[AtomicValue])(implicit
    xfc        : XFormsFunction.Context
  ): Option[AtomicValue] = {

    def fromMetadataAndProperties: Option[AtomicValue] =
      FRComponentParamSupport.fromMetadataAndProperties(
        partAnalysis  = xfc.container.partAnalysis,
        directNameOpt = directName.some,
        paramName     = paramName,
        property      = property
      )

    fromMetadataAndProperties flatMap {
      case paramValue: StringValue =>
        ControlXPathSupport.evaluateAvt(
          attributeValue    = paramValue.getStringValue,
          bindingContext    = xfc.bindingContext,
          namespaceMappings = xfc.namespaceMapping,
          container         = xfc.container,
          locationData      = xfc.bindingContext.locationData,
          eventTarget       = xfc.container.eventTarget,
          collector         = EventCollector.Throw
        ).map(stringToStringValue)
      case paramValue =>
        paramValue.some
    }
  }

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
  def findHierarchicalElem(directNameOpt: Option[QName])(paramName: QName, rootElem: om.NodeInfo): Option[String] =
    for {
      directName    <- directNameOpt
      xblElem       <- rootElem.firstChildOpt(ComponentParamSupport.XblLocalName)
      componentElem <- xblElem.firstChildOpt(directName)
      paramValue    <- componentElem.attValueOpt(paramName)
    } yield
      paramValue

  // Find in a top-level name, such as:
  //
  // <metadata>
  //   <data-migration>enabled</data-migration>
  // </metadata>
  //
  def findTopLevelElem(paramName: QName, rootElem: om.NodeInfo): Option[String] =
    rootElem.elemValueOpt(paramName)

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
    paramName     : QName,
    property      : String => Option[AtomicValue]
  ): Option[AtomicValue] =
    fromMetadataAndPropertiesSupport(
      partAnalysis     = partAnalysis,
      findInMetadata   = findHierarchicalElem(directNameOpt),
      findInProperties = (paramName, appFormOpt) => ComponentParamSupport.fromProperties(paramName, appFormOpt.map(_.toList).getOrElse(Nil), directNameOpt, property),
      paramName        = paramName
    )

  def fromMetadataAndPropertiesEvaluateAvt(
    xfcd          : XFormsContainingDocument,
    featureName   : String,
    propertyName  : String
  )(implicit
    indentedLogger: IndentedLogger
  ): Option[String] = {

    def buildPropertyName(appFormOpt: Option[AppForm]) =
      appFormOpt match {
        case Some(appForm) => frc.buildPropertyName(propertyName, appForm)
        case None          => propertyName
      }

    def findInProperties(appFormOpt: Option[AppForm]): Option[AtomicValue] = {
      FormRunnerRename.replaceVarReferencesWithFunctionCallsFromPropertyAsExpr(
        propertyName    = buildPropertyName(appFormOpt),
        avt             = true,
        libraryName     = None,
        norewrite       = Set.empty,
        functionLibrary = xfcd.functionLibrary
      )
      .map { rewrittenXPathExpr =>
        EvaluateSupport.evaluateInContextFromXPathExpr(
          rewrittenXPathExpr,
          Names.FormModel,
          xfcd,
          xfcd.getDefaultModel.getDefaultInstance.rootElement
        )
      }
      .map(_.collect { case v: AtomicValue => v }) // we know this returns only a `StringValue` since it is an AVT
      .flatMap(_.headOption)
    }

    // `fromMetadataAndPropertiesSupport()` doesn't evaluate AVTs, so we need to do it via `findInProperties()`
    FRComponentParamSupport.fromMetadataAndPropertiesSupport(
      partAnalysis     = xfcd.staticState.topLevelPart,
      findInMetadata   = FRComponentParamSupport.findTopLevelElem,
      findInProperties = (_, appFormOpt) => findInProperties(appFormOpt),
      paramName        = featureName
    ).map(_.getStringValue)
  }

  def fromMetadataAndPropertiesSupport(
    partAnalysis    : PartAnalysisForStaticMetadataAndProperties,
    findInMetadata  : (QName, om.NodeInfo)     => Option[String],
    findInProperties: (QName, Option[AppForm]) => Option[AtomicValue],
    paramName       : QName,
  ): Option[AtomicValue] = {

    def iterateMetadataInParts =
      partAnalysis.ancestorOrSelfIterator flatMap findConstantMetadataRootElem

    def fromMetadataInstance: Option[StringValue] =
      (
        iterateMetadataInParts         flatMap
        (findInMetadata(paramName, _)) map
        StringValue.makeStringValue
      ).nextOption()

    def fromPropertiesWithSuffix: Option[AtomicValue] =
      (
        iterateMetadataInParts flatMap
        appFormFromMetadata    flatMap
        (appForm => findInProperties(paramName, appForm.some))
      ).nextOption()

    def fromPropertiesWithoutSuffix: Option[AtomicValue] =
      findInProperties(paramName, None)

    fromMetadataInstance       orElse
      fromPropertiesWithSuffix orElse
      fromPropertiesWithoutSuffix
  }

  // Called by attachments XBL controls from XPath with `fr:attachment-form-version()`, to pass the header
  // `Orbeon-Form-Definition-Version`.
  // https://github.com/orbeon/orbeon-forms/issues/4919
  def formAttachmentVersion(implicit p: FormRunnerParams): Int =
    if (frc.isDesignTime || p.mode == "test") {
      // The form definition is not published yet, and we need to figure out the library form version

      val associatedControlOpt = XFormsFunction.context.container.associatedControlOpt

      val sectionTemplateAppsIt =
        for {
          staticControl <- associatedControlOpt.flatMap(_.staticControlOpt).iterator
          ancestor      <- ElementAnalysis.ancestorsIterator(staticControl, includeSelf = false)
          if ancestor.isInstanceOf[ComponentControl]
          app           <- frc.findAppFromSectionTemplateUri(ancestor.element.getNamespaceURI)
        } yield
          app

      sectionTemplateAppsIt.nextOption() match {
        case Some(sectionTemplateApp) =>

          def findLibraryVersionForApp(metadata: om.NodeInfo): Option[Int] = {
            val (globalLibraryVersionOpt, specialLibraryVersionOpt, appLibraryVersionOpt) = findLibraryVersions(metadata)
            sectionTemplateApp match {
              case Names.GlobalLibraryAppName  => globalLibraryVersionOpt
              case Names.SpecialLibraryAppName => specialLibraryVersionOpt
              case _                           => appLibraryVersionOpt
            }
          }

          val libraryVersionOpt =
            for {
              sourceControl  <- associatedControlOpt
              part           = sourceControl.container.partAnalysis
              metadata       <- FRComponentParamSupport.findConstantMetadataRootElem(part)
              libraryVersion <- findLibraryVersionForApp(metadata)
            } yield
              libraryVersion

          libraryVersionOpt.getOrElse(1)
        case None      =>
          p.formVersion
      }
    } else {
      // All static attachments are copied to the published form definition and therefore use the form definition version
      p.formVersion
    }

  def findLibraryVersions(metadataRootElem: om.NodeInfo): (Option[Int], Option[Int], Option[Int]) = {

    val libraryVersionsOpt =
      for
        (libraryVersions <- metadataRootElem firstChildOpt Names.LibraryVersionsElemName)
      yield
        (
          libraryVersions elemValueOpt Names.GlobalLibraryVersionElemName map (_.toInt),
          libraryVersions elemValueOpt Names.OrbeonLibraryVersionElemName map (_.toInt),
          libraryVersions elemValueOpt Names.AppLibraryVersionElemName    map (_.toInt),
        )

    libraryVersionsOpt.getOrElse((None, None, None))
  }

  def ancestorSectionsIt(control: XFormsControl): Iterator[XFormsComponentControl] =
    new AncestorOrSelfIterator(control.parent) collect {
      case cc: XFormsComponentControl if cc.staticControl.element.getQName == XMLNames.FRSectionQName => cc
    }

  private val ContainerQNames = Set(XMLNames.FRSectionQName, XMLNames.FRGridQName)

  def ancestorContainersIt(control: XFormsControl): Iterator[XFormsComponentControl] =
    new AncestorOrSelfIterator(control.parent) collect {
      case cc: XFormsComponentControl if ContainerQNames(cc.staticControl.element.getQName) => cc
    }

  def ancestorContainerNamesIt(control: XFormsControl): Iterator[String] =
    ancestorContainersIt(control).map(s => frc.controlNameFromId(s.staticControl.staticId))

  def topLevelSectionNameForControlId(absoluteControlId: String): Option[String] =
    inScopeContainingDocument.findControlByEffectiveId(XFormsId.absoluteIdToEffectiveId(absoluteControlId))
      .flatMap(topLevelAncestorSectionName)

  def ancestorSectionNames(control: XFormsControl): Iterator[String] =
    ancestorSectionsIt(control).map(s => frc.controlNameFromId(s.staticControl.staticId))

  def topLevelAncestorSectionName(control: XFormsControl): Option[String] =
    ancestorSectionNames(control).lastOption()

  def closestAncestorSectionName(control: XFormsControl): Option[String] =
    ancestorSectionNames(control).nextOption()
}