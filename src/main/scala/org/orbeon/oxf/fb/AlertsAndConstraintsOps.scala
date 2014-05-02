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
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis._
import org.dom4j.{Namespace, QName}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.analysis.model.Model._
import scala.xml.Elem
import org.orbeon.oxf.xml.XMLUtils

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
            val newAlertElements = ensureCleanLHHAElements(inDoc, controlName, "alert", count = alertsWithResources.size, replace = true)

            // Insert constraint attribute as needed
            newAlertElements zip alertsWithResources foreach {
                case (e, AlertDetails(Some(forConstraintId), _, _)) ⇒
                    def bindId       = bind \@ "id" stringValue
                    val constraintId = if (hasSingleErrorConstraint) bindId else forConstraintId
                    insert(into = e, origin = attributeInfo(VALIDATION_QNAME, constraintId))
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
        def level: ValidationLevel
        def toXML(forLang: String): Elem
    }

    object Validation {
        def fromXML(validationElem: NodeInfo) =
            LevelByName(validationElem attValue "level")
    }

    // Required is either a simple boolean or a custom XPath expression
    case class RequiredValidation(level: ValidationLevel, required: Either[Boolean, String]) extends Validation {

        import RequiredValidation._

        def write(inDoc: NodeInfo, controlName: String): Unit =
            updateMip(
                inDoc,
                controlName,
                Required.name,
                eitherToXPath(required, keepFalse = false)
            )

        def toXML(forLang: String): Elem =
            <validation type={Required.name} level={level.name}><required>{eitherToXPath(required, keepFalse = true)}</required></validation>
    }

    object RequiredValidation {

        def fromForm(inDoc: NodeInfo, controlName: String): RequiredValidation =
            RequiredValidation(
                ErrorLevel,
                xpathOptToEither(getMip(inDoc, controlName, Required.name))
            )

        def fromXML(validationElem: NodeInfo): Option[RequiredValidation] = {
            require(validationElem \@ "type" === Required.name)

            val level    = Validation.fromXML(validationElem)
            val required = validationElem \ Required.name stringValue

            Some(RequiredValidation(level, xpathOptToEither(nonEmptyOrNone(required))))
        }

        private def xpathOptToEither(opt: Option[String]): Either[Boolean, String] =
            opt match {
                case Some("true()")         ⇒ Left(true)
                case Some("false()") | None ⇒ Left(false)    // normalize missing MIP to false()
                case Some(xpath)            ⇒ Right(xpath)
            }

        private def eitherToXPath(required: Either[Boolean, String], keepFalse: Boolean) =
            required match {
                case Left(true)                  ⇒ "true()"
                case Left(false) if keepFalse    ⇒ "false()"
                case Left(false) if ! keepFalse  ⇒ ""        // empty value causes MIP to be removed
                case Right(xpath)                ⇒ xpath
            }
    }

    // For a builtin type, keep the plain type name and whether the type implied requiredness. For a schema type, keep
    // the type qualified name so we can separate custom types in different namespaces, yet not have to do namespace
    // resolution when creating a DatatypeValidation instance.
    case class DatatypeValidation(level: ValidationLevel, datatype: Either[(String, Boolean), String]) extends Validation {

        def write(inDoc: NodeInfo, controlName: String): Unit = {

            val newDatatype = datatype(inDoc, controlName)

            // Rename control element if needed when the datatype changes
            for {
                control        ← findControlByName(inDoc, controlName)
                oldDatatype    = DatatypeValidation.fromForm(inDoc, controlName).datatype(inDoc, controlName)
                if oldDatatype != newDatatype
                newElementName ← FormBuilder.newElementName(control.qname, oldDatatype, newDatatype, componentBindings)
            } locally {
                // TODO: If binding changes, what about instance and bind templates? Should also be updated? Not a concrete
                // case as of now, but can happen depending on which bindings are available.
                rename(control, newElementName)
            }

            val datatypeString =
                XMLUtils.buildQName(newDatatype.getNamespacePrefix, newDatatype.getName)

            updateMip(inDoc, controlName, Type.name, datatypeString)
        }

        def datatype(inDoc: NodeInfo, controlName: String): QName = {

            // Bind must be present
            val bind = findBindByName(inDoc, controlName).get

            this match {
                case DatatypeValidation(_, Left((builtinType, required))) ⇒
                    // If a builtin type, we just have a local name
                    val nsURI =
                        if (XFormsTypeNames(builtinType) || ! required && XFormsVariationTypeNames(builtinType))
                            XF
                        else
                            XS

                    // Namespace mapping must be in scope
                    val prefix = bind.nonEmptyPrefixesForURI(nsURI).sorted.head

                    new QName(builtinType, new Namespace(prefix, nsURI))
                case DatatypeValidation(_, Right(schemaType))  ⇒
                    // Schema type OTOH comes with a prefix if needed
                    val localname = parseQName(schemaType)._2
                    val namespace = valueNamespaceMappingScopeIfNeeded(bind, schemaType) map
                        { case (prefix, uri) ⇒ new Namespace(prefix, uri) } getOrElse
                        Namespace.NO_NAMESPACE
                    new QName(localname, namespace)
                case _ ⇒
                    // No type specified, must not happen as we require a type at construction
                    throw new IllegalStateException
            }
        }

        def toXML(forLang: String): Elem = {

            val builtinTypeString = datatype match {
                case Left((name, _)) ⇒ name
                case _               ⇒ ""
            }

            val builtinTypeRequired = datatype match {
                case Left((_, required)) ⇒ required.toString
                case _                   ⇒ ""
            }

            <validation type="datatype" level={level.name}>
                <builtin-type>{builtinTypeString}</builtin-type>
                <builtin-type-required>{builtinTypeRequired}</builtin-type-required>
                <schema-type>{datatype.right.getOrElse("")}</schema-type>
            </validation>
        }
    }

    object DatatypeValidation {

        // Create from a control name
        def fromForm(inDoc: NodeInfo, controlName: String): DatatypeValidation = {
            val bind    = findBindByName(inDoc, controlName).get // require the bind
            val typeMIP = getMip(inDoc, controlName, "type")

            val builtinOrSchemaType =
                typeMIP match {
                    case Some(typ) ⇒

                        val qName         = bind.resolveQName(typ)
                        val isBuiltinType = Set(XF, XS)(qName.getNamespaceURI)

                        if (isBuiltinType)
                            Left(qName.getName → (qName.getNamespaceURI == XS))
                        else // FIXME: Handle namespace and prefix for schema type
                            Right(typ)
                    case None ⇒
                        // No specific type means we are a string
                        Left("string" → false)
                }

            DatatypeValidation(ErrorLevel, builtinOrSchemaType)
        }

        def fromXML(validationElem: NodeInfo): Option[DatatypeValidation] = {
            require(validationElem \@ "type" === "datatype")

            val level               = Validation.fromXML(validationElem)
            val builtinTypeString   = nonEmptyOrNone(validationElem \ "builtin-type" stringValue)
            val builtinTypeRequired = nonEmptyOrNone(validationElem \ "builtin-type-required" stringValue) exists (_ == "true")
            val builtinType         = builtinTypeString map (_ → builtinTypeRequired)
            val schemaType          = nonEmptyOrNone(validationElem \ "schema-type"  stringValue)

            val datatype = Either.cond(schemaType.isDefined, schemaType.get, builtinType.get)

            Some(DatatypeValidation(level, datatype))
        }
    }

    case class ConstraintValidation(id: Option[String], level: ValidationLevel, expression: String, alert: Option[AlertDetails]) extends Validation {

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
                val id = e.id
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
                val constraintIdOpt = nonEmptyOrNone(validationElem.id) orElse Some(newIds.next())
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

                val forConstraints = gatherAlertValidations(validationAtt)
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
