/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.processor.pipeline.choose

import org.apache.commons.collections4.CollectionUtils
import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.cache.OutputCacheKey
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.{LoggerFactory, StaticXPath, XPath, XPathCache}
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.xml.NamespaceMapping
import org.slf4j.Logger

import java.util
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*
import scala.util.control.NonFatal


object ConcreteChooseProcessor {

  val logger: Logger = LoggerFactory.createLoggerJava(classOf[ConcreteChooseProcessor])

  private class State {
    var started = false
    var selectedBranchOutputs: Map[String, ProcessorOutput] = _
  }
}

/**
 * @param _branchConditions XPath expression for each branch (except the optimal last `<otherwise>`)
 * @param branchNamespaces  namespaces declared in the context of the given XPath expression
 * @param branchProcessors  one for each branch
 * @param inputs            all the ids possibly referenced by a processor in any branch
 * @param outputsById       outputs of the `<choose>` referenced by other processor
 * @param outputsByParamRef outputs of the `<choose>` referencing pipeline outputs
 */
class ConcreteChooseProcessor(
  id                           : String, // Created when constructed
  private val locationData     : LocationData,
  private val _branchConditions: util.List[String],// `<p:otherwise>` branch has `null` condition
  private val branchNamespaces : util.List[NamespaceMapping],
  private val branchProcessors : util.List[Processor],
  inputs                       : util.Set[String],
  private val outputsById      : util.Set[String],
  private val outputsByParamRef: util.Set[String]
) extends ProcessorImpl {

  thisConcreteChooseProcessor =>

  import ConcreteChooseProcessor.*

  setId(id)

  // Add inputs
  addInputInfo(new ProcessorInputOutputInfo(AbstractChooseProcessor.CHOOSE_DATA_INPUT))
  inputs.asScala.foreach { name =>
    addInputInfo(new ProcessorInputOutputInfo(name))
  }

  // Add outputs
  CollectionUtils.union(outputsById, outputsByParamRef).asScala.foreach { name =>
    addOutputInfo(new ProcessorInputOutputInfo(name))
  }

  private val branchInputs: List[List[(String, ProcessorInput)]] =
    branchProcessors.asScala
      .view
      .map { processor =>
        inputs.asScala.view
          .map(inputName => inputName -> processor.createInput(inputName))
          .toList
      }
      .toList

  private val branchOutputs: List[Map[String, ProcessorOutput]] =
    branchProcessors.asScala
      .view
      .map { processor =>
        CollectionUtils.union(outputsById, outputsByParamRef).asScala
          .view
          .map(outputName => outputName -> processor.createOutput(outputName))
          .toMap
      }
      .toList

  private val branchConditions: Iterable[Option[String]] =
    _branchConditions.asScala.map(Option.apply).toList


  /**
   * Those outputs that must be connected to an outer pipeline output
   */
  def getOutputsByParamRef: util.Set[String] = outputsByParamRef
  def getOutputsById      : util.Set[String] = outputsById

  private def getStateT(pipelineContext: PipelineContext): State =
    getState(pipelineContext).asInstanceOf[ConcreteChooseProcessor.State]

  private def stateOpt(pipelineContext: PipelineContext): Option[ConcreteChooseProcessor.State] =
    hasState(pipelineContext).option(getStateT(pipelineContext))

  override def createOutput(name: String): ProcessorOutput =
    new ProcessorOutputImpl(thisConcreteChooseProcessor, name) {

      override def readImpl(context: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
        val state = getStateT(context)
        if (! state.started)
          start(context)
        state.selectedBranchOutputs(name)
          .read(context, xmlReceiver)
      }

      override def getKeyImpl(pipelineContext: PipelineContext): OutputCacheKey =
        stateOpt(pipelineContext).filter(_.started).flatMap(_.selectedBranchOutputs.get(name)) match {
          case Some(processorOutput) => processorOutput.getKey(pipelineContext)
          case _ if isInputInCache(pipelineContext, AbstractChooseProcessor.CHOOSE_DATA_INPUT) =>
            val state = getStateT(pipelineContext)
            if (! state.started)
              start(pipelineContext)
            state.selectedBranchOutputs(name)
              .getKey(pipelineContext)
          case _ =>
            null
        }

      override protected def getValidityImpl(pipelineContext: PipelineContext): AnyRef =
        stateOpt(pipelineContext).filter(_.started).flatMap(_.selectedBranchOutputs.get(name)) match {
          case Some(processorOutput) => processorOutput.getValidity(pipelineContext)
          case _ if isInputInCache(pipelineContext, AbstractChooseProcessor.CHOOSE_DATA_INPUT) =>
            val state = getStateT(pipelineContext)
            if (! state.started)
              start(pipelineContext)
            state.selectedBranchOutputs(name)
              .getValidity(pipelineContext)
          case _ =>
            null
        }
    }
    .tap(addOutput(name, _))

  override def start(pipelineContext: PipelineContext): Unit = {

    val state = getStateT(pipelineContext)
    if (state.started)
      throw new IllegalStateException("ASTChoose Processor already started")

    // Choose which branch we want to run (we cache the decision)

    // Lazily read input in case there is only a p:otherwise
    lazy val hrefDocumentInfo =
      readCacheInputAsTinyTree(pipelineContext, XPath.GlobalConfiguration, AbstractChooseProcessor.CHOOSE_DATA_INPUT)

    val resultOpt =
      branchConditions.iterator.zipWithIndex.find {
        case (None, _) =>
          true
        case (Some(condition), branchIndex) =>
          try {
            val expression = // LATER: Try to cache the XPath expressions.
              XPathCache.getXPathExpression(
                configuration      = hrefDocumentInfo.getConfiguration,
                contextItem        = hrefDocumentInfo,
                xpathString        = StaticXPath.makeBooleanExpression(condition),
                namespaceMapping   = branchNamespaces.get(branchIndex),
                variableToValueMap = null,
                functionLibrary    = org.orbeon.oxf.pipeline.api.FunctionLibrary,
                baseURI            = null,
                locationData       = locationData // TODO: location should be that of branch
              )

            expression.evaluateSingleToJavaReturnToPoolOrNull.asInstanceOf[Boolean].booleanValue
          } catch {
            case NonFatal(e) =>
              if (logger.isDebugEnabled)
                logger.debug(s"Choose: condition evaluation failed for condition: `$condition` at $branchIndex") // TODO: location should be that of branch
              throw new ValidationException(s"Choose: condition evaluation failed for condition: `$condition`", e, locationData) // TODO: location should be that of branch
          }
      }

    resultOpt match {
      case None =>
        // No branch was selected: this is not acceptable if there are output to the choose
        if (! outputsById.isEmpty || ! outputsByParamRef.isEmpty)
          throw new ValidationException(s"Condition failed for every branch of choose: ${branchConditions.toString}", locationData)
      case Some((conditionOpt, selectedBranch)) =>

        val selectedBranchProcessor = branchProcessors.get(selectedBranch)
        state.selectedBranchOutputs = branchOutputs(selectedBranch)

        // Connect branch inputs
        branchInputs(selectedBranch).foreach { case (branchInputName, branchInput) =>
          branchInput.setOutput(getInputByName(branchInputName).getOutput)
        }

        // Connect branch outputs, or start processor
        selectedBranchProcessor.reset(pipelineContext)
        if (outputsById.size == 0 && outputsByParamRef.size == 0) {
          if (logger.isDebugEnabled) {
            // TODO: location should be that of branch
            conditionOpt match {
              case Some(condition) =>
                logger.debug(s"Choose: taking when branch with test: `$condition` at $locationData")
              case None =>
                logger.debug(s"Choose: taking otherwise branch at $locationData")
            }
          }
          selectedBranchProcessor.start(pipelineContext)
        }
        state.started = true
    }
  }

  override def reset(context: PipelineContext): Unit = {
    setState(context, new ConcreteChooseProcessor.State)
    branchProcessors.asScala
      .foreach(_.reset(context))
  }
}
