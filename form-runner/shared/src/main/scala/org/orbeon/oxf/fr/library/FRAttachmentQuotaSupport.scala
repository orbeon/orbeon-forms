/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fr.library

import org.orbeon.datatypes.{MaximumFiles, MaximumSize}
import org.orbeon.datatypes.MaximumFiles.{LimitedFiles, UnlimitedFiles}
import org.orbeon.datatypes.MaximumSize.{LimitedSize, UnlimitedSize}
import org.orbeon.oxf.fr.{FormRunner, FormRunnerUploadSupport}
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{ByteSizeUtils, Mediatypes}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsProperties.*
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunctionNames
import org.orbeon.oxf.xforms.model.{BindVariableResolver, InstanceData, XFormsModel}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.SimplePath
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.XFormsId

import scala.util.Try


object FRAttachmentQuotaSupport {

  import Private.*

  case class FormQuota(
    maxSize      : MaximumSize,
    currentSizeOpt: Option[Long],
    remainingSize: MaximumSize
  )

  case class ControlQuota(
    isMultiple              : Boolean,
    maxSizePerFile          : MaximumSize,
    explicitMaxSizePerFileOpt: Option[Long],
    maxSizeAggregateControl : MaximumSize,
    currentSizeControl      : Long,
    remainingSizeControl    : MaximumSize,
    formQuota               : FormQuota,
    effectiveRemainingSize  : MaximumSize,
    currentFiles            : Int,
    maxFiles                : MaximumFiles,
    remainingFilesOpt       : Option[Int],
    minFilesOpt             : Option[Int],
    mediatypesOpt           : Option[String]
  )

  def formQuota(xfcd: XFormsContainingDocument): FormQuota = {
    val maxSize        = xfcd.staticState.uploadMaxSizeAggregatePerForm
    val currentSizeOpt = xfcd.getUploadChecker.currentUploadSizeAggregateForForm
    val remainingSize  =
      maxSize match {
        case LimitedSize(max) => LimitedSize(math.max(0L, max - currentSizeOpt.getOrElse(0L)))
        case UnlimitedSize    => UnlimitedSize
      }
    FormQuota(maxSize, currentSizeOpt, remainingSize)
  }

  def formMaxSizeOpt(xfcd: XFormsContainingDocument): Option[Long] =
    formQuota(xfcd).maxSize match {
      case LimitedSize(max) => Some(max)
      case UnlimitedSize    => None
    }

  def formCurrentSizeOpt(xfcd: XFormsContainingDocument): Option[Long] =
    xfcd.getUploadChecker.currentUploadSizeAggregateForForm

  def formRemainingSizeOpt(xfcd: XFormsContainingDocument): Option[Long] =
    formQuota(xfcd).remainingSize match {
      case LimitedSize(rem) => Some(rem)
      case UnlimitedSize    => None
    }

  def resolveAttachmentTarget(
    targetOpt        : Option[Item],
    isMultipleHintOpt: Option[Boolean]
  )(implicit
    xpc              : XPathContext,
    xfc              : XFormsFunction.Context
  ): Option[(NodeInfo, Boolean)] = {

    val boundNodeOpt: Option[NodeInfo] =
      targetOpt match {
        case Some(nodeInfo: NodeInfo) =>
          Some(nodeInfo)
        case Some(sv: StringValue) =>
          val controlName = sv.getStringValue.trimAllToEmpty
          if (controlName.nonEmpty)
            resolveControlNameTarget(controlName)
          else
            None
        case Some(_) =>
          None
        case None =>
          Option(xpc.getContextItem).collect { case n: NodeInfo => n }
      }

    boundNodeOpt.map { boundNode =>
      val isMultiple =
        isMultipleHintOpt.getOrElse(inferIsMultiple(xfc.containingDocument, boundNode))
      (boundNode, isMultiple)
    }
  }

