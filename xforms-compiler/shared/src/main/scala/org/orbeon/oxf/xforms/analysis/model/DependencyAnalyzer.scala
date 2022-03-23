/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis.model

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.xforms.analysis.XPathAnalysis
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.expr.Expression
import shapeless.syntax.typeable._

import java.io.PrintStream
import scala.annotation.tailrec
import scala.collection.compat._


// Analyze a tree of binds to determine expressions dependencies based on references to MIP variables,
// that is to binds which have a `name` attribute. The result is an evaluation order which satisfies
// the dependencies.
//
// See also: https://github.com/orbeon/orbeon-forms/issues/2186
//
object DependencyAnalyzer {

  val Logger = LoggerFactory.createLogger("org.orbeon.xforms.analysis.calculate")

  private case class BindDetails(
    staticBind : StaticBind,
    name       : Option[String],
    refs       : Set[String]
  )

  case class Vertex(name: String, refs: Set[Vertex], expr: Option[String], xpa: Option[XPathAnalysis]) {
    def transitiveRefs: Set[Vertex] = refs ++ refs.flatMap(_.transitiveRefs)
  }

  private object BindDetails {

    def fromStaticBindMIP(
      validBindNames : scala.collection.Set[String],
      staticBind     : StaticBind,
      mipOpt         : Option[StaticBind.XPathMIP]
    ): Option[BindDetails] =
      mipOpt map { mip =>

      val compiledExpr = mip.compiledExpression
      val expr         = compiledExpr.expression.getInternalExpression

      val referencedVariableNamesIt = SaxonUtils.iterateExternalVariableReferences(expr) filter validBindNames

      BindDetails(staticBind, staticBind.nameOpt, referencedVariableNamesIt.to(Set))
    }
  }

  // 2022-03-23: Unused.
  def findMissingVariableReferences(expr: Expression, validBindNames: scala.collection.Set[String]): Set[String] =
    (SaxonUtils.iterateExternalVariableReferences(expr) filterNot validBindNames).to(Set)

  def containsVariableReference(expr: Expression, name: String): Boolean =
    SaxonUtils.iterateExternalVariableReferences(expr) contains name

  //
  // Return an evaluation order or a `ValidationException` if there is a cycle.
  //
  // NOTE: If a variable reference is not found, this behaves as if the variable reference was missing.
  //
  def determineEvaluationOrder(
    model   : Model,
    mip     : ModelDefs.XPathMIP // `Model.Calculate` or `Model.Default`.
  ): (List[StaticBind], () => List[Vertex]) = {

    if (Logger.isDebugEnabled)
      Logger.debug(s"analyzing ${mip.name} dependencies for model ${model.staticId}")

    val allBindsByName = model.bindsByName

    val bindsDetailsForGivenMip = {

      val validBindNames = allBindsByName.keySet

      val allBindsIt = model.iterateAllBinds
      val detailsIt  = allBindsIt flatMap (b => BindDetails.fromStaticBindMIP(validBindNames, b, b.firstXPathMIP(mip)))

      detailsIt.toList
    }

    // The algorithm requires all vertices so create all the ones which are referenced by name by expressions, but
    // are not present in bindsWithMIPDetails.
    val otherBindDetails = {

      val existingBindNames = bindsDetailsForGivenMip flatMap (_.name) toSet
      val referredBindNames = bindsDetailsForGivenMip flatMap (_.refs) toSet
      val namesToAdd        = referredBindNames -- existingBindNames

      for {
        name       <- namesToAdd.toList
        staticBind <- allBindsByName.get(name)
      } yield
        BindDetails(staticBind, staticBind.nameOpt, Set.empty)
    }

    // NOTE: We would like to follow the original bind order as closely as possible, but currently we don't: the
    // order consists of nodes without references first, followed by the order or nodes with one reference, etc.
    // We might need to do a different algorithm to preserve the order.
    def sortTopologically(bindsForSort: List[BindDetails]) = {
      @tailrec
      def visit(bindDetails: List[BindDetails], done: List[StaticBind]): List[StaticBind] =
        bindDetails partition (_.refs.isEmpty) match {
          case (Nil, Nil) =>
            done
          case (Nil, head :: _) =>
            throw new ValidationException(
              s"MIP dependency cycle found for bind id `${head.staticBind.staticId}`",
              head.staticBind.locationData
            )
          case (noRefs, withRefs) =>
            visit(
              bindDetails = withRefs map (b => b.copy(refs = b.refs -- (noRefs flatMap (_.name)))),
              done        = noRefs.map(_.staticBind).reverse ::: done
            )
        }

      visit(bindsForSort, Nil).reverse
    }

    def logResult(explanation: List[Vertex]): Unit = {

      val maxNameWidth = explanation map (_.name.size) max

      val allExplanations =
        explanation map { case Vertex(name, refs, _, projectionDeps) =>

          val dependsOnMsg = if (refs.isEmpty) "" else refs map (_.name) mkString (" (references: ", ", ", ")")

          s"  ${name.padTo(maxNameWidth, ' ')} ($projectionDeps)$dependsOnMsg"
        } mkString "\n"

      Logger.debug(s"topological sort (${explanation.size} nodes):\n$allExplanations")
    }

    def buildExplanationGraph(result: List[StaticBind]): List[Vertex] = {

      val idsToRefs = bindsDetailsForGivenMip map (b => b.staticBind.staticId -> b.refs) toMap

      var verticesByIds: Map[String, Vertex] =
        otherBindDetails.map(d =>
          d.staticBind.staticId ->
            Vertex(
              d.name.get,
              Set.empty,
              None,
              d.staticBind.firstXPathMIP(mip) collect {
                case m if m.analysis.figuredOutDependencies => m.analysis
              }
            )
        ).toMap

      def explanation(staticBind: StaticBind) = {

        val staticId = staticBind.staticId
        val refs     = idsToRefs.getOrElse(staticBind.staticId, Set.empty)

        val vertex = Vertex(
          staticId,
          refs map allBindsByName map (b => verticesByIds(b.staticId)),
          staticBind.firstXPathMIP(mip).map(_.expression),
          staticBind.firstXPathMIP(mip).map(_.analysis)
        )
        verticesByIds += staticId -> vertex
        vertex
      }

      result map explanation
    }

    // We are only interested in the binds containing the MIP
    val idsToKeep = bindsDetailsForGivenMip map (_.staticBind.staticId) toSet

    val resultWithOther = sortTopologically(bindsDetailsForGivenMip ++ otherBindDetails)
    val resultFiltered  = resultWithOther filter (b => idsToKeep(b.staticId))

    lazy val explanation = buildExplanationGraph(resultWithOther)

    if (resultFiltered.nonEmpty && Logger.isDebugEnabled)
      explanation |!> logResult

    (resultFiltered, () => explanation)
  }
}
