/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import cats.syntax.option._
import org.orbeon.dom.{Element, QName, Text}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.{MapSet, XFormsStaticElementValue}
import org.orbeon.oxf.xforms.analysis.PathMapXPathAnalysisBuilder.buildInstanceString
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysis.valueOrSelectAttribute
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model._
import org.orbeon.oxf.xml.ShareableXPathStaticContext
import org.orbeon.oxf.xml.dom.Extensions.{DomElemOps, VisitorListener}
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope

import scala.collection.mutable


object ElementAnalysisTreeXPathAnalyzer {

  import Private._

  def analyzeXPath(
    partAnalysisCtx : PartAnalysisContextAfterTree,
    e               : ElementAnalysis)(implicit
    logger          : IndentedLogger
  ): Unit = {

    e.contextAnalysis = computeContextAnalysis(partAnalysisCtx, e)
    e.bindingAnalysis = computeBindingAnalysis(partAnalysisCtx, e)
    e.valueAnalysis   = computeValueAnalysis  (partAnalysisCtx, e)

    e match {
      case e: Model =>
        for (variable <- e.variablesSeq)
          analyzeXPath(partAnalysisCtx, variable)

        analyzeBindsXPath(partAnalysisCtx, e)

      case e: SelectionControlTrait =>
        e.itemsetAnalysis = computeItemsetAnalysis(partAnalysisCtx, e)
      case _ =>
    }
  }

  class SimplePathMapContext(e: ElementAnalysis) {

    // Current element
    def element: ElementAnalysis = e

    // Return the analysis for the context in scope
    def context: Option[ElementAnalysis] = ElementAnalysis.getClosestAncestorInScope(e, e.scope)

    // Return a map of static id => analysis for all the ancestor-or-self in scope.
    def getInScopeContexts: collection.Map[String, ElementAnalysis] =
      mutable.LinkedHashMap(
        ElementAnalysis.getAllAncestorsInScope(e, e.scope, includeSelf = true) map
          (elementAnalysis => elementAnalysis.staticId -> elementAnalysis): _*
      )

    // Return analysis for closest ancestor repeat in scope.
    def getInScopeRepeat: Option[RepeatControl] = e.ancestorRepeatInScope
  }

  private object Private {

    // Definition of the various scopes:
    //
    // - Container scope: scope defined by the closest ancestor XBL binding. This scope is directly related to the
    //   prefix of the prefixed id. E.g. <fr:foo id="my-foo"> defines a new scope `my-foo`. All children of `my-foo`,
    //   including directly nested handlers, models, shadow trees, have the `my-foo` prefix.
    //
    // - Inner scope: this is the scope given this control if this control has `xxbl:scope='inner'`. It is usually the
    //   same as the container scope, except for directly nested handlers.
    //
    // - Outer scope: this is the scope given this control if this control has `xxbl:scope='outer'`. It is usually the
    //   actual scope of the closest ancestor XBL bound element, except for directly nested handlers.

    def getChildElementScope(partAnalysisCtx: PartAnalysisContextAfterTree, e: ElementAnalysis, childElement: Element): Scope = {
      val childPrefixedId =  XFormsId.getRelatedEffectiveId(e.prefixedId, childElement.idOrNull)
      partAnalysisCtx.scopeForPrefixedId(childPrefixedId)
    }

    def computeContextAnalysis(
      partAnalysisCtx: PartAnalysisContextAfterTree,
      e              : ElementAnalysis)(implicit
      logger         : IndentedLogger
    ): Option[XPathAnalysis] =
      e match {
        case _: Model                                    => None
        case e: OutputControl if e.staticValue.isDefined => None // Q: Do we need to handle the context anyway?
        case e                                           => computeBasicContextAnalysis(partAnalysisCtx, e)
      }

