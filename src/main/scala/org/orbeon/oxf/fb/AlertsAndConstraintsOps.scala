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

import org.dom4j.{Namespace, QName}
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis._
import org.orbeon.oxf.xforms.analysis.model.Model._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

import scala.{xml ⇒ sx}

trait AlertsAndConstraintsOps extends ControlOps {

    self: GridOps ⇒ // funky dependency, to resolve at some point

    private val XFValidationQName: QName = XF → "validation"

    private val OldAlertRefMatcher = """\$form-resources/([^/]+)/alert(\[(\d+)\])?""".r
    private val NewAlertRefMatcher = """xxf:r\('([^.]+)\.alert(\.(\d+))?'\)""".r

    val OldStandardAlertRef = """$fr-resources/detail/labels/alert"""

    // Return the first default alert for the given control, or a blank template if none exists
    def readDefaultAlertAsXML(inDoc: NodeInfo, controlName: String): NodeInfo = (
        AlertDetails.fromForm(inDoc, controlName)
        find      (_.default)
        getOrElse AlertDetails(None, List(currentLang → ""), global = true)
        toXML     currentLang
    )

    // Return all validations as XML for the given control
    def readValidationsAsXML(inDoc: NodeInfo, controlName: String): Array[NodeInfo] =
        RequiredValidation.fromForm(inDoc, controlName) ::
        DatatypeValidation.fromForm(inDoc, controlName) ::
        ConstraintValidation.fromForm(inDoc, controlName) map
        (v ⇒ elemToNodeInfo(v.toXML(currentLang))) toArray

    // Write back everything
    def writeAlertsAndValidationsAsXML(
        inDoc            : NodeInfo,
        controlName      : String,
        defaultAlertElem : NodeInfo,
        validationElems  : Array[NodeInfo]
    ): Unit = {

        // Current resolutions, which could be lifted in the future:
        //
        // - writes are destructive: they remove all xf:alert, alert resources, and validations for the control
        // - we don't allow editing the validation id, but we preserve it when possible

        val validationElemsSeq = validationElems.to[List]

        // Extract from XML
        val allValidations = {
            val idsIterator = nextIds(inDoc, "validation", validationElemsSeq.size).toIterator
            validationElemsSeq map (v ⇒ v → (v attValue "type")) flatMap {
                case (e, Required.name)   ⇒ RequiredValidation.fromXML(e, idsIterator)
                case (e, "datatype")      ⇒ DatatypeValidation.fromXML(e, idsIterator, inDoc, controlName)
                case (e, Constraint.name) ⇒ ConstraintValidation.fromXML(e, idsIterator)
            }
        }

        val defaultAlert = AlertDetails.fromXML(defaultAlertElem, None)

        // We expect only one "required" validation
        allValidations collectFirst {
            case v: RequiredValidation ⇒ v
        } foreach { v ⇒
            writeValidations(
                inDoc,
                controlName,
                Required.name,
                List(v)
            )
        }

        // We expect only one "datatype" validation
        allValidations collect {
            case v: DatatypeValidation ⇒ v
        } foreach { v ⇒

            v.renameControlIfNeeded(inDoc, controlName)

            writeValidations(
                inDoc,
                controlName,
                Type.name,
                List(v)
            )
        }

        // Several "constraint" validations are supported
        writeValidations(
            inDoc,
            controlName,
            Constraint.name,
            allValidations collect { case v: ConstraintValidation ⇒ v }
        )

        writeAlerts(
            inDoc,
            controlName,
            allValidations,
            defaultAlert
        )
    }

