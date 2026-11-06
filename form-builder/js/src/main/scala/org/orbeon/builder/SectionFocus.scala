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
package org.orbeon.builder

import org.orbeon.builder.rpc.FormBuilderRpcClient
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{AjaxClient, XFormsId}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*


object SectionFocus {

  private val FocusHiddenClass     = "fb-section-focus-hidden"
  private val FocusBannerSelector  = ".fb-section-focus-banner"
  private val SectionLabelSelector = ".fr-section-label .btn-link, .fr-section-label .xforms-output-output"

  private var _focusedSectionStaticIdOpt: Option[String] = None

  def focusedSectionStaticIdOpt: Option[String] = _focusedSectionStaticIdOpt

  def isFocused(sectionEl: html.Element): Boolean =
    _focusedSectionStaticIdOpt.contains(XFormsId.getStaticIdFromId(sectionEl.id))

  def toggleFocus(sectionEl: html.Element): Unit =
    if (isFocused(sectionEl)) {
      val sectionId = sectionEl.id
      clearFocus()
      FormBuilderPrivateAPI.moveOutlineContainerIntoView(sectionId)
    } else {
      focusOnSection(sectionEl)
    }

  def focusOnSection(sectionEl: html.Element): Unit = {
    val sectionId = sectionEl.id
    val staticId  = XFormsId.getStaticIdFromId(sectionId)
    _focusedSectionStaticIdOpt = Some(staticId)
    syncDom()
    Position.notifyOffsetMayHaveChanged()
    FormBuilderRpcClient.api.sectionExpandAndSelect(sectionId).foreach { _ =>
      AjaxClient.allEventsProcessedF("focusOnSection").foreach { _ =>
        FormBuilderPrivateAPI.moveOutlineContainerIntoView(sectionId)
      }
    }
  }

  def clearFocus(): Unit =
    if (_focusedSectionStaticIdOpt.isDefined) {
      _focusedSectionStaticIdOpt = None
      syncDom()
      Position.notifyOffsetMayHaveChanged()
    }

  def ensureVisible(elem: html.Element): Unit =
    if (_focusedSectionStaticIdOpt.isDefined && elem.closestOpt(s".$FocusHiddenClass").isDefined)
      clearFocus()

  def syncDom(): Unit = {
    val allSections =
      dom.document
        .querySelectorAllT(s".fb-main .fb ${BlockCache.SectionSelector}")
        .filterNot(BlockCache.isElemInSectionTemplate)

    val focusedSectionOpt =
      _focusedSectionStaticIdOpt.flatMap { focusedStaticId =>
        allSections.find(s => XFormsId.getStaticIdFromId(s.id) == focusedStaticId)
      }

    focusedSectionOpt match {
      case Some(focusedSectionEl) =>
        val ancestorSections =
          focusedSectionEl.ancestorOrSelfElem(BlockCache.SectionSelector, includeSelf = true).toSet

        val descendantSections =
          focusedSectionEl.querySelectorAllT(BlockCache.SectionSelector).toSet

        val visibleSections = ancestorSections ++ descendantSections

        allSections.foreach { sectionEl =>
          sectionEl.classList.toggle(FocusHiddenClass, ! visibleSections.contains(sectionEl))
        }

        val allGrids =
          dom.document
            .querySelectorAllT(s".fb-main .fb ${BlockCache.GridSelector}")
            .filterNot(BlockCache.isElemInSectionTemplate)

        allGrids.foreach { gridEl =>
          gridEl.classList.toggle(FocusHiddenClass, ! focusedSectionEl.contains(gridEl))
        }

        dom.document.querySelectorOpt(FocusBannerSelector).foreach { bannerEl =>
          bannerEl.querySelectorOpt(".fb-section-focus-banner-title").foreach { titleEl =>
            titleEl.textContent = sectionTitleText(focusedSectionEl)
          }
          bannerEl.classList.remove("xforms-hidden")
        }

      case None =>
        _focusedSectionStaticIdOpt = None
        dom.document.querySelectorAllT(s".$FocusHiddenClass").foreach { el =>
          el.classList.remove(FocusHiddenClass)
        }
        dom.document.querySelectorOpt(FocusBannerSelector).foreach { bannerEl =>
          bannerEl.classList.add("xforms-hidden")
        }
    }
  }

  private def sectionTitleText(sectionEl: html.Element): String =
    sectionEl
      .querySelectorOpt(SectionLabelSelector)
      .flatMap(_.textContent.trimAllToOpt)
      .getOrElse {
        val staticId = XFormsId.getStaticIdFromId(sectionEl.id)
        staticId.stripSuffix("-section").stripSuffix("-control")
      }

  locally {
    dom.document.addEventListener(
      "click",
      (e: dom.Event) => {
        for {
          targetEl <- Option(e.target).collect { case el: html.Element => el }
          _        <- targetEl.closestOpt(".fb-section-focus-banner-exit")
        } locally {
          e.preventDefault()
          clearFocus()
        }
      }
    )

    dom.document.addEventListener(
      DomEventNames.KeyDown,
      (e: dom.KeyboardEvent) => {
        if (
          _focusedSectionStaticIdOpt.isDefined &&
          e.key == "Escape"                    &&
          dom.document.querySelectorAll("dialog[open]").length == 0
        ) {
          val isEditingInput =
            Option(dom.document.activeElement).collect { case el: html.Element => el }.exists { activeEl =>
              activeEl.matches("input, textarea, select, [contenteditable='true']")
            }
          if (! isEditingInput) {
            e.preventDefault()
            clearFocus()
          }
        }
      }
    )
  }
}