    def computeBasicContextAnalysis(
      partAnalysisCtx: PartAnalysisContextAfterTree,
      e              : ElementAnalysis)(implicit
      logger         : IndentedLogger
    ): Option[XPathAnalysis] = {

      def findInScopeContext: Option[XPathAnalysis] =
        ElementAnalysis.getClosestAncestorInScopeModel(e, (e.scope, e.model)) match {
          case Some(ancestor) =>
            // There is an ancestor in the same scope with same model, use its analysis as base
            getChildrenContext(partAnalysisCtx, ancestor)
          case None =>
            // We are top-level in a scope/model combination
            e.model match {
              case Some(containingModel) => getChildrenContext(partAnalysisCtx, containingModel) // ask model
              case None                  => None // no model
            }
        }

      e.context match {
        case Some(context) =>
          // @context attribute, use the overridden in-scope context
          analyzeXPathWithStringExpression(partAnalysisCtx, e, findInScopeContext, e.inScopeVariables, context).some
        case None =>
          // No `@context` attribute, use the original in-scope context
          findInScopeContext
      }
    }

    def computeBindingAnalysis(
      partAnalysisCtx: PartAnalysisContextAfterTree,
      e              : ElementAnalysis)(implicit
      logger         : IndentedLogger
    ): Option[XPathAnalysis] =
      e match {
        case _: Model                                               => None
        case e: OutputControl if e.staticValue.isDefined            => None
        // If control does not have an XPath binding, return one anyway so that controls w/o their own binding also get updated.
        case e: ComponentControl if ! e.commonBinding.modeBinding   => e.contextAnalysis
        case e                                                      => computeBasicBindingAnalysis(partAnalysisCtx, e)
      }

    def computeBasicBindingAnalysis(
      partAnalysisCtx : PartAnalysisContextAfterTree,
      e               : ElementAnalysis)(implicit
      logger          : IndentedLogger)
    : Option[XPathAnalysis] =
      e.bind match {
        case Some(bindStaticId) =>
          // Use `@bind` analysis directly from model
          val model = partAnalysisCtx.getModelByScopeAndBind(e.scope, bindStaticId)
          if (model eq null)
            throw new ValidationException(
              s"Reference to non-existing bind id `$bindStaticId`",
              ElementAnalysis.createLocationData(e.element)
            )
          model.bindsById.get(bindStaticId) map (_.bindingAnalysis) orNull
        case None =>
          // No `@bind`
          e.ref match {
            case Some(ref) =>
              // New binding expression
              analyzeXPathWithStringExpression(partAnalysisCtx, e, e.contextAnalysis, e.inScopeVariables, ref).some
            case None =>
              // TODO: Return a binding anyway so that controls w/o their own binding also get updated.
              e.contextAnalysis
          }
      }

    def computeValueAnalysis(
      partAnalysisCtx : PartAnalysisContextAfterTree,
      e               : ElementAnalysis)(implicit
      logger          : IndentedLogger
    ): Option[XPathAnalysis] =
      e match {
        case e: LHHAAnalysis =>

          val result =
            e.expressionOrConstant match {
              case Left(expr) =>
                analyzeXPathWithStringExpression(partAnalysisCtx, e, getChildrenContext(partAnalysisCtx, e), e.inScopeVariables, expr).some
              case Right(_) =>
                // Value of LHHA is 100% static and analysis is constant
                StringAnalysis.some
            }

          // This is not ideal, but we don't need this anymore
          e.contextAnalysis = None
          e.bindingAnalysis = None

          result

        case e: StaticBind =>
          // Compute value analysis if we have a `type` or `xxf:whitespace`, otherwise don't bother
          e.typeMIPOpt orElse e.nonPreserveWhitespaceMIPOpt match {
            case Some(_) if e.hasBinding => analyzeXPathWithStringExpression(partAnalysisCtx, e, getChildrenContext(partAnalysisCtx, e), e.inScopeVariables, StaticXPath.makeStringExpression(".")).some
            case _                       => None
          }

        case _: Model                                    => None
        case e: OutputControl if e.staticValue.isDefined => None
        case e: AttributeControl                         => analyzeXPathWithStringExpression(partAnalysisCtx, e, getChildrenContext(partAnalysisCtx, e), e.inScopeVariables, e.attributeValue, avt = true).some
        case e: VariableAnalysisTrait =>
          e.nestedValueAnalysis match {
            case Some(valueElem) =>
              // Value is provided by nested `xxf:value`

              ElementAnalysisTreeXPathAnalyzer.analyzeXPath(partAnalysisCtx, valueElem)

              // Custom value analysis done here
              val valueXPathAnalysis =
                valueOrSelectAttribute(valueElem.element) match {
                  case Some(value) =>
                    analyzeXPathWithStringExpression(partAnalysisCtx, valueElem, getChildrenContext(partAnalysisCtx, valueElem), valueElem.inScopeVariables, value).some
                  case None =>
                    StringAnalysis.some // TODO: store constant value?
                }

              valueXPathAnalysis

            case None =>
              // No nested `xxf:value` element

              valueOrSelectAttribute(e.element) match {
                case Some(value) => analyzeXPathWithStringExpression(partAnalysisCtx, e, getChildrenContext(partAnalysisCtx, e), e.inScopeVariables, value).some
                case _           => StringAnalysis.some // TODO: store constant value?
              }
          }
        case e: ValueTrait =>
          val subExpression =
            StaticXPath.makeStringExpression(
              e.value getOrElse "."
            )
          analyzeXPathWithStringExpression(partAnalysisCtx, e, getChildrenContext(partAnalysisCtx, e), e.inScopeVariables, subExpression).some
        case _ =>
          None
      }

