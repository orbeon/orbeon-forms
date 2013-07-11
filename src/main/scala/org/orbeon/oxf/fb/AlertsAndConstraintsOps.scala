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

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.xforms.analysis.model.StaticBind._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis._
import org.dom4j.QName
import org.orbeon.oxf.xforms.action.XFormsAPI.{insert, delete, setvalue}
import org.orbeon.oxf.xforms.analysis.model.Model._
import scala.xml.Elem

trait AlertsAndConstraintsOps extends ControlOps {

    self: GridOps ⇒ // funky dependency, to resolve at some point

    private val FBConstraintQName: QName = FB → Constraint.name

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
    def writeAlertsAndValidationsAsXML(inDoc: NodeInfo, controlName: String, defaultAlertElem: NodeInfo, validationElems: Array[NodeInfo]) = {

        // Current resolutions, which could be lifted in the future:
        //
        // - writes are destructive: they remove all xf:alert, alert resources, and constraints for the control
        // - we don't allow editing the constraint id, but we preserve it when possible

        val validationElemsSeq = validationElems.to[List]

        // Extract from XML
        val allValidations = {
            val idsIterator = nextIds(inDoc, Constraint.name, validationElemsSeq.size).toIterator
            validationElemsSeq map (v ⇒ v → (v attValue "type")) flatMap {
                case (e, Required.name)   ⇒ RequiredValidation.fromXML(e)
                case (e, "datatype")      ⇒ DatatypeValidation.fromXML(e)
                case (e, Constraint.name) ⇒ ConstraintValidation.fromXML(e, idsIterator)
            }
        }

        val defaultAlert = AlertDetails.fromXML(defaultAlertElem, None)

        // Write type="required" and type="datatype"
        allValidations collectFirst { case v: RequiredValidation ⇒ v } foreach (_.write(inDoc, controlName))
        allValidations collectFirst { case v: DatatypeValidation ⇒ v } foreach (_.write(inDoc, controlName))

        // Write type="constraint"
        writeConstraintValidations(inDoc, controlName, defaultAlert, allValidations collect { case v: ConstraintValidation ⇒ v })
    }

