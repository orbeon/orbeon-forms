package org.orbeon.oxf.fr.library


import cats.syntax.option._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.dom.QName
import org.orbeon.dom.saxon.TypedNodeWrapper.TypedValueException
import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.common.VersionSupport
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.process.SimpleProcess
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xforms.analysis.{PartAnalysisForStaticMetadataAndProperties, model}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.xxforms.XXFormsComponentParam
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.{OrbeonFunctionLibrary, SaxonUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.value.{AtomicValue, EmptySequence, StringValue}
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable._


object FormRunnerFunctionLibrary extends OrbeonFunctionLibrary {

  lazy val namespaces = List(XMLNames.FR -> XMLNames.FRPrefix)

  @XPathFunction def mode                     : String         = FormRunnerParams().mode
  @XPathFunction def appName                  : String         = FormRunnerParams().app
  @XPathFunction def formName                 : String         = FormRunnerParams().form
  @XPathFunction def documentId               : Option[String] = FormRunnerParams().document
  @XPathFunction def formVersion              : Int            = FormRunnerParams().formVersion

  @XPathFunction def formTitle                : Option[String] = FormRunner.formTitleFromMetadata
  @XPathFunction def lang                     : String         = FormRunner.currentLang
  @XPathFunction def workflowStageValue       : Option[String] = FormRunner.documentWorkflowStage
  @XPathFunction def username                 : Option[String] = CoreCrossPlatformSupport.externalContext.getRequest.credentials map     (_.username)
  @XPathFunction def userGroup                : Option[String] = CoreCrossPlatformSupport.externalContext.getRequest.credentials flatMap (_.group)
//  @XPathFunction def relevantFormValuesString : String         = FormRunnerMetadata.findAllControlsWithValues(html = false)
//  @XPathFunction def wizardCurrentPageName    : Option[String] = Wizard.wizardCurrentPageNameOpt

  @XPathFunction def isBrowserEnvironment     : Boolean        = CoreCrossPlatformSupport.isJsEnv
  @XPathFunction def isPe                     : Boolean        = CoreCrossPlatformSupport.isPE
  @XPathFunction def isDesignTime             : Boolean        = FormRunner.isDesignTime(FormRunnerParams())
  @XPathFunction def isReadonlyMode           : Boolean        = FormRunner.isReadonlyMode(FormRunnerParams())
  @XPathFunction def isNoscript               : Boolean        = false
//  @XPathFunction def isFormDataValid          : Boolean        = countValidationsByLevel(ErrorLevel) == 0
  @XPathFunction def isFormDataSaved          : Boolean        = FormRunner.isFormDataSaved
  @XPathFunction def isWizardTocShown         : Boolean        = false // Wizard.isWizardTocShown  // XXX TODO: migrate Wizard
  @XPathFunction def isWizardBodyShown        : Boolean        = false // Wizard.isWizardBodyShown // XXX TODO: migrate Wizard
  @XPathFunction def isWizardFirstPage        : Boolean        = false // Wizard.isWizardFirstPage // XXX TODO: migrate Wizard
  @XPathFunction def isWizardLastPage         : Boolean        = false // Wizard.isWizardLastPage  // XXX TODO: migrate Wizard
  @XPathFunction def canCreate                : Boolean        = FormRunner.canCreate
  @XPathFunction def canRead                  : Boolean        = FormRunner.canRead
  @XPathFunction def canUpdate                : Boolean        = FormRunner.canUpdate
  @XPathFunction def canDelete                : Boolean        = FormRunner.canDelete
  @XPathFunction def ownsLeaseOrNoneRequired  : Boolean        = FormRunner.userOwnsLeaseOrNoneRequired

  @XPathFunction(name = "created-dateTime")
  def createdDateTime                         : Option[java.time.Instant] = FormRunner.documentCreatedDate.map(java.time.Instant.ofEpochMilli)
  @XPathFunction(name = "modified-dateTime")
  def modifiedDateTime                        : Option[java.time.Instant] = FormRunner.documentModifiedDate.map(java.time.Instant.ofEpochMilli)

  @XPathFunction
  def userRoles: List[String] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials.toList flatMap (_.roles map (_.roleName))

  @XPathFunction
  def userOrganizations: List[String] =
    for {
        credentials <- CoreCrossPlatformSupport.externalContext.getRequest.credentials.toList
        org         <- credentials.organizations
        leafOrg     <- org.levels.lastOption.toList
      } yield
        leafOrg

  @XPathFunction
  def ancestorOrganizations(leafOrgParam: String): List[String] = {

    // There should be only one match if the organizations are well-formed
    val foundOrgs =
      for {
          credentials <- CoreCrossPlatformSupport.externalContext.getRequest.credentials.toList
          org         <- credentials.organizations
          if org.levels.lastOption contains leafOrgParam
        } yield
          org

      foundOrgs.headOption match {
        case Some(foundOrg) => foundOrg.levels.init.reverse
        case None           => Nil
      }
    }

  @XPathFunction
  def runProcessByName(scope: String, name: String): Boolean =
    SimpleProcess.runProcessByName(scope, name).isSuccess

  @XPathFunction
  def runProcess(scope: String, process: String): Boolean =
    SimpleProcess.runProcess(scope, process).isSuccess

  @XPathFunction
  def dataset(datasetName: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[om.NodeInfo] =
    XFormsFunctionLibrary.instanceImpl("fr-dataset-" + datasetName)

  @XPathFunction
  def controlStringValue(
    targetControlName : String,
    followIndexes     : Boolean = false)(implicit
    ctx               : XFormsFunction.Context
  ): Option[String] =
    FormRunner.resolveTargetRelativeToActionSourceOpt(
      actionSourceAbsoluteId = XFormsId.effectiveIdToAbsoluteId(ctx.sourceEffectiveId),
      targetControlName      = targetControlName,
      followIndexes          = followIndexes
    ) flatMap
      (_.nextOption()) map
      (_.getStringValue)

  @XPathFunction
  def controlTypedValue(
    targetControlName : String,
    followIndexes     : Boolean = false)(implicit
    ctx               : XFormsFunction.Context
  ): Option[om.Item] = { // should be `Option[ArrayItem]`

    // TODO: Clarify when `resolveTargetRelativeToActionSourceOpt` returns `None`. Is it only
    //   when the controls cannot be resolved? Any other condition?
    val resolvedTarget: Option[Iterator[om.Item]] =
      FormRunner.resolveTargetRelativeToActionSourceOpt(
        actionSourceAbsoluteId = XFormsId.effectiveIdToAbsoluteId(ctx.sourceEffectiveId),
        targetControlName      = targetControlName,
        followIndexes          = followIndexes
      )

    resolvedTarget map { resolvedItems =>

      val allItems =
        resolvedItems map { item =>
          try {
            item.atomize().head
          } catch {
            case _: TypedValueException => EmptySequence
          }
        }

      SaxonUtils.newArrayItem(allItems.toList)
    }
  }

  // Example: `fr:component-param-value('decimal-separator')`
  //
  // This searches bound element attributes, the `fr-form-metadata` instance, and properties.
  //
  // We search `fr-form-metadata` statically, since we know that it is readonly and inline.
  //
  @XPathFunction
  def componentParamValue(paramNameString: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[AtomicValue] = {

    // TODO: Support QName params + QName resolution? For now, all callers pass a `String`.
    //  See `getQNameFromItem`.
    val paramName = QName(paramNameString)

    import XXFormsComponentParam._

    findSourceComponent flatMap { sourceComponent =>

      val staticControl   = sourceComponent.staticControl
      val concreteBinding = staticControl.bindingOrThrow

      def fromAttributes: Option[AtomicValue] =
        fromElem(
          atts        = concreteBinding.boundElementAtts.lift,
          paramName   = paramName
        )

      def fromMetadataAndProperties: Option[AtomicValue] =
        FRComponentParam.fromMetadataAndProperties(
          partAnalysis  = sourceComponent.container.partAnalysis,
          directNameOpt = staticControl.commonBinding.directName,
          paramName     = paramName
        )

      fromAttributes orElse fromMetadataAndProperties map {
        case paramValue: StringValue => stringToStringValue(sourceComponent.evaluateAvt(paramValue.getStringValue))
        case paramValue              => paramValue
      }
    }
  }

  // TODO: Macro to handle `Map` results.
//  @XPathFunction
//  def pdfTemplates: Iterable[Map[String, Option[String]]] =
//    FormRunnerRenderedFormat.listPdfTemplates map { case PdfTemplate(path, nameOpt, langOpt) =>
//      Map(
//        "path" -> path.some,
//        "name" -> nameOpt,
//        "lang" -> langOpt
//      )
//    }
//
//  def iterate(context: XPathContext): SequenceIterator =
//    FormRunnerRenderedFormat.listPdfTemplates map { template =>
//      MapFunctions.createValue(
//        Map[AtomicValue, ValueRepresentation](
//          (SaxonUtils.fixStringValue("path"), template.path),
//          (SaxonUtils.fixStringValue("name"), template.nameOpt map stringToStringValue getOrElse EmptySequence.getInstance),
//          (SaxonUtils.fixStringValue("lang"), template.langOpt map stringToStringValue getOrElse EmptySequence.getInstance)
//        )
//      )
//    }

  @XPathFunction
  def createdWithOrNewer(paramVersion: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Boolean = {
    val metadataVersionOpt =
      for {
        sourceControl      <- xfc.container.associatedControlOpt
        part               = sourceControl.container.partAnalysis
        metadata           <- FRComponentParam.findConstantMetadataRootElem(part)
        createdWithVersion <- metadata elemValueOpt Names.CreatedWithVersion
      } yield
        createdWithVersion

    metadataVersionOpt match {
      case None =>
        // If no version info the metadata, or no metadata, do as if the form was created with an old version
        false
      case Some(metadataVersion) =>
        VersionSupport.compare(metadataVersion, paramVersion).exists(_ >= 0)
    }
  }
}

object FRComponentParam {

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
  //   <metadata>
  //     <xbl>
  //       <fr:number>
  //         <decimal-separator>'</decimal-separator>
  //       </fr:number>
  //     </xbl>
  //   </metadata>
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
      partAnalysis.ancestorOrSelfIterator flatMap FRComponentParam.findConstantMetadataRootElem

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