    def analyzeBindsXPath(
      partAnalysisCtx : PartAnalysisContextAfterTree,
      model           : Model)(implicit
      logger          : IndentedLogger
    ): Unit = {
      // Analyze all binds and return whether all of them were successfully analyzed
      model.figuredAllBindRefAnalysis = (model.topLevelBinds map (analyzeXPathGather(partAnalysisCtx, model, _))).foldLeft(true)(_ && _)

      // Analyze all MIPs
      // NOTE: Do this here, because MIPs can depend on bind/@name, which requires all bind/@ref to be analyzed first
      model.topLevelBinds foreach (analyzeBindMips(partAnalysisCtx, model, _))

      if (! model.figuredAllBindRefAnalysis) {
        model.bindInstances.clear()
        model.computedBindExpressionsInstances.clear()
        model.validationBindInstances.clear()
        // keep bindAnalysis as those can be used independently from each other
      }

      if (partAnalysisCtx.staticProperties.isCalculateDependencies) {
        model.recalculateOrder  = Some(DependencyAnalyzer.determineEvaluationOrder(model, ModelDefs.Calculate))
        model.defaultValueOrder = Some(DependencyAnalyzer.determineEvaluationOrder(model, ModelDefs.Default))
      }
    }

    def analyzeBindMips(partAnalysisCtx: PartAnalysisContextAfterTree, bindTree: BindTree, bind: StaticBind): Unit = {
      // Analyze local MIPs if there is a @ref
      bind.ref match {
        case Some(_) =>
          for (mips <- bind.allMIPNameToXPathMIP.values; mip <- mips)
            analyzeMip(partAnalysisCtx, bindTree, bind, mip)
        case None =>
      }

      // Analyze descendants
      bind.childrenBindsIt foreach (analyzeBindMips(partAnalysisCtx, bindTree, _))
    }

    def analyzeMip(
      partAnalysisCtx : PartAnalysisContextAfterTree,
      bindTree        : BindTree,
      bind            : StaticBind,
      mip             : StaticBind.XPathMIP
    ): Unit = {

      val allBindVariablesInScope = bindTree.allBindVariables

      // Saxon: "In the case of free-standing XPath expressions it will be the StaticContext object"
      val staticContext  = mip.compiledExpression.expression.getInternalExpression.getContainer.asInstanceOf[ShareableXPathStaticContext]
      if (staticContext ne null) {
        // NOTE: The StaticContext can be null if the expression is a constant such as BooleanValue
        val usedVariables = staticContext.referencedVariables

        // Check whether all variables used by the expression are actually in scope, throw otherwise
        usedVariables find (name => ! allBindVariablesInScope.contains(name.getLocalName)) foreach { name =>
          throw new ValidationException("Undeclared variable in XPath expression: $" + name.getClarkName, bind.locationData)
        }
      }

      // Analyze and remember if figured out
      analyzeXPathWithCompiledExpression(partAnalysisCtx, bind, getChildrenContext(partAnalysisCtx, bind), allBindVariablesInScope, mip.compiledExpression) match {
        case valueAnalysis if valueAnalysis.figuredOutDependencies => mip.analysis = valueAnalysis
        case _ => // NOP
      }
    }