  def controlQuota(
    xfcd      : XFormsContainingDocument,
    boundNode : NodeInfo,
    isMultiple: Boolean
  ): ControlQuota = {

    val staticState = xfcd.staticState

    val mipPerFileOpt =
      findUploadCustomMip(xfcd, boundNode, ValidationFunctionNames.UploadMaxSizePerFile)
        .flatMap(MaximumSize.unapply)

    val staticExplicitPerFileOpt =
      (staticState.staticStringProperty(UploadMaxSizeProperty).trimAllToOpt orElse
       staticState.staticStringProperty(UploadMaxSizePerFileProperty).trimAllToOpt)
        .flatMap(MaximumSize.unapply)

    val maxSizePerFile =
      mipPerFileOpt.getOrElse(staticState.uploadMaxSizePerFile)

    val explicitMaxSizePerFileOpt =
      (mipPerFileOpt orElse staticExplicitPerFileOpt).collect {
        case LimitedSize(bytes) => bytes
      }

    val rawMaxSizeAggregateControl =
      findUploadCustomMip(xfcd, boundNode, ValidationFunctionNames.UploadMaxSizeAggregatePerControl)
        .flatMap(MaximumSize.unapply)
        .getOrElse(staticState.uploadMaxSizeAggregatePerControl)

    val maxSizeAggregateControl =
      if (isMultiple) rawMaxSizeAggregateControl else UnlimitedSize

    val currentSizeControl   = FormRunnerUploadSupport.attachmentSizeForBoundNode(boundNode)
    val remainingSizeControl = maxSizeAggregateControl.minus(currentSizeControl)

    val fq                     = formQuota(xfcd)
    val effectiveRemainingSize = remainingSizeControl min fq.remainingSize

    val currentFiles = FormRunnerUploadSupport.attachmentCountForBoundNode(boundNode, isMultiple)

    val maxFiles =
      if (isMultiple)
        findUploadCustomMip(xfcd, boundNode, ValidationFunctionNames.UploadMaxFilesPerControl)
          .flatMap(MaximumFiles.unapply)
          .orElse(
            staticState.staticStringProperty(UploadMaxFilesPerControlProperty)
              .trimAllToOpt
              .flatMap(MaximumFiles.unapply)
          )
          .getOrElse(UnlimitedFiles)
      else
        UnlimitedFiles

    val remainingFilesOpt =
      maxFiles match {
        case LimitedFiles(max) => Some(math.max(0, max - currentFiles))
        case UnlimitedFiles    => None
      }

    val minFilesOpt =
      if (isMultiple)
        findUploadCustomMip(xfcd, boundNode, ValidationFunctionNames.UploadMinFilesPerControl)
          .flatMap(_.trimAllToOpt)
          .flatMap(_.toIntOption)
          .filter(_ >= 1)
      else
        None

    val mediatypesOpt =
      findUploadCustomMip(xfcd, boundNode, ValidationFunctionNames.UploadMediatypes)
        .flatMap(_.trimAllToOpt)
        .orElse(staticState.staticStringProperty(UploadMediatypesProperty).trimAllToOpt)
        .filter(m => m != "*/*")

    ControlQuota(
      isMultiple               = isMultiple,
      maxSizePerFile           = maxSizePerFile,
      explicitMaxSizePerFileOpt = explicitMaxSizePerFileOpt,
      maxSizeAggregateControl  = maxSizeAggregateControl,
      currentSizeControl       = currentSizeControl,
      remainingSizeControl     = remainingSizeControl,
      formQuota                = fq,
      effectiveRemainingSize   = effectiveRemainingSize,
      currentFiles             = currentFiles,
      maxFiles                 = maxFiles,
      remainingFilesOpt        = remainingFilesOpt,
      minFilesOpt              = minFilesOpt,
      mediatypesOpt            = mediatypesOpt
    )
  }

