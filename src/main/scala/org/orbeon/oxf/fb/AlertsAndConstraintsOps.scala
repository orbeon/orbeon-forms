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

trait AlertsAndConstraintsOps extends ControlOps {

    self: GridOps ⇒ // funky dependency, to resolve at some point

    private val FBConstraintQName: QName = FB → "constraint"

    private val OldAlertRefMatcher = """\$form-resources/([^/]+)/alert(\[(\d+)\])?""".r
    private val NewAlertRefMatcher = """xxf:r\('([^.]+)\.alert(\.(\d+))?'\)""".r

    val OldStandardAlertRef = """$fr-resources/detail/labels/alert"""

    def readAlertsAndConstraints(inDoc: NodeInfo, controlName: String): List[AlertDetails] = {

        val control                    = findControlByName(inDoc, controlName).get
        val bind                       = findBindByName(inDoc, controlName).toList
        val alertResourcesForAllLangs  = getControlResourcesWithLang(controlName, "alert")

        // NOTE: There is some duplication of extraction logic here with StaticBind: StaticBind works on dom4j, and here
        // we work on NodeInfo.
        def constraintFromAttribute(a: NodeInfo) =
            ConstraintDetails(a parent * att "id", a.stringValue, ErrorLevel)

        def constraintFromElement(e: NodeInfo) =
            ConstraintDetails(e att "id", e att "value", nonEmptyOrNone(e attValue LEVEL_QNAME) map LevelByName getOrElse ErrorLevel)

        // Gather all constraints (in fb:*)
        val attributeConstraints = bind \@ FBConstraintQName map constraintFromAttribute
        val elementConstraints   = bind \  FBConstraintQName map constraintFromElement

        // Gather all alerts and join with constraints
        val allConstraints = attributeConstraints ++ elementConstraints

        // Return alert details for the element when possible
        // Return None if the alert message can't be found or if the alert/constraint combination cannot be handled by FB
        def alertFromElement(e: NodeInfo, constraints: Seq[ConstraintDetails]): Option[AlertDetails] = {

            def attValueOrNone(name: QName) = e att name map (_.stringValue) headOption

            val constraintAtt = attValueOrNone(CONSTRAINT_QNAME)
            val levelAtt      = attValueOrNone(LEVEL_QNAME)
            val refAtt        = attValueOrNone(REF_QNAME)

            // Try to find the alert index from xf:alert/@ref
            def alertIndexOpt =
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

            val constraintIds  = constraints.map(_.id).to[Set]

            // Form Builder only handles a subset of the allowed XForms mappings for now
            def isGlobal         = forConstraints.isEmpty && forLevels.isEmpty
            def specifiesOne     = forConstraints.size == 1 && forLevels.isEmpty || forConstraints.isEmpty && forLevels.size == 1
            def constraintsExist = forConstraints forall constraintIds // NOTE: true if forConstraints.isEmpty

            def canHandle        = (isGlobal || specifiesOne) && constraintsExist

            def forConstraint    = forConstraints.headOption flatMap (id ⇒ constraints find (_.id == id))

            canHandle option AlertDetails(forConstraint, forLevels.headOption, alertsByLang)
        }

        control child "alert" flatMap (alertFromElement(_, allConstraints)) toList
    }

    def readAlertsAndConstraintsAsXML(inDoc: NodeInfo, controlName: String): Array[NodeInfo] =
        readAlertsAndConstraints(inDoc, controlName) map (_.toXML(currentLang)) toArray