    // Return true if analysis succeeded
    def analyzeXPathGather(
      partAnalysisCtx : PartAnalysisContextAfterTree,
      bindTree        : BindTree,
      bind            : StaticBind)(implicit
      logger          : IndentedLogger
    ): Boolean = {

      // Analyze context/binding
      ElementAnalysisTreeXPathAnalyzer.analyzeXPath(partAnalysisCtx, bind)

      // If successful, gather derived information
      val refSucceeded =
        bind.ref match {
          case Some(_) =>
            bind.bindingAnalysis match {
              case Some(bindingAnalysis) if bindingAnalysis.figuredOutDependencies =>
                // There is a binding and analysis succeeded

                // Remember dependent instances
                val returnableInstances = bindingAnalysis.returnableInstances
                bindTree.bindInstances ++= returnableInstances

                if (bind.hasCalculateComputedMIPs || bind.hasCustomMIPs)
                  bindTree.computedBindExpressionsInstances ++= returnableInstances

                if (bind.hasValidateMIPs)
                  bindTree.validationBindInstances ++= returnableInstances

                true

              case _ =>
                // Analysis failed
                false
            }

          case None =>
            // No binding, consider a success
            true
        }

      // Analyze children
      // TODO: CHECK eagerness vs. `forall` short-circuit.
      val childrenSucceeded = (bind.childrenBinds map (analyzeXPathGather(partAnalysisCtx, bindTree, _))).forall(identity)

      // Result
      refSucceeded && childrenSucceeded
    }

