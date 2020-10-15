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
import org.orbeon.oxf.util.XPath.CompiledExpression
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysis.{valueOrSelectAttribute, valueOrSequenceElement}
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model._
import org.orbeon.oxf.xml.dom.Extensions.{DomElemOps, VisitorListener}
import org.orbeon.oxf.xml.{ShareableXPathStaticContext, XMLUtils}
import org.orbeon.xforms.XFormsNames._

import scala.collection.mutable


object ElementAnalysisTreeXPathAnalyzer {

  import Private._

  def analyzeXPath(e: ElementAnalysis): Unit = {

    e.contextAnalysis = computeContextAnalysis(e)
    e.bindingAnalysis = computeBindingAnalysis(e)
    e.valueAnalysis   = computeValueAnalysis(e)

    e match {
      case e: Model =>
        for (variable <- e.variablesSeq)
          analyzeXPath(variable)

        analyzeBindsXPath(e.bindTree)

      case e: SelectionControlTrait =>
        e.itemsetAnalysis = computeItemsetAnalysis(e)
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

    def computeContextAnalysis(e: ElementAnalysis): Option[XPathAnalysis] =
      e match {
        case _: LHHAAnalysis                             => None // delegate to `computeValueAnalysis`
        case _: Model                                    => None
        case e: OutputControl if e.staticValue.isDefined => None // Q: Do we need to handle the context anyway?
        case e                                           => computeBasicContextAnalysis(e)
      }

    def computeBasicContextAnalysis(e: ElementAnalysis): Option[XPathAnalysis] = {

      def findInScopeContext: Option[XPathAnalysis] =
        ElementAnalysis.getClosestAncestorInScopeModel(e, (e.scope, e.model)) match {
          case Some(ancestor: ElementAnalysis) =>
            // There is an ancestor in the same scope with same model, use its analysis as base
            ancestor.getChildrenContext
          case None =>
            // We are top-level in a scope/model combination
            e.model match {
              case Some(containingModel) => containingModel.getChildrenContext // ask model
              case None                  => None // no model
            }
        }

      e.context match {
        case Some(context) =>
          // @context attribute, use the overridden in-scope context
          analyzeXPath(e, findInScopeContext, context).some
        case None =>
          // No `@context` attribute, use the original in-scope context
          findInScopeContext
      }
    }

    def computeBindingAnalysis(e: ElementAnalysis): Option[XPathAnalysis] =
      e match {
        case _: LHHAAnalysis                                        => None // delegate to `computeValueAnalysis`
        case _: Model                                               => None
        case e: OutputControl if e.staticValue.isDefined            => None
        // If control does not have an XPath binding, return one anyway so that controls w/o their own binding also get updated.
        case e: ComponentControl if ! e.commonBinding.modeBinding   => e.contextAnalysis
        case e                                                      => computeBasicBindingAnalysis(e.part, e)
      }

    def computeBasicBindingAnalysis(part: PartAnalysisImpl, e: ElementAnalysis): Option[XPathAnalysis] =
      e.bind match {
        case Some(bindStaticId) =>
          // Use `@bind` analysis directly from model
          val model = part.getModelByScopeAndBind(e.scope, bindStaticId)
          if (model eq null)
            throw new ValidationException(
              s"Reference to non-existing bind id `$bindStaticId`",
              ElementAnalysis.createLocationData(e.element)
            )
          model.bindsById.get(bindStaticId) map (_.bindingAnalysis) orNull
        case None =>
          // No @bind
          e.ref match {
            case Some(ref) =>
              // New binding expression
              analyzeXPath(e, e.contextAnalysis, ref).some
            case None =>
              // TODO: Return a binding anyway so that controls w/o their own binding also get updated.
              e.contextAnalysis
          }
      }

    def computeValueAnalysis(e: ElementAnalysis): Option[XPathAnalysis] =
      e match {
        case e: LHHAAnalysis =>
          if (e.staticValue.isEmpty) {
            // Value is likely not static

            // Delegate to concrete implementation
            val delegateAnalysis =
              new ElementAnalysis(e.part, e.index /* wrong */, e.element, e.parent, e.preceding, e.scope)
                with ValueTrait with OptionalSingleNode with ViewTrait

            ElementAnalysisTreeXPathAnalyzer.analyzeXPath(delegateAnalysis)

            if (e.ref.isDefined || e.value.isDefined) {
              // 1. E.g. <xf:label model="…" context="…" value|ref="…"/>

              // Don't assert because we want to support nested `fr:param` elements in Form Builder.
              //assert(element.elements.isEmpty) // no children elements allowed in this case

              // Use value provided by the delegate
              delegateAnalysis.valueAnalysis
            } else {
              // 2. E.g. <xf:label>…<xf:output value|ref=""…/>…<span class="{…}">…</span></xf:label>

              // NOTE: We do allow @context and/or @model on LHHA element, which can change the context

              // The subtree can only contain HTML elements interspersed with xf:output. HTML elements may have AVTs.
              var combinedAnalysis: XPathAnalysis = StringAnalysis()

              e.element.visitDescendants(
                new VisitorListener {
                  val hostLanguageAVTs: Boolean = XFormsProperties.isHostLanguageAVTs
                  def startElement(element: Element): Unit = {
                    if (element.getQName == XFORMS_OUTPUT_QNAME) {
                      // Add dependencies
                      val outputAnalysis =
                        new ElementAnalysis(
                          part               = e.part,
                          index              = e.index, // wrong
                          element            = element,
                          parent             = delegateAnalysis.some,
                          preceding          = None,
                          scope              = delegateAnalysis.getChildElementScope(element)
                        ) with ValueTrait with OptionalSingleNode with ViewTrait
                      ElementAnalysisTreeXPathAnalyzer.analyzeXPath(outputAnalysis)
                      if (outputAnalysis.valueAnalysis.isDefined)
                        combinedAnalysis = combinedAnalysis combine outputAnalysis.valueAnalysis.get
                    } else if (hostLanguageAVTs) {
                      for {
                        attribute <- element.attributes
                        attributeValue = attribute.getValue
                        if XMLUtils.maybeAVT(attributeValue)
                      } locally {
                        // not supported just yet
                        combinedAnalysis = NegativeAnalysis(attributeValue)
                      }
                    }
                  }

                  def endElement(element: Element): Unit = ()
                  def text(text: Text): Unit = ()
                },
                mutable = false
              )

              // Result of all combined analyses
              combinedAnalysis.some
            }
          } else {
            // Value of LHHA is 100% static and analysis is constant
            StringAnalysis().some
          }
        case e: StaticBind =>
          // Compute value analysis if we have a `type` or `xxf:whitespace`, otherwise don't bother
          e.typeMIPOpt orElse e.nonPreserveWhitespaceMIPOpt match {
            case Some(_) if e.hasBinding => analyzeXPath(e, e.getChildrenContext, "string(.)").some // TODO: function to create `string()`
            case _                       => None
          }

        case _: Model                                    => None
        case e: OutputControl if e.staticValue.isDefined => None
        case e: AttributeControl                         => analyzeXPath(e, e.getChildrenContext, e.attributeValue, avt = true).some
        case e: VariableAnalysisTrait =>

          valueOrSequenceElement(e.element) match {
            case Some(valueElement) =>
              // Value is provided by nested `xxf:value`

              val valueElementAnalysis =
                new ElementAnalysis(e.part, e.index /* wrong */, valueElement, e.some, None, e.valueScope) {

                  nestedSelf =>

                  // If in same scope as xf:var, in-scope variables are the same as xxf:var because we don't
                  // want the variable defined by xf:var to be in-scope for xxf:value. Otherwise, use
                  // default algorithm.

                  // TODO: This is bad architecture as we duplicate the logic in ViewTrait.
                  override lazy val inScopeVariables: Map[String, VariableTrait] =
                    if (e.scope == nestedSelf.scope)
                      e.inScopeVariables
                    else
                      getRootVariables ++ nestedSelf.treeInScopeVariables

                  override protected def getRootVariables: Map[String, VariableAnalysisTrait] = e match {
                    case _: ViewTrait => nestedSelf.model match { case Some(model) => model.variablesMap; case None => Map() }
                    case _            => Map.empty
                  }
                }

              ElementAnalysisTreeXPathAnalyzer.analyzeXPath(valueElementAnalysis)

              // Custom value analysis done here
              val valueXPathAnalysis =
                VariableAnalysis.valueOrSelectAttribute(valueElement) match {
                  case Some(value) =>
                    analyzeXPath(valueElementAnalysis, valueElementAnalysis.getChildrenContext, value).some
                  case None =>
                    Some(StringAnalysis()) // TODO: store constant value?
                }

              valueXPathAnalysis

            case None =>
              // No nested `xxf:value` element

              valueOrSelectAttribute(e.element) match {
                case Some(value) => analyzeXPath(e, e.getChildrenContext, value).some
                case _           => StringAnalysis().some // TODO: store constant value?
              }
          }
        case e: ValueTrait =>
          val subExpression =
            if (e.value.isDefined) "string((" + e.value.get + ")[1])" else "string(.)" // TODO: function to create `string()`
          analyzeXPath(e, e.getChildrenContext, subExpression).some
        case _ =>
          None
      }

    def analyzeBindsXPath(bindTree: BindTree): Unit = {
      // Analyze all binds and return whether all of them were successfully analyzed
      bindTree.figuredAllBindRefAnalysis = (bindTree.topLevelBinds map (analyzeXPathGather(bindTree, _))).foldLeft(true)(_ && _)

      // Analyze all MIPs
      // NOTE: Do this here, because MIPs can depend on bind/@name, which requires all bind/@ref to be analyzed first
      bindTree.topLevelBinds foreach (analyzeBindMips(bindTree, _))

      if (! bindTree.figuredAllBindRefAnalysis) {
        bindTree.bindInstances.clear()
        bindTree.computedBindExpressionsInstances.clear()
        bindTree.validationBindInstances.clear()
        // keep bindAnalysis as those can be used independently from each other
      }

      if (bindTree.model.part.staticState.isCalculateDependencies) {
        bindTree.recalculateOrder  = Some(DependencyAnalyzer.determineEvaluationOrder(bindTree, ModelDefs.Calculate))
        bindTree.defaultValueOrder = Some(DependencyAnalyzer.determineEvaluationOrder(bindTree, ModelDefs.Default))
      }
    }

    def analyzeBindMips(bindTree: BindTree, bind: StaticBind): Unit = {
      // Analyze local MIPs if there is a @ref
      bind.ref match {
        case Some(_) =>
          for (mips <- bind.allMIPNameToXPathMIP.values; mip <- mips)
            analyzeMip(bindTree, bind, mip)
        case None =>
      }

      // Analyze descendants
      bind.children foreach (analyzeBindMips(bindTree, _))
    }

    def analyzeMip(bindTree: BindTree, bind: StaticBind, mip: StaticBind#XPathMIP): Unit = {

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
      analyzeXPathWithCompiledExpression(bind, bind.getChildrenContext, allBindVariablesInScope, mip.compiledExpression) match {
        case valueAnalysis if valueAnalysis.figuredOutDependencies => mip.analysis = valueAnalysis
        case _ => // NOP
      }
    }

    // Return true if analysis succeeded
    def analyzeXPathGather(bindTree: BindTree, bind: StaticBind): Boolean = {

      // Analyze context/binding
      ElementAnalysisTreeXPathAnalyzer.analyzeXPath(bind)

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
      val childrenSucceeded = (bind.children map (analyzeXPathGather(bindTree, _))).forall(identity)

      // Result
      refSucceeded && childrenSucceeded
    }

    def computeItemsetAnalysis(e: SelectionControlTrait): Option[XPathAnalysis] = {

      // TODO: operate on nested ElementAnalysis instead of Element

      var combinedAnalysis: XPathAnalysis = StringAnalysis()

      e.element.visitDescendants(
        new VisitorListener {

          var stack: List[ElementAnalysis] = e :: Nil

          def startElement(element: Element): Unit = {

            // Make lazy as might not be used
            lazy val itemElementAnalysis =
              new ElementAnalysis(e.part, e.index /* wrong */, element, stack.head.some, None, stack.head.getChildElementScope(element))
                with ValueTrait with OptionalSingleNode with ViewTrait

            def processElement(qName: QName, required: Boolean): Unit = {

              val nestedElementOpt = element.elementOpt(qName)

              if (required)
                require(nestedElementOpt.isDefined)

              nestedElementOpt foreach { nestedElement =>
                val nestedAnalysis = new LHHAAnalysis( // TODO: Weird! This is not an LHHA analysis.
                  part      = e.part,
                  index     = e.index, // wrong
                  element   = nestedElement,
                  parent    = Some(itemElementAnalysis),
                  preceding = None,
                  scope     = itemElementAnalysis.getChildElementScope(nestedElement)
                )
                ElementAnalysisTreeXPathAnalyzer.analyzeXPath(nestedAnalysis)
                combinedAnalysis = combinedAnalysis combine nestedAnalysis.valueAnalysis.get
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
                ElementAnalysisTreeXPathAnalyzer.analyzeXPath(itemElementAnalysis)
                combinedAnalysis = combinedAnalysis combine itemElementAnalysis.bindingAnalysis.get.makeValuesDependencies

                processElement(LABEL_QNAME, required = true)
                processElement(XFORMS_VALUE_QNAME, required = false)
                processElement(XFORMS_COPY_QNAME, required = false)

                if (e.isFull) {
                  processElement(HELP_QNAME, required = false)
                  processElement(HINT_QNAME, required = false)
                }

              case XFORMS_CHOICES_QNAME =>

                // Analyze container and add as a value dependency (see above)
                ElementAnalysisTreeXPathAnalyzer.analyzeXPath(itemElementAnalysis)
                combinedAnalysis = combinedAnalysis combine itemElementAnalysis.bindingAnalysis.get.makeValuesDependencies

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

    def analyzeXPath(
      e               : ElementAnalysis,
      contextAnalysis : Option[XPathAnalysis],
      xpathString     : String,
      avt             : Boolean = false
    ): XPathAnalysis =
      analyzeXPathWithStringExpression(e, contextAnalysis, e.inScopeVariables, xpathString, avt)

    def analyzeXPathWithStringExpression(
      e                : ElementAnalysis,
      contextAnalysis  : Option[XPathAnalysis],
      inScopeVariables : Map[String, VariableTrait],
      xpathString      : String,
      avt              : Boolean
    ): XPathAnalysis =
      PathMapXPathAnalysis(
        partAnalysis              = e.part,
        xpathString               = xpathString,
        namespaceMapping          = e.part.metadata.getNamespaceMapping(e.prefixedId).orNull,
        baseAnalysis              = contextAnalysis,
        inScopeVariables          = inScopeVariables,
        pathMapContext            = new SimplePathMapContext(e),
        scope                     = e.scope,
        defaultInstancePrefixedId = e.model flatMap (_.defaultInstancePrefixedId),
        locationData              = e.locationData,
        element                   = e.element,
        avt                       = avt
      )(e.logger)

    def analyzeXPathWithCompiledExpression(
      e                : ElementAnalysis,
      contextAnalysis  : Option[XPathAnalysis],
      inScopeVariables : Map[String, VariableTrait],
      expression       : CompiledExpression
    ): XPathAnalysis = {
      val defaultInstancePrefixedId = e.model flatMap (_.defaultInstancePrefixedId)
      PathMapXPathAnalysis(
        partAnalysis              = e.part,
        compiledExpression        = expression,
        baseAnalysis              = contextAnalysis,
        inScopeVariables          = inScopeVariables,
        pathMapContext            = new SimplePathMapContext(e),
        scope                     = e.scope,
        defaultInstancePrefixedId = defaultInstancePrefixedId,
        element                   = e.element
      )
    }
  }
}