    def writeAlertsAndConstraints(inDoc: NodeInfo, controlName: String, allAlertNodes: Array[NodeInfo]): Unit = {

        // Current resolutions, which could be lifted in the future:
        //
        // - writes are destructive: they remove all xf:alert, alert resources, and constraints for the control
        // - we don't allow editing the constraint id

        // Reserve enough constraint ids in advance
        val idsIterator = nextIds(inDoc, Constraint.name, allAlertNodes.size) toIterator

        val allAlerts = allAlertNodes.to[List] map (AlertDetails.fromXML(_, idsIterator))

        // Write all resources

        def messagesForAllLangs(a: AlertDetails) = {
            val messagesMap = a.messages.toMap
            allLangs map { lang ⇒ lang → messagesMap.getOrElse(lang, "") }
        }

        val messagesByLang = (
            allAlerts
            flatMap messagesForAllLangs
            groupBy (_._1)
            map     { case (lang, values) ⇒ lang → (values map (_._2)) }
            toList
        )

        setControlResourcesWithLang(controlName, "alert", messagesByLang)

        // Write alerts
        val alertElements = ensureCleanLHHAElements(inDoc, controlName, "alert", allAlerts.size)

        // Point to the default alert if there is a single alert blank in all languages
        if (allAlerts.size == 1 && hasBlankOrMissingLHHAForAllLangsUseDoc(inDoc, controlName, "alert"))
            alertElements foreach (alert ⇒ setvalue(alert \@ "ref", OldStandardAlertRef))

        // Write constraints
        val bind = findBindByName(inDoc, controlName).toList
        val attributeConstraints = bind \@ FBConstraintQName
        val elementConstraints   = bind \  FBConstraintQName

        val allConstraints = allAlerts flatMap (_.forConstraint)

        val hasSingleErrorConstraint =
            allConstraints match {
                case List(ConstraintDetails(_, expression, ErrorLevel)) ⇒
                    // Single error constraint, set @fb:constraint and remove all nested elements
                    updateMip(inDoc, controlName, Constraint.name, expression)
                    delete(elementConstraints)
                    true
                case List() ⇒
                    false
                case _ ⇒
                    // More than one constraint or not an error constraint, create nested fb:constraint and remove attribute
                    val nestedConstraints =
                        allConstraints map { constraint ⇒
                            <fb:constraint id={constraint.id} value={constraint.expression} level={constraint.level.name} xmlns:fb={FB}/>: NodeInfo
                        }

                    delete(attributeConstraints ++ elementConstraints)
                    insertElementsImposeOrder(into = bind, origin = nestedConstraints, AllMIPNamesInOrder)
                    false
            }

        // Insert level or constraint attribute as needed
        alertElements zip allAlerts foreach {
            case (e, AlertDetails(Some(constraint), _, _)) ⇒
                val constraintId = if (hasSingleErrorConstraint) bind \@ "id" stringValue else constraint.id
                insert(into = e, origin = attributeInfo("constraint", constraintId))
            case (e, AlertDetails(_, Some(level), _)) ⇒
                insert(into = e, origin = attributeInfo("level", level.name))
            case _ ⇒ // no attributes to insert
        }
    }

    case class ConstraintDetails(id: String, expression: String, level: ConstraintLevel)

    case class AlertDetails(forConstraint: Option[ConstraintDetails], forLevel: Option[ConstraintLevel], messages: List[(String, String)]) {
        // XML representation used by Form Builder
        def toXML(forLang: String): NodeInfo = {

            def levelAtt =
                if (forConstraint.isEmpty && forLevel.isEmpty)
                    "any"
                else if (forConstraint.isDefined)
                    forConstraint map (_.level.name) head
                else if (forLevel.isDefined)
                    forLevel map (_.name) head
                else
                    throw new IllegalStateException()

            def constraintExpressionAtt =
                forConstraint map (_.expression) getOrElse ""

            def constraintIdAtt =
                forConstraint map (_.id) getOrElse ""

            // The alert contains the message for the main language as an attribute, and the languages for the other
            // languages so we can write them back.
            <alert message={messages.toMap getOrElse (forLang, "")} level={levelAtt} constraint-expression={constraintExpressionAtt} constraint-id={constraintIdAtt}>{
                messages collect {
                    case (lang, message) if lang != forLang ⇒
                        <message lang={lang} value={message}/>
                }
            }</alert>
        }
    }

    object AlertDetails {
        def fromXML(node: NodeInfo, newIds: Iterator[String]) = {

            val messageAtt              = node attValue "message"
            val levelAtt                = node attValue "level"
            val constraintExpressionAtt = nonEmptyOrNone(node attValue "constraint-expression")

            val messagesElems = (node child "message" toList) map {
                message ⇒ (message attValue "lang", message attValue "value")
            }

            val hasConstraint = constraintExpressionAtt.isDefined && levelAtt != "any"

            // Generate a new id if it doesn't have one (new constraint created by the user)
            def constraintId  = nonEmptyOrNone(node attValue "constraint-id") getOrElse newIds.next()

            val constraintOpt = hasConstraint option ConstraintDetails(constraintId, constraintExpressionAtt.get, LevelByName(levelAtt))
            val levelOpt      = levelAtt != "any" && ! hasConstraint option levelAtt map LevelByName

            AlertDetails(constraintOpt, levelOpt, (currentLang, messageAtt) :: messagesElems)
        }
    }
}