  def automaticHint(
    xfcd      : XFormsContainingDocument,
    boundNode : NodeInfo,
    isMultiple: Boolean
  )(implicit
    xfc       : XFormsFunction.Context
  ): Option[String] = {

    val q = controlQuota(xfcd, boundNode, isMultiple)

    def frResource(key: String, default: String): String =
      FRResourceSupport.r(key, Some("|fr-fr-resources|"), xfcd.rFallbackLang).getOrElse(default)

    val smallestAggregateOpt: Option[Long] =
      List(q.maxSizeAggregateControl, q.formQuota.maxSize)
        .collect { case LimitedSize(bytes) => bytes }
        .minOption

    val perFileHintOpt: Option[String] =
      q.explicitMaxSizePerFileOpt
        .filter(maxPerFile => ! smallestAggregateOpt.exists(_ <= maxPerFile))
        .map { maxPerFile =>
          formatMessage(
            frResource("detail.hints.upload-max-size-per-file", "Files must be under {0}."),
            List(ByteSizeUtils.byteCountToCompactDisplaySize(maxPerFile))
          )
        }

    val controlAggregateHintOpt: Option[String] =
      q.maxSizeAggregateControl match {
        case LimitedSize(maxCtrl) =>
          val maxFormatted = ByteSizeUtils.byteCountToCompactDisplaySize(maxCtrl)
          if (q.currentSizeControl > 0L) {
            val remBytes     = math.max(0L, maxCtrl - q.currentSizeControl)
            val remFormatted = ByteSizeUtils.byteCountToCompactDisplaySize(remBytes)
            Some(
              formatMessage(
                frResource(
                  "detail.hints.upload-max-size-aggregate-per-control-remaining",
                  "Total file size in control must be under {0} ({1} remaining)."
                ),
                List(maxFormatted, remFormatted)
              )
            )
          } else {
            Some(
              formatMessage(
                frResource(
                  "detail.hints.upload-max-size-aggregate-per-control",
                  "Total file size in control must be under {0}."
                ),
                List(maxFormatted)
              )
            )
          }
        case UnlimitedSize =>
          None
      }

    val formAggregateHintOpt: Option[String] =
      q.formQuota.maxSize match {
        case LimitedSize(maxForm) =>
          val maxFormatted = ByteSizeUtils.byteCountToCompactDisplaySize(maxForm)
          val usedForm     = q.formQuota.currentSizeOpt.getOrElse(0L)
          if (usedForm > 0L) {
            val remBytes     = math.max(0L, maxForm - usedForm)
            val remFormatted = ByteSizeUtils.byteCountToCompactDisplaySize(remBytes)
            Some(
              formatMessage(
                frResource(
                  "detail.hints.upload-max-size-aggregate-per-form-remaining",
                  "Total file size in form must be under {0} ({1} remaining)."
                ),
                List(maxFormatted, remFormatted)
              )
            )
          } else {
            Some(
              formatMessage(
                frResource(
                  "detail.hints.upload-max-size-aggregate-per-form",
                  "Total file size in form must be under {0}."
                ),
                List(maxFormatted)
              )
            )
          }
        case UnlimitedSize =>
          None
      }

    val maxFilesHintOpt: Option[String] =
      q.maxFiles match {
        case LimitedFiles(maxF) =>
          if (q.currentFiles > 0) {
            val remF = math.max(0, maxF - q.currentFiles)
            Some(
              formatMessage(
                frResource(
                  "detail.hints.upload-max-files-per-control-remaining",
                  "You can attach up to {0} files ({1} remaining)."
                ),
                List(maxF.toString, remF.toString)
              )
            )
          } else {
            Some(
              formatMessage(
                frResource("detail.hints.upload-max-files-per-control", "You can attach up to {0} files."),
                List(maxF.toString)
              )
            )
          }
        case UnlimitedFiles =>
          None
      }

    val minFilesHintOpt: Option[String] =
      q.minFilesOpt.map { minF =>
        formatMessage(
          frResource("detail.hints.upload-min-files-per-control", "You must attach at least {0} files."),
          List(minF.toString)
        )
      }

    val mediatypesHintOpt: Option[String] =
      q.mediatypesOpt.flatMap { mediatypesStr =>
        val labels = displayMediatypeLabels(mediatypesStr, frResource)
        labels.nonEmpty.option(
          formatMessage(
            frResource("detail.hints.upload-mediatypes", "Only {0} files are accepted."),
            List(labels.mkString(", "))
          )
        )
      }

    val parts =
      List(
        perFileHintOpt,
        controlAggregateHintOpt,
        formAggregateHintOpt,
        maxFilesHintOpt,
        minFilesHintOpt,
        mediatypesHintOpt
      ).flatten

    parts.nonEmpty.option(parts.mkString(" "))
  }