    private def writeConstraintValidations(inDoc: NodeInfo, controlName: String, defaultAlert: AlertDetails, validations: List[ConstraintValidation]): Unit = {

        val bind = findBindByName(inDoc, controlName).toList
        val existingAttributeConstraints = bind \@ FBConstraintQName
        val existingElementConstraints   = bind \  FBConstraintQName

        // Write constraints
        val hasSingleErrorConstraint =
            validations match {
                case List() ⇒
                    delete(existingAttributeConstraints ++ existingElementConstraints)
                    false
                case List(ConstraintValidation(_, ErrorLevel, expression, _)) ⇒
                    // Single error constraint, set @fb:constraint and remove all nested elements
                    updateMip(inDoc, controlName, Constraint.name, expression)
                    delete(existingElementConstraints)
                    true
                case _ ⇒
                    // More than one constraint or not an error constraint, create nested fb:constraint and remove attribute
                    val nestedConstraints =
                        validations map { constraint ⇒
                            <fb:constraint id={constraint.id.get} value={constraint.expression} level={constraint.level.name} xmlns:fb={FB}/>: NodeInfo
                        }

                    delete(existingAttributeConstraints ++ existingElementConstraints)
                    insertElementsImposeOrder(into = bind, origin = nestedConstraints, AllMIPNamesInOrder)
                    false
            }

        // Write resources and alerts for those that have resources
        // If the default alert has resources, write it as well
        locally {

            val alertsWithResources = {

                val alertsForConstraints =
                    validations collect
                        { case ConstraintValidation(_, _, _, Some(alert)) ⇒ alert }

                val nonGlobalDefaultAlert =
                    ! defaultAlert.global list defaultAlert

                alertsForConstraints ::: nonGlobalDefaultAlert
            }

            val messagesByLangForAllLangs = {

                def messagesForAllLangs(a: AlertDetails) = {
                    val messagesMap = a.messages.toMap
                    allLangs map { lang ⇒ lang → messagesMap.getOrElse(lang, "") }
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
                val defaultMessages = allLangs map (_ → Nil)

                defaultMessages.toMap ++ messagesByLang toList
            }

            setControlResourcesWithLang(controlName, "alert", messagesByLangForAllLangs)

            // Write alerts
            val newAlertElements = ensureCleanLHHAElements(inDoc, controlName, "alert", count = alertsWithResources.size, replace = true)

            // Insert constraint attribute as needed
            newAlertElements zip alertsWithResources foreach {
                case (e, AlertDetails(Some(forConstraintId), _, _)) ⇒
                    def bindId       = bind \@ "id" stringValue
                    val constraintId = if (hasSingleErrorConstraint) bindId else forConstraintId
                    insert(into = e, origin = attributeInfo(Constraint.name, constraintId))
                case _ ⇒ // no attributes to insert if this is not an alert linked to a constraint
            }
        }

        // Write global default alert if needed
        if (defaultAlert.global) {
            val newGlobalAlert = ensureCleanLHHAElements(inDoc, controlName, "alert", count = 1, replace = false).head
            setvalue(newGlobalAlert \@ "ref", OldStandardAlertRef)
        }
    }

    sealed trait Validation {
        def level: ConstraintLevel
        def toXML(forLang: String): Elem
    }

    object Validation {
        def fromXML(validationElem: NodeInfo) =
            LevelByName(validationElem attValue "level")
    }

    case class RequiredValidation(level: ConstraintLevel, required: Boolean) extends Validation {

        def write(inDoc: NodeInfo, controlName: String): Unit =
            updateMip(inDoc, controlName, "required", if (required) "true()" else "")

        def toXML(forLang: String): Elem =
            <validation type={Required.name} level={level.name}><required>{required.toString}</required></validation>
    }

    object RequiredValidation {
        def fromForm(inDoc: NodeInfo, controlName: String): RequiredValidation = {
            // FB only handles true() and false() at this time and other values are overwritten
            val required = getMip(inDoc, controlName, Required.name) exists (_ == "true()")
            // NOTE: Set a blank id because we don't want that attribute to roundtrip
            RequiredValidation(ErrorLevel, required)
        }

        def fromXML(validationElem: NodeInfo): Option[RequiredValidation] = {
            require(validationElem \@ "type" === Required.name)

            val level    = Validation.fromXML(validationElem)
            val required = validationElem \ Required.name === "true"
            Some(RequiredValidation(level, required))
        }
    }

    case class DatatypeValidation(level: ConstraintLevel, builtinType: Option[String], schemaType: Option[String], required: Boolean) extends Validation {

        def write(inDoc: NodeInfo, controlName: String): Unit = {
            val datatype =
                this match {
                    case DatatypeValidation(_, Some(builtinType), _, required) ⇒
                        // If a builtin type, we just have a local name
                        val nsURI =
                            if (XFormsTypeNames(builtinType) || ! required && XFormsVariationTypeNames(builtinType))
                                XF
                            else
                                XS

                        val bind   = findBindByName(inDoc, controlName).get // require the bind
                        val ns     = bind.namespaceMappings
                        val prefix = ns collectFirst { case (prefix, `nsURI`) ⇒ prefix } get // mapping must be in scope

                        prefix + ':' + builtinType
                    case DatatypeValidation(_, _, Some(schemaType), _)  ⇒
                        // Schema type OTOH comes with a prefix if needed
                        schemaType
                    case _ ⇒
                        // No type specified, should not happen, but if it does we remove the type MIP
                        ""
                }

            updateMip(inDoc, controlName, "type", datatype)
        }

        def toXML(forLang: String): Elem =
            <validation type="datatype" level={level.name}>
                <builtin-type>{builtinType.getOrElse("")}</builtin-type>
                <schema-type>{schemaType.getOrElse("")}</schema-type>
                <required>{required.toString}</required>
            </validation>
    }

    object DatatypeValidation {

        // Create from a control name
        def fromForm(inDoc: NodeInfo, controlName: String): DatatypeValidation = {
            val bind    = findBindByName(inDoc, controlName).get // require the bind
            val typeMIP = getMip(inDoc, controlName, "type")

            val (builtinType, schemaType, required) =
                typeMIP match {
                    case Some(typ) ⇒

                        val qName = resolveQName(bind, typ)

                        val isBuiltinType   = Set(XF, XS)(qName.getNamespaceURI)
                        val isRequired      = qName.getNamespaceURI == XS

                        if (isBuiltinType)
                            (Some(qName.getName), None, isRequired)
                        else // FIXME: Handle namespace and prefix for schema type
                            (None, Some(typ), isRequired)
                    case None ⇒
                        // No specific type means we are a string
                        (Some("string"), None, false)
                }

            DatatypeValidation(ErrorLevel, builtinType, schemaType, required)
        }

        def fromXML(validationElem: NodeInfo): Option[DatatypeValidation] = {
            require(validationElem \@ "type" === "datatype")

            val level       = Validation.fromXML(validationElem)
            val builtinType = nonEmptyOrNone(validationElem \ "builtin-type" stringValue)
            val schemaType  = nonEmptyOrNone(validationElem \ "schema-type"  stringValue)
            val required    = validationElem \ Required.name === "true"

            Some(DatatypeValidation(level, builtinType, schemaType, required))
        }
    }

    case class ConstraintValidation(id: Option[String], level: ConstraintLevel, expression: String, alert: Option[AlertDetails]) extends Validation {

        private def alertOrPlaceholder(forLang: String) =
            alert orElse Some(AlertDetails(None, List(currentLang → ""), global = false)) map (_.toXML(forLang)) get

        def toXML(forLang: String): Elem =
            <validation type="constraint" id={id getOrElse ""} level={level.name} default-alert={alert.isEmpty.toString}><constraint expression={expression}/>{
                alertOrPlaceholder(forLang)
            }</validation>
    }

    object ConstraintValidation {

        def fromForm(inDoc: NodeInfo, controlName: String): List[ConstraintValidation] = {

            val supportedAlerts = AlertDetails.fromForm(inDoc, controlName)

            val bind = findBindByName(inDoc, controlName).toList

            def findAlertForId(id: String) =
                supportedAlerts find (_.forConstraintId == Some(id))

            def constraintFromAttribute(a: NodeInfo) = {
                val bindId = (a parent * att "id").stringValue
                // NOTE: No id because we don't want that attribute to roundtrip
                ConstraintValidation(None, ErrorLevel, a.stringValue, findAlertForId(bindId))
            }

            def constraintFromElement(e: NodeInfo) = {
                val id = e attValue "id"
                ConstraintValidation(nonEmptyOrNone(id), nonEmptyOrNone(e attValue LEVEL_QNAME) map LevelByName getOrElse ErrorLevel, e att "value", findAlertForId(id))
            }

            // Gather all constraints (in fb:*)
            def attributeConstraints = bind \@ FBConstraintQName map constraintFromAttribute
            def elementConstraints   = bind \  FBConstraintQName map constraintFromElement

            attributeConstraints ++ elementConstraints toList
        }

        def fromXML(validationElem: NodeInfo, newIds: Iterator[String]) = {
            require(validationElem \@ "type" === Constraint.name)

            val constraintExpressionOpt = (validationElem child Constraint.name attValue "expression" headOption) flatMap nonEmptyOrNone

            constraintExpressionOpt map { expression ⇒

                val level           = Validation.fromXML(validationElem)
                val constraintIdOpt = nonEmptyOrNone(validationElem attValue "id") orElse Some(newIds.next())
                val useDefaultAlert = validationElem \@ "default-alert" === "true"

                def alertOpt = {
                    val alertElem = validationElem child "alert" headOption

                    alertElem map (AlertDetails.fromXML(_, constraintIdOpt))
                }

                ConstraintValidation(constraintIdOpt, level, expression, if (useDefaultAlert) None else alertOpt)
            }
        }
    }

    case class AlertDetails(forConstraintId: Option[String], messages: List[(String, String)], global: Boolean) {

        require(! (global && forConstraintId.isDefined))
        require(messages.nonEmpty)

        def default = forConstraintId.isEmpty

        // XML representation used by Form Builder
        def toXML(forLang: String): Elem = {
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
        // - return None if the alert message can't be found or if the alert/constraint combination cannot be handled by FB
        // - alerts returned are either global (no constraint/level specified) or for a single specific constraint
        def fromForm(inDoc: NodeInfo, controlName: String): Seq[AlertDetails] = {

            val control                    = findControlByName(inDoc, controlName).get
            val alertResourcesForAllLangs  = getControlResourcesWithLang(controlName, "alert")

            def alertFromElement(e: NodeInfo) = {

                def attValueOrNone(name: QName) = e att name map (_.stringValue) headOption

                val constraintAtt = attValueOrNone(CONSTRAINT_QNAME)
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

                val forConstraints = gatherAlertConstraints(constraintAtt)
                val forLevels      = gatherAlertLevels(levelAtt)

                // Form Builder only handles a subset of the allowed XForms mappings for now
                def isDefault           = forConstraints.isEmpty && forLevels.isEmpty
                def hasSingleConstraint = forConstraints.size == 1 && forLevels.isEmpty
                def canHandle           = isDefault || hasSingleConstraint

                canHandle option AlertDetails(forConstraints.headOption, alertsByLang, isGlobal)
            }

            control child "alert" flatMap alertFromElement toList
        }

        def fromXML(alertElem: NodeInfo, forConstraintId: Option[String]) = {

            val messageAtt = alertElem attValue "message"

            val messagesElems = (alertElem child "message" toList) map {
                message ⇒ (message attValue "lang", message attValue "value")
            }

            val isGlobal = (alertElem attValue "global") == "true"

            AlertDetails(forConstraintId, (currentLang, messageAtt) :: messagesElems, isGlobal)
        }
    }
}
