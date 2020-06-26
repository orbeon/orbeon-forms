/**
 * Copyright (C) 2013 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fb

import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.Names
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis._
import org.orbeon.oxf.xforms.analysis.model.Model._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevel._
import org.orbeon.oxf.xforms.analysis.model.{Model, ValidationLevel}
import org.orbeon.oxf.xforms.function.xxforms.{ExcludedDatesValidation, UploadMediatypesValidation}
import org.orbeon.oxf.xforms.xbl.BindingDescriptor
import org.orbeon.oxf.xml.SaxonUtils.parseQName
import org.orbeon.oxf.xml.{XMLConstants, XMLUtils}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._

import scala.{xml => sx}
import scala.collection.compat._

trait AlertsAndConstraintsOps extends ControlOps {

  self: GridOps => // funky dependency, to resolve at some point

  private val OldAlertRefMatcher = """\$form-resources/([^/]+)/(\w+)(?:\[(\d+)\])?""".r
  private val NewAlertRefMatcher = """xxf:r\('([^.]+)\.(\w+)(?:\.(\d+))?'\)""".r

  val OldStandardAlertRef = """$fr-resources/detail/labels/alert"""

  def readValidationsAsXML(controlName: String)(implicit ctx: FormBuilderDocContext): List[NodeInfo] =
    RequiredValidation.fromForm(controlName)    ::
    DatatypeValidation.fromForm(controlName)    ::
    ConstraintValidation.fromForm(controlName)  map
    (v => elemToNodeInfo(v.toXML(currentLang)))

  def writeAlertsAndValidationsAsXML(
    controlName      : String,
    newAppearance    : String,
    defaultAlertElem : NodeInfo,
    validationElems  : Seq[NodeInfo])(implicit
    ctx              : FormBuilderDocContext
  ): Unit = {

    val inDoc = ctx.formDefinitionRootElem

    // Current resolutions, which could be lifted in the future:
    //
    // - writes are destructive: they remove all xf:alert, alert resources, and validations for the control
    // - we don't allow editing the validation id, but we preserve it when possible

    val validationElemsSeq = validationElems.to(List)

    // Extract from XML
    val allValidations = {
      val idsIterator = nextTmpIds(token = Names.Validation, count = validationElemsSeq.size).toIterator
      validationElemsSeq map (v => v -> (v attValue "type")) flatMap {
        case (e, Required.name) => Some(RequiredValidation.fromXml(e, idsIterator))
        case (e, "datatype")    => Some(DatatypeValidation.fromXml(e, idsIterator, inDoc, controlName))
        case (e, _)             => ConstraintValidation.fromXmlOpt(e, idsIterator)
      }
    }

    val defaultAlert = AlertDetails.fromXML(defaultAlertElem, None)

    // We expect only one "required" validation
    allValidations collectFirst {
      case v: RequiredValidation => v
    } foreach { v =>
      writeValidations(
        controlName,
        Required,
        List(v)
      )
    }

    // We expect only one "datatype" validation
    allValidations collect {
      case v: DatatypeValidation => v
    } foreach { v =>

      v.renameControlIfNeeded(controlName, newAppearance.trimAllToOpt)

      writeValidations(
        controlName,
        Type,
        List(v)
      )
    }

    // Several "constraint" validations are supported
    writeValidations(
      controlName,
      Constraint,
      allValidations collect { case v: ConstraintValidation => v }
    )

    writeAlerts(
      controlName,
      allValidations,
      defaultAlert
    )
  }

  private def writeValidations(
    controlName : String,
    mip         : ValidateMIP,
    validations : List[Validation])(implicit
    ctx         : FormBuilderDocContext
  ): Unit = {

    val inDoc = ctx.formDefinitionRootElem

    val bind = findBindByName(inDoc, controlName).get

    val existingAttributeValidations = mipAtts (bind, mip)
    val existingElementValidations   = mipElems(bind, mip)

    val (_, mipElemQName) = mipToFBMIPQNames(mip)

    validations match {
      case Nil =>
        delete(existingAttributeValidations ++ existingElementValidations)
      case List(Validation(_, ErrorLevel, value, None)) =>

        // Single validation without custom alert: set @fb:mipAttName and remove all nested elements
        // See also: https://github.com/orbeon/orbeon-forms/issues/1829
        // NOTE: We could optimize further by taking this branch if there is no type or required validation.
        writeAndNormalizeMip(controlName, mip, value)
        delete(existingElementValidations)
      case _ =>
        val nestedValidations =
          validations flatMap { case Validation(idOpt, level, value, _) =>

            value.trimAllToOpt match {
              case Some(nonEmptyValue) =>

                val prefix = mipElemQName.namespace.uri match {
                  case XMLNames.FB => XMLNames.FBPrefix // also covers the case of `xxf:default` (Form Builder names here)
                  case XF          => "xf" // case of `xf:type`, `xf:required`
                }

                val dummyMIPElem =
                  <xf:dummy
                    id={idOpt.orNull}
                    level={if (level != ErrorLevel) level.entryName else null}
                    value={if (mip != Type) nonEmptyValue else null}
                    xmlns:xf={XF}
                    xmlns:fb={XMLNames.FB}>{if (mip == Type) nonEmptyValue else null}</xf:dummy>

                List(dummyMIPElem.copy(prefix = prefix, label = mipElemQName.localName): NodeInfo)
              case None =>
                Nil
            }
          }

        delete(existingAttributeValidations ++ existingElementValidations)
        insertElementsImposeOrder(into = bind, origin = nestedValidations, AllMIPNamesInOrder)
    }
  }

  // Write resources and alerts for those that have resources
  // If the default alert has resources, write it as well
  private def writeAlerts(
    controlName  : String,
    validations  : List[Validation],
    defaultAlert : AlertDetails)(implicit
    ctx          : FormBuilderDocContext
  ): Unit = {

    val alertsWithResources = {

      val alertsForValidations =
        validations collect
          { case Validation(_, _, _, Some(alert)) => alert }

      val nonGlobalDefaultAlert =
        ! defaultAlert.global list defaultAlert

      alertsForValidations ::: nonGlobalDefaultAlert
    }

    val messagesByLangForAllLangs = {

      def messagesForAllLangs(a: AlertDetails) = {
        val messagesMap = a.messages.toMap
        allLangs(resourcesRoot) map { lang => lang -> messagesMap.getOrElse(lang, "") }
      }

      val messagesByLang = (
        alertsWithResources
        flatMap messagesForAllLangs
        groupBy (_._1)
        map     { case (lang, values) => lang -> (values map (_._2)) }
      )

      // Make sure we have a default for all languages if there are no alerts or if some languages are missing
      // from the alerts. We do want to update all languages on write, including removing unneeded <alert>
      // elements.
      val defaultMessages = allLangs(resourcesRoot) map (_ -> Nil)

      defaultMessages.toMap ++ messagesByLang toList
    }

    setControlResourcesWithLang(controlName, "alert", messagesByLangForAllLangs)

    // Write alerts
    val newAlertElements =
      ensureCleanLHHAElements(
        controlName = controlName,
        lhhaName    = LHHA.Alert.entryName,
        count       = alertsWithResources.size,
        replace     = true
      )

    // Insert validation attribute as needed
    newAlertElements zip alertsWithResources foreach {
      case (e, AlertDetails(Some(forValidationId), _, _)) =>
        insert(into = e, origin = NodeInfoFactory.attributeInfo(VALIDATION_QNAME, forValidationId))
      case _ => // no attributes to insert if this is not an alert linked to a validation
    }

    // Write global default alert if needed
    if (defaultAlert.global) {
      val newGlobalAlert = ensureCleanLHHAElements(controlName, LHHA.Alert.entryName, count = 1, replace = false).head
      setvalue(newGlobalAlert /@ "ref", OldStandardAlertRef)
    }
  }

  sealed trait Validation {
    def idOpt       : Option[String]
    def level       : ValidationLevel
    def stringValue : String
    def alert       : Option[AlertDetails]

    def toXML(forLang: String)(implicit ctx: FormBuilderDocContext): sx.Elem
  }

  object Validation {

    def unapply(v: Validation) =
      Some((v.idOpt, v.level, v.stringValue, v.alert))

    def levelFromXML(validationElem: NodeInfo) =
      LevelByName(validationElem attValue "level")
  }

  // Required is either a simple boolean or a custom XPath expression
  case class RequiredValidation(
    idOpt    : Option[String],
    required : Either[Boolean, String],
    alert    : Option[AlertDetails]
  ) extends Validation {

    import RequiredValidation._

    def level       = ErrorLevel
    def stringValue = eitherToXPath(required)

    def toXML(forLang: String)(implicit ctx: FormBuilderDocContext): sx.Elem =
      <validation type={Required.name} level={level.entryName} default-alert={alert.isEmpty.toString}>
        <required>{eitherToXPath(required)}</required>
        {alertOrPlaceholder(alert, forLang)}
      </validation>
  }

  object RequiredValidation {

    val DefaultRequireValidation = RequiredValidation(None, Left(false), None)

    def fromForm(controlName: String)(implicit ctx: FormBuilderDocContext): RequiredValidation =
      findMIPs(controlName, Required).headOption map {
        case (idOpt, _, value, alertOpt) =>
          RequiredValidation(idOpt, xpathOptToEither(Some(value)), alertOpt)
      } getOrElse
        DefaultRequireValidation

    def fromXml(validationElem: NodeInfo, newIds: Iterator[String])(implicit ctx: FormBuilderDocContext): RequiredValidation = {
      require(validationElem /@ "type" === Required.name)

      val validationIdOpt = validationElem.id.trimAllToOpt orElse Some(newIds.next())
      val required        = validationElem / Required.name stringValue

      RequiredValidation(
        validationIdOpt,
        xpathOptToEither(required.trimAllToOpt),
        AlertDetails.fromValidationXML(validationElem, validationIdOpt)
      )
    }

    private def xpathOptToEither(opt: Option[String]): Either[Boolean, String] =
      opt match {
        case Some("true()")         => Left(true)
        case Some("false()") | None => Left(false)    // normalize missing MIP to false()
        case Some(xpath)            => Right(xpath)
      }

    private def eitherToXPath(required: Either[Boolean, String]) =
      required match {
        case Left(true)   => "true()"
        case Left(false)  => "false()"
        case Right(xpath) => xpath
      }
  }

  case class DatatypeValidation(
    idOpt    : Option[String],
    datatype : Either[(QName, Boolean), QName],
    alert    : Option[AlertDetails]
  ) extends Validation {

    val datatypeQName = datatype.fold(_._1, identity)

    def level       = ErrorLevel
    def stringValue = XMLUtils.buildQName(datatypeQName.namespace.prefix, datatypeQName.localName)

    // Rename control element if needed when the datatype changes
    def renameControlIfNeeded(
      controlName      : String,
      newAppearanceOpt : Option[String])(implicit
      ctx              : FormBuilderDocContext
    ): Unit = {
      val newDatatype = datatypeQName
      for {
        controlElem    <- findControlByName(ctx.formDefinitionRootElem, controlName)
        oldDatatype    = DatatypeValidation.fromForm(controlName).datatypeQName
        oldAppearances = controlElem attTokens APPEARANCE_QNAME
        (newElemName, newAppearanceAttOpt) <- BindingDescriptor.newElementName(
          controlElem.uriQualifiedName,
          oldDatatype,
          oldAppearances,
          newDatatype,
          newAppearanceOpt,
          ctx.componentBindings
        )
      } locally {
        // Q: If binding changes, what about instance and bind templates? Should also be updated? Not a
        // concrete case as of now, but can happen depending on which bindings are available.
        val newControlElem = rename(controlElem, newElemName)
        toggleAttribute(newControlElem, APPEARANCE_QNAME, newAppearanceAttOpt)
      }
    }

    def toXML(forLang: String)(implicit ctx: FormBuilderDocContext): sx.Elem = {

      val builtinTypeString = datatype match {
        case Left((name, _)) => name.localName
        case _               => ""
      }

      val builtinTypeRequired = datatype match {
        case Left((_, required)) => required.toString
        case _                   => ""
      }

      <validation type="datatype" id={idOpt.orNull} level={level.entryName} default-alert={alert.isEmpty.toString}>
        <builtin-type>{builtinTypeString}</builtin-type>
        <builtin-type-required>{builtinTypeRequired}</builtin-type-required>
        <schema-type>{datatype.right.toOption map (_.qualifiedName) getOrElse ""}</schema-type>
        {alertOrPlaceholder(alert, forLang)}
      </validation>
    }
  }

  object DatatypeValidation {

    private val DefaultDataTypeValidation =
      DatatypeValidation(None, Left(XMLConstants.XS_STRING_QNAME -> false), None)

    // Create from a control name
    def fromForm(controlName: String)(implicit ctx: FormBuilderDocContext): DatatypeValidation = {

      val inDoc = ctx.formDefinitionRootElem

      val bind = findBindByName(inDoc, controlName).get // require the bind

      def builtinOrSchemaType(typ: String): Either[(QName, Boolean), QName] = {
        val qName         = bind.resolveQName(typ)
        val isBuiltinType = Set(XF, XS)(qName.namespace.uri)

        if (isBuiltinType)
          Left(qName -> (qName.namespace.uri == XS))
        else
          Right(qName)
      }

      findMIPs(controlName, Type).headOption map {
        case (idOpt, _, value, alertOpt) =>
          DatatypeValidation(idOpt, builtinOrSchemaType(value), alertOpt)
      } getOrElse
        DefaultDataTypeValidation
    }

    def fromXml(
      validationElem : NodeInfo,
      newIds         : Iterator[String],
      inDoc          : NodeInfo,
      controlName    : String)(implicit
      ctx            : FormBuilderDocContext
    ): DatatypeValidation = {
      require(validationElem /@ "type" === "datatype")

      val validationIdOpt = validationElem.id.trimAllToOpt orElse Some(newIds.next())

      val datatype = {

        val bind = findBindByName(inDoc, controlName).get

        val builtinTypeStringOpt = (validationElem elemValue "builtin-type").trimAllToOpt
        val builtinTypeRequired  = (validationElem elemValue "builtin-type-required").trimAllToOpt contains "true"
        val schemaTypeOpt        = (validationElem elemValue "schema-type").trimAllToOpt

        def builtinTypeQName: (QName, Boolean) = {

          val builtinTypeString = builtinTypeStringOpt.get

          // If a builtin type, we just have a local name
          val nsURI = Model.uriForBuiltinTypeName(builtinTypeString, builtinTypeRequired)

          // Namespace mapping must be in scope
          val prefix = bind.nonEmptyPrefixesForURI(nsURI).min

          QName(builtinTypeString, Namespace(prefix, nsURI)) -> builtinTypeRequired
        }

        def schemaTypeQName: QName = {

          val schemaType = schemaTypeOpt.get

          // Schema type OTOH comes with a prefix if needed
          val localname = parseQName(schemaType)._2
          val namespace = valueNamespaceMappingScopeIfNeeded(bind, schemaType) map
            { case (prefix, uri) => Namespace(prefix, uri) } getOrElse
            Namespace.EmptyNamespace
          QName(localname, namespace)
        }

        Either.cond(schemaTypeOpt.isDefined, schemaTypeQName, builtinTypeQName)
      }

      DatatypeValidation(
        validationIdOpt,
        datatype,
        AlertDetails.fromValidationXML(validationElem, validationIdOpt)
      )
    }
  }

  case class ConstraintValidation(
    idOpt      : Option[String],
    level      : ValidationLevel,
    expression : String,
    alert      : Option[AlertDetails]
  ) extends Validation {

    def stringValue = expression

    def toXML(forLang: String)(implicit ctx: FormBuilderDocContext): sx.Elem = {

      // NOTE: We use the namespaces in scope on the model, not the bind containing the constraint. This is
      // a simplification and implies a constraint that there are no new namespace declarations on binds
      // compared to the model.
      val analyzed = CommonConstraint.analyzeKnownConstraint(
        expression,
        ctx.formBuilderModel.getOrElse(throw new IllegalStateException).staticModel.namespaceMapping,
        inScopeContainingDocument.getFunctionLibrary
      )

      <validation
        type={analyzed map (_._1) getOrElse "formula"}
        id={idOpt getOrElse ""}
        level={level.entryName}
        default-alert={alert.isEmpty.toString}>
        <constraint
          expression={if (analyzed.isEmpty) expression else ""}
          argument={analyzed flatMap (_._2) getOrElse ""}
        />
        {alertOrPlaceholder(alert, forLang)}
      </validation>
    }
  }

  object ConstraintValidation {

    def fromForm(controlName: String)(implicit ctx: FormBuilderDocContext): List[ConstraintValidation] =
      findMIPs(controlName, Constraint) map {
        case (idOpt, level, value, alertOpt) =>
          ConstraintValidation(idOpt, level, value, alertOpt)
      }

    def fromXmlOpt(validationElem: NodeInfo, newIds: Iterator[String])(implicit ctx: FormBuilderDocContext): Option[ConstraintValidation] = {

      def normalizedAttOpt(attName: String) =
        (validationElem child Constraint.name attValue attName headOption) flatMap trimAllToOpt

      val constraintExpressionOpt = validationElem attValue "type" match {
        case "formula"                                    => normalizedAttOpt("expression")
        case vn @ UploadMediatypesValidation.PropertyName => Some(s"xxf:$vn('${normalizedAttOpt("argument") getOrElse ""}')") // quote
        case vn @ ExcludedDatesValidation.PropertyName    => Some(s"xxf:$vn((${normalizedAttOpt("argument") getOrElse ""}))") // parens
        case vn                                           => Some(s"xxf:$vn(${normalizedAttOpt("argument") getOrElse ""})")   // as is
      }

      constraintExpressionOpt map { expr =>

        val level           = Validation.levelFromXML(validationElem)
        val validationIdOpt = validationElem.id.trimAllToOpt orElse Some(newIds.next())

        ConstraintValidation(
          validationIdOpt,
          level,
          expr,
          AlertDetails.fromValidationXML(validationElem, validationIdOpt)
        )
      }
    }
  }

  case class AlertDetails(forValidationId: Option[String], messages: List[(String, String)], global: Boolean) {

    require(! (global && forValidationId.isDefined))
    require(messages.nonEmpty)

    def default = forValidationId.isEmpty

    // XML representation used by Form Builder
    def toXML(forLang: String): sx.Elem = {
      // The alert contains the message for the main language as an attribute, and the languages for the other
      // languages so we can write them back.
      <alert message={messages.toMap getOrElse (forLang, "")} global={global.toString}>{
        messages collect {
          case (lang, message) if lang != forLang =>
            <message lang={lang} value={message}/>
        }
      }</alert>
    }
  }

  def isGlobalAlertRef(refAtt: String): Boolean =
    refAtt == OldStandardAlertRef

  // - If the attribute matches a non-global alert path, return `Some`, otherwise `None`.
  // - If there is an explicit index such as as `[1]`, then return a nested `Some` index, otherwise `None.
  // - The index, if any, is 0-based.
  def findZeroBasedIndexFromAlertRef(refAtt: String, resourceName: String): Option[Option[Int]] = {

    def normalizeIndex(index: String) =
      Option(index) map (_.toInt - 1)

    refAtt match {
      case OldAlertRefMatcher(_, `resourceName`, index) => Some(normalizeIndex(index))
      case NewAlertRefMatcher(_, `resourceName`, index) => Some(normalizeIndex(index))
      case _                                            => None
    }
  }

  // Same as `findZeroBasedIndexFromAlertRef` but handle case of a blank value which returns `Some(None)`.
  def findZeroBasedIndexFromAlertRefHandleBlankRef(refAtt: String, resourceName: String): Option[Option[Int]] =
    findZeroBasedIndexFromAlertRef(refAtt, resourceName) orElse (refAtt.isAllBlank option None)

  // NOTE: The index is 0-based.
  def buildResourcePointer(controlName: String, lhhaName: String, indexOpt: Option[Int]) =
    s"$$form-resources/$controlName/$lhhaName${indexOpt map (i => s"[${i + 1}]") getOrElse ""}"

  object AlertDetails {

    // Return supported alert details for the control
    //
    // - None if the alert message can't be found or if the alert/validation combination can't be handled by FB
    // - alerts returned are either global (no validation/level specified) or for a single specific validation
    def fromForm(controlName: String)(implicit ctx: FormBuilderDocContext): Seq[AlertDetails] = {

      val inDoc = ctx.formDefinitionRootElem

      val controlElem                = findControlByName(inDoc, controlName).get
      val alertResourcesForAllLangs  = getControlResourcesWithLang(controlName, "alert", allLangs(resourcesRoot))

      def alertFromElement(e: NodeInfo) = {

        def attValueOrNone(name: QName) = e att name map (_.stringValue) headOption

        val validationAtt = attValueOrNone(VALIDATION_QNAME)
        val levelAtt      = attValueOrNone(LEVEL_QNAME)
        val refAttOpt     = attValueOrNone(REF_QNAME)

        val alertIndexOpt = refAttOpt match {
          case Some(refAtt) => findZeroBasedIndexFromAlertRef(refAtt, LHHA.Alert.entryName).flatten orElse Some(0)
          case None         => throw new IllegalArgumentException(s"missing `${REF_QNAME.qualifiedName}` attribute")
        }

        // Try to find an existing resource for the given index if present, otherwise assume a blank value for
        // the language
        val alertsByLang = alertResourcesForAllLangs.to(List) map {
          case (lang, alerts) => lang -> (alertIndexOpt flatMap alerts.lift map (_.stringValue) getOrElse "")
        }

        val forValidations = gatherAlertValidations(validationAtt)
        val forLevels      = gatherAlertLevels(levelAtt)

        // Form Builder only handles a subset of the allowed XForms mappings for now
        def isDefault           = forValidations.isEmpty && forLevels.isEmpty
        def hasSingleValidation = forValidations.size == 1 && forLevels.isEmpty
        def canHandle           = (isDefault || hasSingleValidation) && alertsByLang.nonEmpty

        canHandle option AlertDetails(forValidations.headOption, alertsByLang, refAttOpt exists isGlobalAlertRef)
      }

      controlElem child "alert" flatMap alertFromElement toList
    }

    def fromXML(alertElem: NodeInfo, forValidationId: Option[String])(implicit ctx: FormBuilderDocContext) = {

      val messageAtt = alertElem attValue "message"

      val messagesElems = (alertElem child "message" toList) map {
        message => (message attValue "lang", message attValue "value")
      }

      val isGlobal = (alertElem attValue "global") == "true"

      AlertDetails(forValidationId, (currentLang, messageAtt) :: messagesElems, isGlobal)
    }

    def fromValidationXML(validationElem: NodeInfo, forValidationId: Option[String])(implicit ctx: FormBuilderDocContext) = {

      val useDefaultAlert = validationElem /@ "default-alert" === "true"

      def alertOpt = {
        val alertElem = validationElem child "alert" headOption

        alertElem map (AlertDetails.fromXML(_, forValidationId))
      }

      if (useDefaultAlert) None else alertOpt
    }
  }

  private def findMIPs(controlName: String, mip: MIP)(implicit ctx: FormBuilderDocContext) = {

    val inDoc = ctx.formDefinitionRootElem

    val bind            = findBindByName(inDoc, controlName).get // require the bind
    val supportedAlerts = AlertDetails.fromForm(controlName)

    def findAlertForId(id: String) =
      supportedAlerts find (_.forValidationId.contains(id))

    def fromAttribute(a: NodeInfo) = {
      val bindId = (a parent * head).id
      (
        None, // no id because we don't want that attribute to roundtrip
        ErrorLevel,
        a.stringValue,
        findAlertForId(bindId)
      )
    }

    def fromElement(e: NodeInfo) = {
      val id = e.id
      (
        id.trimAllToOpt,
        (e attValue LEVEL_QNAME).trimAllToOpt map LevelByName getOrElse ErrorLevel,
        if (mip == Type) e.stringValue else e attValue VALUE_QNAME,
        findAlertForId(id)
      )
    }

    // Gather all validations (in fb:* except for type)
    def attributeValidations = mipAtts (bind, mip) map fromAttribute
    def elementValidations   = mipElems(bind, mip) map fromElement

    attributeValidations ++ elementValidations toList
  }

  private def mipAtts (bind: NodeInfo, mip: MIP) = bind /@ mipToFBMIPQNames(mip)._1
  private def mipElems(bind: NodeInfo, mip: MIP) = bind /  mipToFBMIPQNames(mip)._2

  private def alertOrPlaceholder(alert: Option[AlertDetails], forLang: String)(implicit ctx: FormBuilderDocContext) =
    alert orElse Some(AlertDetails(None, List(currentLang -> ""), global = false)) map (_.toXML(forLang)) get
}