  def quotaSummary(
    xfcd      : XFormsContainingDocument,
    boundNode : NodeInfo,
    isMultiple: Boolean
  )(implicit
    xfc       : XFormsFunction.Context
  ): Option[String] = {

    val q = controlQuota(xfcd, boundNode, isMultiple)

    def frResource(key: String, default: String): String =
      FRResourceSupport.r(key, Some("|fr-fr-resources|"), xfcd.rFallbackLang).getOrElse(default)

    // Pick the binding aggregate quota (tightest remaining between control aggregate and form aggregate)
    val bindingAggregateOpt: Option[(Long, Long, Long)] = {
      val ctrlOpt =
        q.maxSizeAggregateControl match {
          case LimitedSize(maxCtrl) =>
            val remCtrl = math.max(0L, maxCtrl - q.currentSizeControl)
            Some((q.currentSizeControl, maxCtrl, remCtrl))
          case UnlimitedSize =>
            None
        }
      val formOpt =
        q.formQuota.maxSize match {
          case LimitedSize(maxForm) =>
            val usedForm = q.formQuota.currentSizeOpt.getOrElse(0L)
            val remForm  = math.max(0L, maxForm - usedForm)
            Some((usedForm, maxForm, remForm))
          case UnlimitedSize =>
            None
        }
      (ctrlOpt, formOpt) match {
        case (Some(c), Some(f)) => if (c._3 <= f._3) Some(c) else Some(f)
        case (Some(c), None)    => Some(c)
        case (None,    Some(f)) => Some(f)
        case (None,    None)    => None
      }
    }

    val aggregateSizePartOpt: Option[String] =
      bindingAggregateOpt.map { case (used, max, rem) =>
        if (used > 0L)
          formatMessage(
            frResource("detail.attachment-quota.size-remaining", "{0} of {1} remaining"),
            List(ByteSizeUtils.byteCountToCompactDisplaySize(rem), ByteSizeUtils.byteCountToCompactDisplaySize(max))
          )
        else
          formatMessage(
            frResource("detail.attachment-quota.size-available", "{0} available"),
            List(ByteSizeUtils.byteCountToCompactDisplaySize(max))
          )
      }

    val perFilePartOpt: Option[String] =
      q.explicitMaxSizePerFileOpt.flatMap { maxPerFile =>
        val perFileFormatted = ByteSizeUtils.byteCountToCompactDisplaySize(maxPerFile)
        bindingAggregateOpt match {
          case None =>
            if (isMultiple)
              Some(
                formatMessage(
                  frResource("detail.attachment-quota.max-per-file", "Max {0} per file"),
                  List(perFileFormatted)
                )
              )
            else
              Some(
                formatMessage(
                  frResource("detail.attachment-quota.max-size", "Max {0}"),
                  List(perFileFormatted)
                )
              )
          case Some((_, maxAgg, remAgg)) =>
            if (isMultiple && maxPerFile < maxAgg)
              Some(
                formatMessage(
                  frResource("detail.attachment-quota.max-per-file", "Max {0} per file"),
                  List(perFileFormatted)
                )
              )
            else if (! isMultiple && maxPerFile < remAgg)
              Some(
                formatMessage(
                  frResource("detail.attachment-quota.max-size", "Max {0}"),
                  List(perFileFormatted)
                )
              )
            else
              None
        }
      }

    val filesPartOpt: Option[String] =
      q.maxFiles match {
        case LimitedFiles(maxF) =>
          if (q.currentFiles > 0) {
            val remF = math.max(0, maxF - q.currentFiles)
            Some(
              formatMessage(
                frResource("detail.attachment-quota.files-remaining", "{0} of {1} files remaining"),
                List(remF.toString, maxF.toString)
              )
            )
          } else {
            Some(
              formatMessage(
                frResource("detail.attachment-quota.files-available", "Up to {0} files"),
                List(maxF.toString)
              )
            )
          }
        case UnlimitedFiles =>
          None
      }

    val parts = List(aggregateSizePartOpt, perFilePartOpt, filesPartOpt).flatten
    parts.nonEmpty.option(parts.mkString(" · "))
  }

  private object Private {

    def formatMessage(template: String, args: Seq[String]): String = {
      val unescaped = template.replace("''", "'")
      args.zipWithIndex.foldLeft(unescaped) { case (acc, (arg, idx)) =>
        acc.replace(s"{$idx}", arg)
      }
    }

    def resolveControlNameTarget(controlName: String)(implicit xfc: XFormsFunction.Context): Option[NodeInfo] = {
      val fromControlsOpt =
        Option(xfc.sourceEffectiveId)
          .flatMap(_.trimAllToOpt)
          .flatMap { sourceEffId =>
            Try(
              FormRunner.resolveTargetRelativeToActionSourceOpt(
                actionSourceAbsoluteId  = XFormsId.effectiveIdToAbsoluteId(sourceEffId),
                targetControlName       = controlName,
                followIndexes           = true,
                libraryOrSectionNameOpt = None
              )
            ).toOption.flatten
          }
          .flatMap(_.collectFirst { case n: NodeInfo => n })

      fromControlsOpt.orElse {
        for {
          model            <- xfc.modelOpt.orElse(xfc.containingDocument.findObjectByEffectiveId("fr-form-model").collect { case m: XFormsModel => m })
          modelBinds       <- model.modelBindsOpt
          targetStaticBind <- model.staticModel.bindsById.get(FormRunner.bindId(controlName))
          itemsIt          <- BindVariableResolver.resolveClosestBind(modelBinds, xfc.bindNodeOpt, targetStaticBind)
          node             <- itemsIt.collectFirst { case n: NodeInfo => n }
        } yield node
      }
    }