    def computeItemsetAnalysis(
      partAnalysisCtx : PartAnalysisContextAfterTree,
      e               : SelectionControlTrait)(implicit
      logger          : IndentedLogger
    ): Option[XPathAnalysis] = {

      // TODO: operate on nested ElementAnalysis instead of Element

      var combinedAnalysis: XPathAnalysis = StringAnalysis

      e.element.visitDescendants(
        new VisitorListener {

          var stack: List[ElementAnalysis] = e :: Nil

          def startElement(element: Element): Unit = {

            val staticId   = element.idOrNull
            val prefixedId = XFormsId.getRelatedEffectiveId(e.prefixedId, staticId)

            // Make lazy as might not be used
            lazy val itemElementAnalysis = {

              val ea =
                new ElementAnalysis(
                  -1,
                  element,
                  stack.head.some,
                  None,
                  staticId,
                  prefixedId,
                  partAnalysisCtx.getNamespaceMapping(prefixedId).orNull, // probably should throw
                  getChildElementScope(partAnalysisCtx, stack.head, element),
                  e.containerScope
                ) with ValueTrait
                  with OptionalSingleNode
                  with ViewTrait

              ElementAnalysisTreeBuilder.setModelOnElement(partAnalysisCtx, ea)

              ea
            }

            def processElement(qName: QName, required: Boolean): Unit = {

              val nestedElementOpt = element.elementOpt(qName)

              if (required)
                require(nestedElementOpt.isDefined)

              nestedElementOpt foreach { nestedElement =>

                val staticId   = nestedElement.idOrNull
                val prefixedId = XFormsId.getRelatedEffectiveId(e.prefixedId, staticId)

                val (expressionOrConstant, _) =
                  XFormsStaticElementValue.getElementExpressionOrConstant(
                    outerElem       = nestedElement,
                    containerPrefix = "",    // won't be used
                    isWithinRepeat  = false, // won't be used
                    acceptHTML      = true
                  )

                val nestedAnalysis = new LHHAAnalysis( // TODO: Weird! We have LHH, but also `value` and `copy`.
                  index                     = -1,
                  element                   = nestedElement,
                  parent                    = itemElementAnalysis.some,
                  preceding                 = None,
                  staticId                  = staticId,
                  prefixedId                = prefixedId,
                  namespaceMapping          = partAnalysisCtx.getNamespaceMapping(prefixedId).orNull, // probably should throw,
                  scope                     = getChildElementScope(partAnalysisCtx, itemElementAnalysis, nestedElement),
                  containerScope            = e.containerScope,
                  expressionOrConstant      = expressionOrConstant,
                  isPlaceholder             = false,
                  containsHTML              = false,
                  hasLocalMinimalAppearance = false,
                  hasLocalFullAppearance    = false,
                  hasLocalLeftAppearance    = false
                )
                ElementAnalysisTreeBuilder.setModelOnElement(partAnalysisCtx, nestedAnalysis)
                ElementAnalysisTreeXPathAnalyzer.analyzeXPath(partAnalysisCtx, nestedAnalysis)
                combinedAnalysis = combineXPathAnalysis(combinedAnalysis, nestedAnalysis.valueAnalysis.get)
              }
            }

            element.getQName match {

              case XFORMS_ITEM_QNAME | XFORMS_ITEMSET_QNAME =>

                // Analyze container and add as a value dependency
                // We add this as dependency because the itemset must also be recomputed if any returned item of
                // a container is changing. That's because that influences whether the item is present or not,
                // as in:
                //
                // <xf:itemset ref=".[not(context() = instance('foo'))]">
                //   <xf:label>Year</xf:label>
                //   <xf:value/>
                // </xf:itemset>
                //
                // This is not an issue with controls, as container relevance ensures we don't evaluate nested
                // expressions, but it must be done for itemsets.
                //
                // See also #289 https://github.com/orbeon/orbeon-forms/issues/289 (closed)
                ElementAnalysisTreeXPathAnalyzer.analyzeXPath(partAnalysisCtx, itemElementAnalysis)
                combinedAnalysis =
                  combineXPathAnalysis(
                    combinedAnalysis,
                    makeValuesDependencies(itemElementAnalysis.bindingAnalysis.get)
                  )

                processElement(LABEL_QNAME, required = true)
                processElement(XFORMS_VALUE_QNAME, required = false)
                processElement(XFORMS_COPY_QNAME, required = false)

                if (e.isFull) {
                  processElement(HELP_QNAME, required = false)
                  processElement(HINT_QNAME, required = false)
                }

              case XFORMS_CHOICES_QNAME =>

                // Analyze container and add as a value dependency (see above)
                ElementAnalysisTreeXPathAnalyzer.analyzeXPath(partAnalysisCtx, itemElementAnalysis)
                combinedAnalysis =
                  combineXPathAnalysis(
                    combinedAnalysis,
                    makeValuesDependencies(itemElementAnalysis.bindingAnalysis.get)
                  )

                processElement(LABEL_QNAME, required = false) // label is optional on xf:choices

                // Always push the container
                stack ::= itemElementAnalysis

              case _ => // ignore
            }
          }

          def endElement(element: Element): Unit =
            if (element.getQName == XFORMS_CHOICES_QNAME)
              stack = stack.tail

          def text(text: Text): Unit = ()
        },
        mutable = false
      )

      combinedAnalysis.some
    }

    def analyzeXPathWithStringExpression(
      partAnalysisCtx  : PartAnalysisContextAfterTree,
      e                : ElementAnalysis,
      contextAnalysis  : Option[XPathAnalysis],
      inScopeVariables : Map[String, VariableTrait],
      xpathString      : String,
      avt              : Boolean = false)(implicit
      logger           : IndentedLogger
    ): XPathAnalysis =
      PathMapXPathAnalysisBuilder(
        partAnalysisCtx           = partAnalysisCtx,
        xpathString               = xpathString,
        namespaceMapping          = partAnalysisCtx.getNamespaceMapping(e.prefixedId).orNull,
        baseAnalysis              = contextAnalysis,
        inScopeVariables          = inScopeVariables,
        pathMapContext            = new SimplePathMapContext(e),
        scope                     = e.scope,
        defaultInstancePrefixedId = e.model flatMap (_.defaultInstancePrefixedId),
        locationData              = e.locationData,
        element                   = e.element,
        avt                       = avt
      )

