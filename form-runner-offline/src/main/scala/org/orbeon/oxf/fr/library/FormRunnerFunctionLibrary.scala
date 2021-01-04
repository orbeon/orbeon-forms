package org.orbeon.oxf.fr.library


import cats.syntax.option._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.dom.QName
import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.common.VersionSupport
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.process.SimpleProcess
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xforms.analysis.{PartAnalysisForStaticMetadataAndProperties, model}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.xxforms.XXFormsComponentParam
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.value.{AtomicValue, StringValue}
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable._


object FormRunnerFunctionLibrary extends OrbeonFunctionLibrary {

  lazy val namespaces = List(XMLNames.FR -> XMLNames.FRPrefix)

  @XPathFunction def mode                     : String         = FormRunnerParams().mode
  @XPathFunction def appName                  : String         = FormRunnerParams().app
  @XPathFunction def formName                 : String         = FormRunnerParams().form
  @XPathFunction def documentId               : Option[String] = FormRunnerParams().document
  @XPathFunction def formTitle                : Option[String] = FormRunner.formTitleFromMetadata
  @XPathFunction def lang                     : String         = FormRunner.currentLang
  @XPathFunction def workflowStageValue       : Option[String] = FormRunner.documentWorkflowStage
  @XPathFunction def username                 : Option[String] = CoreCrossPlatformSupport.externalContext.getRequest.credentials map     (_.username)
  @XPathFunction def userGroup                : Option[String] = CoreCrossPlatformSupport.externalContext.getRequest.credentials flatMap (_.group)
//  @XPathFunction def relevantFormValuesString : String         = FormRunnerMetadata.findAllControlsWithValues(html = false)
//  @XPathFunction def wizardCurrentPageName    : Option[String] = Wizard.wizardCurrentPageNameOpt

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

  @XPathFunction def formVersion              : Int            = FormRunnerParams().formVersion

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
  def runProcessByName(scope: String, name: String): Boolean = {
    println(s"TODO: `runProcessByName($scope, $name)` called")
    SimpleProcess.runProcessByName(scope, name).isSuccess
  }

  @XPathFunction
  def runProcess(scope: String, process: String): Boolean =
    SimpleProcess.runProcess(scope, process).isSuccess

  // TODO: How to deal with the rewrite
//    Fun("dataset", classOf[FRDataset], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )

//  // TODO: Handle `XFormsFunction.Context` parameter
//  @XPathFunction
//  def controlStringValue(targetControlName: String, followIndexes: Boolean = false)(ctx: XFormsFunction.Context): Option[String] =
//    FormRunner.resolveTargetRelativeToActionSourceOpt(
//      actionSourceAbsoluteId = XFormsId.effectiveIdToAbsoluteId(ctx.sourceEffectiveId),
//      targetControlName      = targetControlName,
//      followIndexes          = followIndexes
//    ) flatMap
//      (_.nextOption()) map
//      (_.getStringValue)
//
//  // TODO: Handle `XFormsFunction.Context` parameter
//  @XPathFunction
//  def controlTypedValue(targetControlName: String, followIndexes: Boolean = false)(ctx: XFormsFunction.Context): Option[AtomicValue] = {
//
//  }

//  Namespace(List(XMLNames.FR)) {

//    Fun("control-typed-value", classOf[FRControlTypedValue], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//

  // Example: `fr:component-param-value('decimal-separator')`
  //
  // This searches bound element attributes, the `fr-form-metadata` instance, and properties.
  //
  // We search `fr-form-metadata` statically, since we know that it is readonly and inline.
  //
  @XPathFunction
  def componentParamValue(paramNameString: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[AtomicValue] = {

    val paramName = QName(paramNameString)

    import XXFormsComponentParam._

    findSourceComponent(XFormsFunction.context) flatMap { sourceComponent =>

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

/*
private object FormRunnerFunctions {


  class StringFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): StringValue =
      IndexedStringFunctions(operation).apply()
  }

  class BooleanFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): BooleanValue =
      IndexedBooleanFunctions(operation).apply()
  }

  class IntFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): IntegerValue =
      IndexedIntFunctions(operation).apply()
  }

  class DateTimeFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): DateTimeValue =
      IndexedDateTimeFunctions(operation).apply() map
        (new java.util.Date(_))                   map
        DateTimeValue.fromJavaDate                orNull
  }

  class FRRunProcessByName extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      SimpleProcess.runProcessByName(stringArgument(0)(context), stringArgument(1)(context)).isSuccess
  }

  class FRRunProcess extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      SimpleProcess.runProcess(stringArgument(0)(context), stringArgument(1)(context)).isSuccess
  }

  class FRDataset extends SystemFunction {

    // Rewrite `fr:dataset($arg1)` into `instance(concat('fr-dataset-', $arg1))`
    override def simplify(visitor: ExpressionVisitor): Expression = {

      simplifyArguments(visitor)

      val concatFn =
        SystemFunction.makeSystemFunction(
          "concat",
          Array(new StringLiteral("fr-dataset-"), getArguments()(0))
        )

      // From `Expression.java`: "The rule is that an implementation of simplify(), typeCheck(), or optimize()
      // that returns a value other than `this` is required to set the location information and parent pointer
      // in the new child expression."
      concatFn.setContainer(getContainer)
      ExpressionTool.copyLocationInfo(this, concatFn)

      val instanceFn = new function.Instance
      instanceFn.setDetails(XFormsFunctionLibrary.getEntry(XFORMS_NAMESPACE_URI, "instance", 1).get)
      instanceFn.setFunctionName(new StructuredQName("", XFORMS_NAMESPACE_URI, "instance"))
      instanceFn.setArguments(Array(concatFn))

      instanceFn.setContainer(getContainer)
      ExpressionTool.copyLocationInfo(this, instanceFn)

      instanceFn.simplify(visitor)

      instanceFn
    }
  }

  class FRControlTypedValue extends FunctionSupport with RuntimeDependentFunction {

    override def evaluateItem(context: XPathContext): Item = {

      implicit val ctx = context

      val resolvedItems =
        FormRunner.resolveTargetRelativeToActionSourceOpt(
          actionSourceAbsoluteId = XFormsId.effectiveIdToAbsoluteId(XFormsFunction.context.sourceEffectiveId),
          targetControlName      = stringArgument(0),
          followIndexes          = booleanArgumentOpt(1) getOrElse false
        ) getOrElse
          Iterator.empty

      val allItems =
        resolvedItems map { item =>
          try {
            // `TypedNodeWrapper.getTypedValue` *should* return a single value or throw
            Option(item.getTypedValue.next()) getOrElse EmptySequence.getInstance
          } catch {
            case _: TypedValueException => EmptySequence.getInstance
          }
        }

      ArrayFunctions.createValue(allItems.to(Vector))
    }
  }

}

*/

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