    def findUploadCustomMip(
      xfcd                  : XFormsContainingDocument,
      boundNode             : NodeInfo,
      validationFunctionName: String
    ): Option[String] =
      ValidationFunctionNames.oldName(validationFunctionName)
        .flatMap(InstanceData.findCustomMip(boundNode, _))
        .orElse(InstanceData.findCustomMip(boundNode, validationFunctionName))
        .orElse(findStaticConstraintLiteral(xfcd, boundNode, validationFunctionName))

    def findStaticConstraintLiteral(
      xfcd                  : XFormsContainingDocument,
      boundNode             : NodeInfo,
      validationFunctionName: String
    ): Option[String] = {
      val namesToCheck =
        ValidationFunctionNames.oldName(validationFunctionName).toList :+ validationFunctionName

      val modelOpt =
        xfcd.findObjectByEffectiveId("fr-form-model").collect { case m: XFormsModel => m }
          .orElse(xfcd.findDefaultModel)

      (for {
        model      <- modelOpt.iterator
        staticBind <- model.staticModel.bindsById.get(FormRunner.bindId(boundNode.localname)).iterator
        mip        <- staticBind.constraintsByLevel.valuesIterator.flatten
        fnName     <- namesToCheck.iterator
        value      <- extractLiteralUploadConstraintArg(mip.compiledExpression.string, fnName).iterator
      } yield value).nextOption()
    }

    def extractLiteralUploadConstraintArg(expr: String, fnName: String): Option[String] = {
      val pattern = s""":\\Q$fnName\\E\\s*\\(\\s*(?:'([^']*)'|"([^"]*)"|([+-]?\\d+))\\s*\\)""".r
      pattern.findFirstMatchIn(expr).flatMap { m =>
        Option(m.group(1)).orElse(Option(m.group(2))).orElse(Option(m.group(3)))
      }
    }

    def inferIsMultiple(xfcd: XFormsContainingDocument, boundNode: NodeInfo): Boolean = {
      def fromControlOpt: Option[Boolean] =
        xfcd.controls.getCurrentControlTree.effectiveIdsToControls.valuesIterator
          .collect {
            case c: XFormsComponentControl
              if c.isRelevant &&
                 c.boundNodeOpt.contains(boundNode) &&
                 c.staticControl.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment")) =>
              c.staticControl.attributeValueOpt("multiple").contains("true")
          }
          .nextOption()

      def fromStaticControlOpt: Option[Boolean] = {
        val expectedControlId = FormRunner.controlId(boundNode.localname)
        val expectedBindId    = FormRunner.bindId(boundNode.localname)
        xfcd.staticState.topLevelPart.iterateControls
          .collectFirst {
            case c
              if (c.staticId == expectedControlId || c.bind.contains(expectedBindId)) &&
                 c.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment")) =>
              c.attributeValueOpt("multiple").contains("true")
          }
      }

      fromControlOpt
        .orElse(fromStaticControlOpt)
        .getOrElse(boundNode.child(SimplePath.*).exists(_.localname == "_"))
    }

    def displayMediatypeLabels(
      mediatypesStr: String,
      frResource   : (String, String) => String
    ): List[String] =
      mediatypesStr
        .splitTo[List](", ")
        .filter(m => m.nonEmpty && m != "*/*")
        .flatMap { mediatype =>
          def labelFromKnownMediatypes: Option[String] =
            Mediatypes.findLabelForMediatype(mediatype)

          def labelFromMediaStar: Option[String] =
            if (mediatype.endsWith("/*")) {
              val prefix = mediatype.substring(0, mediatype.length - 2)
              Set("image", "video", "audio")(prefix).option(
                frResource(s"detail.hints.upload-$prefix", prefix)
              )
            } else
              None

          def labelUsingHeuristic: Option[String] = {
            val slashPosition = mediatype.indexOf('/')
            if (slashPosition >= 0 && slashPosition + 1 < mediatype.length) {
              val mediatypeRight = mediatype.substring(slashPosition + 1)
              val beforePlus     = mediatypeRight.substringBeforeOpt("+").getOrElse(mediatypeRight)
              Some(beforePlus.toUpperCase)
            } else
              None
          }

          labelFromKnownMediatypes orElse labelFromMediaStar orElse labelUsingHeuristic
        }
        .distinct
  }
}