    def analyzeXPathWithCompiledExpression(
      partAnalysisCtx  : PartAnalysisContextAfterTree,
      e                : ElementAnalysis,
      contextAnalysis  : Option[XPathAnalysis],
      inScopeVariables : Map[String, VariableTrait],
      expression       : CompiledExpression
    ): XPathAnalysis = {
      val defaultInstancePrefixedId = e.model flatMap (_.defaultInstancePrefixedId)
      PathMapXPathAnalysisBuilder(
        partAnalysisCtx           = partAnalysisCtx,
        compiledExpression        = expression,
        baseAnalysis              = contextAnalysis,
        inScopeVariables          = inScopeVariables,
        pathMapContext            = new SimplePathMapContext(e),
        scope                     = e.scope,
        defaultInstancePrefixedId = defaultInstancePrefixedId,
        element                   = e.element
      )
    }

    def combineXPathAnalysis(xpa1: XPathAnalysis, xpa2: XPathAnalysis): XPathAnalysis =
      xpa1 match {
        case na: NegativeAnalysis =>
          new NegativeAnalysis(combineXPathStrings(na.xpathString, xpa2.xpathString))
        case StringAnalysis =>
          xpa2
        case pmxpa: PathMapXPathAnalysis =>
          if (! pmxpa.figuredOutDependencies || ! xpa2.figuredOutDependencies)
            // Either side is negative, return a constant negative with the combined expression
            new NegativeAnalysis(combineXPathStrings(pmxpa.xpathString, xpa2.xpathString))
          else
            xpa2 match {
              case _: ConstantXPathAnalysis =>
                // Other is constant positive analysis, so just return this
                pmxpa
              case other: PathMapXPathAnalysis =>
                // Both are PathMap analysis so actually combine
                new PathMapXPathAnalysis(
                  combineXPathStrings(pmxpa.xpathString, other.xpathString),
                  true,
                  pmxpa.valueDependentPaths combine other.valueDependentPaths,
                  pmxpa.returnablePaths combine other.returnablePaths,
                  pmxpa.dependentModels ++ other.dependentModels,
                  pmxpa.dependentInstances ++ other.dependentInstances)(
                  {
                    val newPathmap = pmxpa.pathmap.get.clone
                    newPathmap.addRoots(other.pathmap.get.clone.getPathMapRoots)
                    Some(newPathmap)
                  },
                )
              case _ =>
                throw new IllegalStateException // should not happen
            }
      }

    // Convert the `XPathAnalysis` into the same analysis but where values have become dependencies
    def makeValuesDependencies(xpa: XPathAnalysis): XPathAnalysis =
      xpa match {
        case cxpa: ConstantXPathAnalysis => cxpa
        case pmxpa: PathMapXPathAnalysis =>
          new PathMapXPathAnalysis(
            xpathString            = pmxpa.xpathString,
            figuredOutDependencies = true,
            valueDependentPaths    = pmxpa.valueDependentPaths combine pmxpa.returnablePaths,
            returnablePaths        = MapSet.empty[String, String],
            dependentModels        = pmxpa.dependentModels,
            dependentInstances     = pmxpa.dependentInstances)(
            pathmap                = Some(pmxpa.pathmap.get.clone),
          )
      }

    // Some kind of combination that makes sense (might not exactly match the combined PathMap)
    def combineXPathStrings(s1: String, s2: String): String = "(" + s1 + ") | (" + s2 + ")"

    // Return the context within which children elements or values evaluate. This is the element binding if any, or the
    // element context if there is no binding.
    def getChildrenContext(
      partAnalysisCtx: PartAnalysisContextAfterTree,
      e              : ElementAnalysis
    ): Option[XPathAnalysis] =
      e match {
        case m: Model =>
          m.defaultInstancePrefixedId map { defaultInstancePrefixedId => // instance('defaultInstanceId')
            PathMapXPathAnalysisBuilder(
              partAnalysisCtx           = partAnalysisCtx,
              xpathString               = buildInstanceString(defaultInstancePrefixedId),
              namespaceMapping          = null,
              baseAnalysis              = None,
              inScopeVariables          = Map.empty,
              pathMapContext            = null,
              scope                     = m.scope,
              defaultInstancePrefixedId = defaultInstancePrefixedId.some,
              locationData              = m.locationData,
              element                   = m.element,
              avt                       = false)(
              logger                    = null // only used for warnings
            )
          }
        case e =>
          if (e.hasBinding) e.bindingAnalysis else e.contextAnalysis
      }
  }
}