    private def writeValidations(
        inDoc       : NodeInfo,
        controlName : String,
        mipName     : String,
        validations : List[Validation]
    ): Unit = {

        val bind = findBindByName(inDoc, controlName).get

        val mipAttQName = mipNameToFBMIPQname(mipName)

        val existingAttributeValidations = bind /@ mipAttQName
        val existingElementValidations   = bind /  XFValidationQName filter (_ /@ mipAttQName nonEmpty)

        validations match {
            case Nil ⇒
                delete(existingAttributeValidations ++ existingElementValidations)
            case List(Validation(_, ErrorLevel, value, None)) ⇒

                // Single validation without custom alert: set @fb:mipAttName and remove all nested elements
                // See also: https://github.com/orbeon/orbeon-forms/issues/1829
                // NOTE: We could optimize further by taking this branch if there is no type or required validation.
                updateMip(inDoc, controlName, mipName, value)
                delete(existingElementValidations)
            case _ ⇒
                val nestedValidations =
                    validations flatMap { case Validation(idOpt, level, value, _) ⇒

                        nonEmptyOrNone(value) match {
                            case Some(nonEmptyValue) ⇒

                                val validationElem =
                                    <xf:validation
                                        id={idOpt.orNull}
                                        level={if (level != ErrorLevel) level.name else null}
                                        xmlns:xf={XF}
                                        xmlns:fb={FB}/>

                                val mipAtt =
                                    sx.Attribute(
                                        mipAttQName.getNamespacePrefix,
                                        mipAttQName.getName,
                                        sx.Text(nonEmptyValue),
                                        sx.Null
                                    )

                                List(validationElem % mipAtt: NodeInfo)
                            case None ⇒
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
        inDoc        : NodeInfo,
        controlName  : String,
        validations  : List[Validation],
        defaultAlert : AlertDetails
    ): Unit = {

        val alertsWithResources = {

            val alertsForValidations =
                validations collect
                    { case Validation(_, _, _, Some(alert)) ⇒ alert }

            val nonGlobalDefaultAlert =
                ! defaultAlert.global list defaultAlert

            alertsForValidations ::: nonGlobalDefaultAlert
        }

        val messagesByLangForAllLangs = {

            def messagesForAllLangs(a: AlertDetails) = {
                val messagesMap = a.messages.toMap
                allLangs(resourcesRoot) map { lang ⇒ lang → messagesMap.getOrElse(lang, "") }
            }

            val messagesByLang = (
                alertsWithResources
                flatMap messagesForAllLangs
                groupBy (_._1)
                map     { case (lang, values) ⇒ lang → (values map (_._2)) }
            )

            // Make sure we have a default for all languages if there are no alerts or if some languages are missing
            // from the alerts. We do want to update all languages on write, including removing unneeded <alert>
            // elements.
            val defaultMessages = allLangs(resourcesRoot) map (_ → Nil)

            defaultMessages.toMap ++ messagesByLang toList
        }

        setControlResourcesWithLang(controlName, "alert", messagesByLangForAllLangs)

        // Write alerts
        val newAlertElements =
            ensureCleanLHHAElements(
                inDoc       = inDoc,
                controlName = controlName,
                lhha        = "alert",
                count       = alertsWithResources.size,
                replace     = true
            )

        // Insert validation attribute as needed
        newAlertElements zip alertsWithResources foreach {
            case (e, AlertDetails(Some(forValidationId), _, _)) ⇒
                insert(into = e, origin = attributeInfo(VALIDATION_QNAME, forValidationId))
            case _ ⇒ // no attributes to insert if this is not an alert linked to a validation
        }

        // Write global default alert if needed
        if (defaultAlert.global) {
            val newGlobalAlert = ensureCleanLHHAElements(inDoc, controlName, "alert", count = 1, replace = false).head
            setvalue(newGlobalAlert /@ "ref", OldStandardAlertRef)
        }
    }

    sealed trait Validation {
        def idOpt       : Option[String]
        def level       : ValidationLevel
        def stringValue : String
        def alert       : Option[AlertDetails]

        def toXML(forLang: String): sx.Elem
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

        def toXML(forLang: String): sx.Elem =
            <validation type={Required.name} level={level.name} default-alert={alert.isEmpty.toString}>
                <required>{eitherToXPath(required)}</required>
                {alertOrPlaceholder(alert, forLang)}
            </validation>
    }

    object RequiredValidation {

        val DefaultRequireValidation = RequiredValidation(None, Left(false), None)

        def fromForm(inDoc: NodeInfo, controlName: String): RequiredValidation = {
            val mipAttQName = mipNameToFBMIPQname(Required.name)

            findMIPs(inDoc, controlName, mipAttQName).headOption map {
                case (idOpt, _, value, alertOpt) ⇒
                    RequiredValidation(idOpt, xpathOptToEither(Some(value)), alertOpt)
            } getOrElse
                DefaultRequireValidation
        }

        def fromXML(validationElem: NodeInfo, newIds: Iterator[String]): Option[RequiredValidation] = {
            require(validationElem /@ "type" === Required.name)

            val validationIdOpt = nonEmptyOrNone(validationElem.id) orElse Some(newIds.next())
            val required        = validationElem / Required.name stringValue

            Some(
                RequiredValidation(
                    validationIdOpt,
                    xpathOptToEither(nonEmptyOrNone(required)),
                    AlertDetails.fromValidationXML(validationElem, validationIdOpt)
                )
            )
        }

        private def xpathOptToEither(opt: Option[String]): Either[Boolean, String] =
            opt match {
                case Some("true()")         ⇒ Left(true)
                case Some("false()") | None ⇒ Left(false)    // normalize missing MIP to false()
                case Some(xpath)            ⇒ Right(xpath)
            }

        private def eitherToXPath(required: Either[Boolean, String]) =
            required match {
                case Left(true)   ⇒ "true()"
                case Left(false)  ⇒ "false()"
                case Right(xpath) ⇒ xpath
            }
    }

    case class DatatypeValidation(
        idOpt    : Option[String],
        datatype : Either[(QName, Boolean), QName],
        alert    : Option[AlertDetails]
    ) extends Validation {

        val datatypeQName = datatype.fold(_._1, identity)

        def level       = ErrorLevel
        def stringValue = XMLUtils.buildQName(datatypeQName.getNamespacePrefix, datatypeQName.getName)

        // Rename control element if needed when the datatype changes
        def renameControlIfNeeded(inDoc: NodeInfo, controlName: String): Unit = {
            val newDatatype = datatypeQName
            for {
                control        ← findControlByName(inDoc, controlName)
                oldDatatype    = DatatypeValidation.fromForm(inDoc, controlName).datatypeQName
                if oldDatatype != newDatatype
                newElementName ← FormBuilder.newElementName(control.uriQualifiedName, newDatatype, componentBindings)
            } locally {
                // TODO: If binding changes, what about instance and bind templates? Should also be updated? Not a concrete
                // case as of now, but can happen depending on which bindings are available.
                rename(control, newElementName)
            }
        }

        def toXML(forLang: String): sx.Elem = {

            val builtinTypeString = datatype match {
                case Left((name, _)) ⇒ name.getName
                case _               ⇒ ""
            }

            val builtinTypeRequired = datatype match {
                case Left((_, required)) ⇒ required.toString
                case _                   ⇒ ""
            }

            <validation type="datatype" id={idOpt.orNull} level={level.name} default-alert={alert.isEmpty.toString}>
                <builtin-type>{builtinTypeString}</builtin-type>
                <builtin-type-required>{builtinTypeRequired}</builtin-type-required>
                <schema-type>{datatype.right.toOption map (_.getQualifiedName) getOrElse ""}</schema-type>
                {alertOrPlaceholder(alert, forLang)}
            </validation>
        }
    }

    object DatatypeValidation {

        private val DefaultDataTypeValidation =
            DatatypeValidation(None, Left(new QName("string") → false), None)

        // Create from a control name
        def fromForm(inDoc: NodeInfo, controlName: String): DatatypeValidation = {

            val bind = findBindByName(inDoc, controlName).get // require the bind

            def builtinOrSchemaType(typ: String): Either[(QName, Boolean), QName] = {
                val qName         = bind.resolveQName(typ)
                val isBuiltinType = Set(XF, XS)(qName.getNamespaceURI)

                if (isBuiltinType)
                    Left(qName → (qName.getNamespaceURI == XS))
                else
                    Right(qName)
            }
            
            val mipAttQName = mipNameToFBMIPQname(Type.name)

            findMIPs(inDoc, controlName, mipAttQName).headOption map {
                case (idOpt, _, value, alertOpt) ⇒
                    DatatypeValidation(idOpt, builtinOrSchemaType(value), alertOpt)
            } getOrElse
                DefaultDataTypeValidation
        }

        def fromXML(
            validationElem : NodeInfo,
            newIds         : Iterator[String],
            inDoc          : NodeInfo,
            controlName    : String
        ): Option[DatatypeValidation] = {
            require(validationElem /@ "type" === "datatype")

            val validationIdOpt = nonEmptyOrNone(validationElem.id) orElse Some(newIds.next())

            val datatype = {

                val bind = findBindByName(inDoc, controlName).get

                val builtinTypeStringOpt = nonEmptyOrNone(validationElem / "builtin-type" stringValue)
                val builtinTypeRequired  = nonEmptyOrNone(validationElem / "builtin-type-required" stringValue) exists (_ == "true")
                val schemaTypeOpt        = nonEmptyOrNone(validationElem / "schema-type"  stringValue)

                def builtinTypeQName: (QName, Boolean) = {

                    val builtinTypeString = builtinTypeStringOpt.get

                    // If a builtin type, we just have a local name
                    val nsURI =
                        if (XFormsTypeNames(builtinTypeString) || ! builtinTypeRequired && XFormsVariationTypeNames(builtinTypeString))
                            XF
                        else
                            XS

                    // Namespace mapping must be in scope
                    val prefix = bind.nonEmptyPrefixesForURI(nsURI).sorted.head

                    new QName(builtinTypeString, new Namespace(prefix, nsURI)) → builtinTypeRequired
                }

                def schemaTypeQName: QName = {

                    val schemaType = schemaTypeOpt.get

                    // Schema type OTOH comes with a prefix if needed
                    val localname = parseQName(schemaType)._2
                    val namespace = valueNamespaceMappingScopeIfNeeded(bind, schemaType) map
                        { case (prefix, uri) ⇒ new Namespace(prefix, uri) } getOrElse
                        Namespace.NO_NAMESPACE
                    new QName(localname, namespace)
                }

                Either.cond(schemaTypeOpt.isDefined, schemaTypeQName, builtinTypeQName)
            }

            Some(
                DatatypeValidation(
                    validationIdOpt,
                    datatype,
                    AlertDetails.fromValidationXML(validationElem, validationIdOpt)
                )
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

        def toXML(forLang: String): sx.Elem =
            <validation
                type="constraint"
                id={idOpt getOrElse ""}
                level={level.name}
                default-alert={alert.isEmpty.toString}><constraint expression={expression}/>{
                alertOrPlaceholder(alert, forLang)
            }</validation>
    }

    object ConstraintValidation {

        def fromForm(inDoc: NodeInfo, controlName: String): List[ConstraintValidation] =
            findMIPs(inDoc, controlName, mipNameToFBMIPQname(Constraint.name)) map {
                case (idOpt, level, value, alertOpt) ⇒
                    ConstraintValidation(idOpt, level, value, alertOpt)
            }

        def fromXML(validationElem: NodeInfo, newIds: Iterator[String]) = {
            require(validationElem /@ "type" === Constraint.name)

            val constraintExpressionOpt = (validationElem child Constraint.name attValue "expression" headOption) flatMap nonEmptyOrNone

            constraintExpressionOpt map { expression ⇒

                val level           = Validation.levelFromXML(validationElem)
                val validationIdOpt = nonEmptyOrNone(validationElem.id) orElse Some(newIds.next())

                ConstraintValidation(
                    validationIdOpt,
                    level,
                    expression,
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
                    case (lang, message) if lang != forLang ⇒
                        <message lang={lang} value={message}/>
                }
            }</alert>
        }
    }

    object AlertDetails {

        // Return supported alert details for the control
        //
        // - return None if the alert message can't be found or if the alert/validation combination can't be handled by FB
        // - alerts returned are either global (no validation/level specified) or for a single specific validation
        def fromForm(inDoc: NodeInfo, controlName: String): Seq[AlertDetails] = {

            val control                    = findControlByName(inDoc, controlName).get
            val alertResourcesForAllLangs  = getControlResourcesWithLang(controlName, "alert")

            def alertFromElement(e: NodeInfo) = {

                def attValueOrNone(name: QName) = e att name map (_.stringValue) headOption

                val validationAtt = attValueOrNone(VALIDATION_QNAME)
                val levelAtt      = attValueOrNone(LEVEL_QNAME)
                val refAtt        = attValueOrNone(REF_QNAME)

                val isGlobal = refAtt exists (_ == OldStandardAlertRef)

                // Try to find the alert index from xf:alert/@ref
                val alertIndexOpt =
                    if (isGlobal)
                        None
                    else
                        refAtt collect {
                            case OldAlertRefMatcher(`controlName`, _, index) ⇒ Option(index)
                            case NewAlertRefMatcher(`controlName`, _, index) ⇒ Option(index)
                        } map {
                            _  map (_.toInt - 1) getOrElse 0
                        }

                // Try to find an existing resource for the given index if present, otherwise assume a blank value for the language
                val alertsByLang = alertResourcesForAllLangs.to[List] map {
                    case (lang, alerts) ⇒ lang → (alertIndexOpt flatMap alerts.lift map (_.stringValue) getOrElse "")
                }

                val forValidations = gatherAlertValidations(validationAtt)
                val forLevels      = gatherAlertLevels(levelAtt)

                // Form Builder only handles a subset of the allowed XForms mappings for now
                def isDefault           = forValidations.isEmpty && forLevels.isEmpty
                def hasSingleValidation = forValidations.size == 1 && forLevels.isEmpty
                def canHandle           = isDefault || hasSingleValidation

                canHandle option AlertDetails(forValidations.headOption, alertsByLang, isGlobal)
            }

            control child "alert" flatMap alertFromElement toList
        }

        def fromXML(alertElem: NodeInfo, forValidationId: Option[String]) = {

            val messageAtt = alertElem attValue "message"

            val messagesElems = (alertElem child "message" toList) map {
                message ⇒ (message attValue "lang", message attValue "value")
            }

            val isGlobal = (alertElem attValue "global") == "true"

            AlertDetails(forValidationId, (currentLang, messageAtt) :: messagesElems, isGlobal)
        }
        
        def fromValidationXML(validationElem: NodeInfo, forValidationId: Option[String]) = {
            
            val useDefaultAlert = validationElem /@ "default-alert" === "true"
            
            def alertOpt = {
                val alertElem = validationElem child "alert" headOption
                
                alertElem map (AlertDetails.fromXML(_, forValidationId))
            }
            
            if (useDefaultAlert) None else alertOpt
        }
    }

    private def findMIPs(inDoc: NodeInfo, controlName: String, attQName: QName) = {

        val bind            = findBindByName(inDoc, controlName).get // require the bind
        val supportedAlerts = AlertDetails.fromForm(inDoc, controlName)

        def findAlertForId(id: String) =
            supportedAlerts find (_.forValidationId == Some(id))

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
                nonEmptyOrNone(id),
                nonEmptyOrNone(e attValue LEVEL_QNAME) map LevelByName getOrElse ErrorLevel,
                e attValue attQName,
                findAlertForId(id)
            )
        }

        // Gather all validations (in fb:*)
        def attributeValidations = bind /@ attQName map fromAttribute
        def elementValidations   = bind /  XFValidationQName filter (_ /@ attQName nonEmpty) map fromElement

        attributeValidations ++ elementValidations toList
    }

    private def alertOrPlaceholder(alert: Option[AlertDetails], forLang: String) =
        alert orElse Some(AlertDetails(None, List(currentLang → ""), global = false)) map (_.toXML(forLang)) get
}
