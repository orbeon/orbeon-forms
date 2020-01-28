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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.function.{Instance, XFormsFunction}
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.saxon.MapFunctions
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr._
import org.orbeon.saxon.function.ProcessTemplate
import org.orbeon.saxon.om._
import org.orbeon.saxon.pattern.NameTest
import org.orbeon.saxon.value.{StringValue, Value}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.annotation.tailrec
import scala.collection.compat._

class XXFormsResource extends XFormsFunction {

  import XXFormsResource._

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext

    val resourceKeyArgument = stringArgument(0)
    val instanceArgumentOpt = stringArgumentOpt(1)
    val templateParamsOpt   = itemsArgumentOpt(2) map (it => MapFunctions.collectMapValues(it.iterator).next())

    def findInstance = instanceArgumentOpt match {
      case Some(instanceName) => resolveOrFindByStaticOrAbsoluteId(instanceName)
      case None               => resolveOrFindByStaticOrAbsoluteId("orbeon-resources") orElse resolveOrFindByStaticOrAbsoluteId("fr-form-resources")
    }

    def findResourcesElement = findInstance collect { case instance: XFormsInstance => instance.rootElement }

    def processResourceString(resourceOrTemplate: String): String =
      templateParamsOpt match {
        case Some(params) =>

          val javaNamedParamsIt = params.iterator map {
            case (key, value) =>
              val javaParamOpt = asScalaIterator(Value.asIterator(value)) map Value.convertToJava nextOption()
              key.getStringValue -> javaParamOpt.orNull
          }

          ProcessTemplate.processTemplateWithNames(resourceOrTemplate, javaNamedParamsIt.to(List), currentLocale)

        case None =>
          resourceOrTemplate
      }

    val resultOpt =
      for {
        elementAnalysis <- elementAnalysisForSource
        resources       <- findResourcesElement
        requestedLang   <- XXFormsLang.resolveXMLangHandleAVTs(getContainingDocument, elementAnalysis)
        resourceRoot    <- findResourceElementForLang(resources, requestedLang)
        leaf            <- pathFromTokens(resourceRoot, splitResourceName(resourceKeyArgument)).headOption
      } yield
        stringToStringValue(processResourceString(leaf.stringValue))

    resultOpt.orNull
  }

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {

    updatePathMap(
      getExecutable.getConfiguration.getNamePool,
      arguments,
      pathMap,
      pathMapNodeSet
    )

    // We return an atomic value
    null
  }
}

object XXFormsResource {

  private val IndexRegex = """(\d+)""".r

  def splitResourceName(s: String): List[String] =
    s.splitTo[List](".")

  def flattenResourceName(s: String): List[String] =
    splitResourceName(s) filter Name10Checker.getInstance.isValidNCName

  // Hand-made simple path search
  //
  // - path *must* have the form `foo.bar.2.baz` (names with optional index parts)
  // - each path element must be a NCName (non-qualified) except for indexes
  // - as in XPath, non-qualified names mean "in no namespace"
  //
  // NOTE: Ideally should check if this is faster than using a pre-compiled Saxon XPath expression!
  def pathFromTokens(context: NodeInfo, tokens: List[String]): List[NodeInfo] = {

      @tailrec
      def findChild(parents: List[NodeInfo], tokens: List[String]): List[NodeInfo] =
        tokens match {
          case Nil => parents
          case token :: restTokens =>
            parents match {
              case Nil => Nil
              case parents =>
                token match {
                  case IndexRegex(index) =>
                    findChild(List(parents(index.toInt)), restTokens)
                  case path if Name10Checker.getInstance.isValidNCName(token) =>
                    findChild(parents / token toList, restTokens)
                  case _ =>
                    throw new IllegalArgumentException(s"invalid resource path `${tokens mkString "."}`")
                }
            }
        }

      findChild(List(context), tokens)
  }

  def findResourceElementForLang(resourcesElement: NodeInfo, requestedLang: String): Option[NodeInfo] = {
    val availableLangs = resourcesElement / "resource" /@ "lang"
    availableLangs find (_ === requestedLang) orElse availableLangs.headOption flatMap (_.parentOption)
  }

  def updatePathMap(
    namePool       : NamePool,
    arguments      : Seq[Expression],
    pathMap        : PathMap,
    pathMapNodeSet : PathMap.PathMapNodeSet
  ): PathMap.PathMapNodeSet = {

    // Only support dependencies if we can figure out the resource path statically
    // In theory, we could also support the case where we don't know the path, and assume that any change to the
    // resources would cause a miss. But I don't think we have a way to express this right now. Note that the case
    // where a single resource changes is rare as we tend to use a readonly instance for resources anyway. But the
    // function must work in either case, readonly and readwrite.
    val resourcePath = arguments.head match {
      case s: StringLiteral =>
        flattenResourceName(s.getStringValue) // this removes the indexes if any
      case _ =>
        pathMap.setInvalidated(true)
        return null
    }

    // Dependency on language
    XXFormsLang.addXMLLangDependency(pathMap)
    if (pathMap.isInvalidated)
      return null

    // Dependency on all arguments
    arguments foreach (_.addToPathMap(pathMap, pathMapNodeSet))

    // Add dependency as if on instance('my-instance-name')
    def addInstanceDependency(name: String): Unit = {

      def newInstanceExpression = {
        val instanceExpression = new Instance

        instanceExpression.setFunctionName(new StructuredQName("", NamespaceConstant.FN, "instance"))
        instanceExpression.setArguments(Array(new StringLiteral(name)))

        instanceExpression
      }

      // Start with new instance() root
      var target = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(newInstanceExpression))

      // Add path elements to pathmap
      "resource" :: resourcePath foreach { name =>
        val test = new NameTest(Type.ELEMENT, "", name, namePool)
        target = new AxisExpression(Axis.CHILD, test).addToPathMap(pathMap, target)
      }

      // The result is used as an atomic value
      target.setAtomized()
    }

    addInstanceDependency("orbeon-resources")
    addInstanceDependency("fr-form-resources")

    // We return an atomic value
    null
  }
}