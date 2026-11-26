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
package org.orbeon.oxf.fr

import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.properties.PropertyStore
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.controls.{XFormsGroupControl, XFormsOutputControl, XFormsTriggerControl}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.events.XXFormsUploadStoreEvent
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector}
import org.orbeon.oxf.xml.XMLConstants.{XS_BOOLEAN_QNAME, XS_INTEGER_QNAME}
import org.orbeon.oxf.xml.dom.IOSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.file.Files
import scala.jdk.CollectionConverters.*


class AttachmentQuotaTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  import Private.*

  describe("Issue #6829: Attachment quotas, hints, navbar indicator, and XPath functions") {

    it("must keep quota UI surfaces opt-in by default while honoring per-control show-quota and excluding form-definition attachments") {
      val (processorService, docOpt, _) =
        runFormRunner(
          app        = "issue",
          form       = "6829",
          mode       = "new",
          attributes = propertiesOverride(
            PropertyParams(Map.empty, "oxf.fr.detail.attachment.max-size-aggregate-per-form.issue.6829", XS_INTEGER_QNAME, "2097152"),
            PropertyParams(Map.empty, "oxf.fr.detail.attachment.max-files-per-control.issue.6829",       XS_INTEGER_QNAME, "3")
          )
        )

      assert(docOpt.isDefined)
      val doc: XFormsContainingDocument = docOpt.get

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {
          // `oxf.fr.detail.attachment.max-files-per-control` must propagate to `fr-form-model` as `xxf:upload.max-files-per-control`
          assert(doc.staticState.staticStringProperty("upload.max-files-per-control") == "3")

          // Static form-definition attachment (`/fr/service/persistence/crud/issue/6829/form/bg.png`, 512000 B) must be excluded (#6807)
          assert(outputValue("out-form-max-control")       == "2097152")
          assert(outputValue("out-form-size-control")      == "0")
          assert(outputValue("out-form-remaining-control") == "2097152")

          // Opt-in surfaces must be hidden/disabled by default
          assert(controlHint("single-doc-control").forall(_.isEmpty))
          assert(controlHint("multi-docs-control").forall(_.isEmpty))
          assert(findNavbarQuotaGroup.isEmpty)
          assert(findControlQuotaOutput("multi-docs-control").forall(! _.isRelevant))

          // Explicit `show-quota="true"` on `quota-docs-control` must render even when property default is false
          val explicitQuotaOut = findControlQuotaOutput("quota-docs-control")
          assert(explicitQuotaOut.exists(_.isRelevant))
          assert(explicitQuotaOut.get.getValue(EventCollector.Throw) == "2 MB available · Up to 2 files")

          // Custom hint using `<fr:param type="ExpressionParam">` with `fr:attachment-*` and `xxf:format-bytes` works regardless of automatic hints
          assert(controlHint("custom-hint-docs-control").contains("Space: 2 MB, slots: 3."))
        }
      }
    }

    it("must evaluate XPath functions and dynamically update automatic hints, control quota lines, template params, and navbar quota on upload and removal") {
      val attrs = propertiesOverride(
        PropertyParams(Map.empty, "oxf.fr.detail.attachment.max-size-aggregate-per-form.issue.6829", XS_INTEGER_QNAME, "2097152"),
        PropertyParams(Map.empty, "oxf.fr.detail.attachment.max-files-per-control.issue.6829",       XS_INTEGER_QNAME, "3"),
        PropertyParams(Map.empty, "oxf.fr.detail.hint.automatic.issue.6829",                         XS_BOOLEAN_QNAME, "true"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.attachment.show-quota.issue.6829",              XS_BOOLEAN_QNAME, "true"),
        PropertyParams(Map.empty, "oxf.fr.navbar.attachment-quota.enable.issue.6829",                XS_BOOLEAN_QNAME, "true")
      )

      val (processorService, docOpt, _) =
        runFormRunner(
          app        = "issue",
          form       = "6829",
          mode       = "new",
          attributes = attrs
        )

      assert(docOpt.isDefined)
      val doc: XFormsContainingDocument = docOpt.get

      withTestExternalContext { implicit ec =>
        attrs.foreach { case (k, v) => ec.getRequest.getAttributesMap.put(k, v) }
        withFormRunnerDocument(processorService, doc) {
          // 1. Initial state (0 bytes uploaded)
          assert(outputValue("out-format-bytes-control")              == "1.5 MB")
          assert(outputValue("out-format-empty-control")              == "")
          assert(outputValue("out-form-max-control")                  == "2097152")
          assert(outputValue("out-form-size-control")                 == "0")
          assert(outputValue("out-form-remaining-control")            == "2097152")
          assert(outputValue("out-single-per-file-node-control")      == "512000")
          assert(outputValue("out-single-per-file-name-control")      == "512000")
          assert(outputValue("out-multi-agg-control")                 == "1048576")
          assert(outputValue("out-multi-size-control")                == "0")
          assert(outputValue("out-multi-remaining-control")           == "1048576")
          assert(outputValue("out-multi-count-control")               == "0")
          assert(outputValue("out-multi-max-files-control")           == "3")
          assert(outputValue("out-multi-remaining-files-control")     == "3")
          assert(outputValue("out-unlimited-max-files-control")       == "")
          assert(outputValue("out-unlimited-remaining-files-control") == "")
          assert(outputValue("out-photo-min-files-control")           == "1")
          assert(outputValue("out-context-remaining-control")         == "1048576")

          // Navbar indicator
          assert(findNavbarQuotaGroup.exists(_.isRelevant))
          assert(findNavbarQuotaLabel.contains("2 MB / 2 MB left"))

          // Automatic hints across single attachment, multiple attachment, `-1` unlimited override, image-attachment, and combined custom hint
          assert(
            controlHint("single-doc-control").contains(
              "Files must be under 500 KB. Total file size in form must be under 2 MB. Only PDF files are accepted."
            )
          )
          assert(
            controlHint("multi-docs-control").contains(
              "Total file size in control must be under 1 MB. Total file size in form must be under 2 MB. You can attach up to 3 files."
            )
          )
          assert(
            controlHint("unlimited-files-docs-control").contains(
              "Total file size in form must be under 2 MB."
            )
          )
          assert(
            controlHint("photo-control").contains(
              "Total file size in form must be under 2 MB. You can attach up to 3 files. You must attach at least 1 files. Only image files are accepted."
            )
          )
          assert(
            controlHint("custom-hint-docs-control").contains(
              "Total file size in form must be under 2 MB. You can attach up to 3 files. Space: 2 MB, slots: 3."
            )
          )

          // Built-in `.fr-attachment-quota` on `<fr:attachment>` and `<fr:image-attachment>`
          assert(controlQuotaText("single-doc-control").contains("2 MB available · Max 500 KB"))
          assert(controlQuotaText("multi-docs-control").contains("1 MB available · Up to 3 files"))
          assert(controlQuotaText("unlimited-files-docs-control").contains("2 MB available"))
          assert(controlQuotaText("photo-control").contains("2 MB available · Up to 3 files"))

          // 2. Upload 409600 bytes (400 KB) to `multi-docs-control`
          simulateUploadBytes("multi-docs-control", "report.pdf", "application/pdf", sizeBytes = 409600)

          // XPath functions and `xxf:xpath-analysis="true"` outputs must refresh dynamically
          assert(outputValue("out-form-size-control")             == "409600")
          assert(outputValue("out-form-remaining-control")        == "1687552") // 2097152 - 409600 = 1.61 MB
          assert(outputValue("out-multi-size-control")            == "409600")
          assert(outputValue("out-multi-remaining-control")       == "638976")  // 1048576 - 409600 = 624 KB
          assert(outputValue("out-multi-count-control")           == "1")
          assert(outputValue("out-multi-remaining-files-control") == "2")
          assert(outputValue("out-context-remaining-control")     == "638976")

          // Navbar indicator updates live
          assert(findNavbarQuotaLabel.contains("1.61 MB / 2 MB left"))

          // Automatic hints update live on both the uploaded control and sibling controls sharing the form quota
          assert(
            controlHint("multi-docs-control").contains(
              "Total file size in control must be under 1 MB (624 KB remaining). Total file size in form must be under 2 MB (1.61 MB remaining). You can attach up to 3 files (2 remaining)."
            )
          )
          assert(
            controlHint("single-doc-control").contains(
              "Files must be under 500 KB. Total file size in form must be under 2 MB (1.61 MB remaining). Only PDF files are accepted."
            )
          )
          assert(
            controlHint("custom-hint-docs-control").contains(
              "Total file size in form must be under 2 MB (1.61 MB remaining). You can attach up to 3 files. Space: 1.61 MB, slots: 3."
            )
          )

          // Control `.fr-attachment-quota` lines update live
          assert(controlQuotaText("multi-docs-control").contains("624 KB of 1 MB remaining · 2 of 3 files remaining"))
          assert(controlQuotaText("photo-control").contains("1.61 MB of 2 MB remaining · Up to 3 files"))

          // 3. Remove the uploaded file from `multi-docs-control`
          val removeTriggers = findRemoveTriggers("multi-docs-control")
          assert(removeTriggers.size == 1)
          activateControlWithEvent(removeTriggers.head.effectiveId)

          assert(outputValue("out-form-size-control")             == "0")
          assert(outputValue("out-form-remaining-control")        == "2097152")
          assert(outputValue("out-multi-size-control")            == "0")
          assert(outputValue("out-multi-remaining-control")       == "1048576")
          assert(outputValue("out-multi-count-control")           == "0")
          assert(outputValue("out-multi-remaining-files-control") == "3")
          assert(findNavbarQuotaLabel.contains("2 MB / 2 MB left"))
          assert(controlQuotaText("multi-docs-control").contains("1 MB available · Up to 3 files"))
          assert(
            controlHint("multi-docs-control").contains(
              "Total file size in control must be under 1 MB. Total file size in form must be under 2 MB. You can attach up to 3 files."
            )
          )
        }
      }
    }

    it("must format localized French hints and quota summaries cleanly including single quotes") {
      val attrs = propertiesOverride(
        PropertyParams(Map.empty, "oxf.fr.detail.attachment.max-size-aggregate-per-form.issue.6829", XS_INTEGER_QNAME, "2097152"),
        PropertyParams(Map.empty, "oxf.fr.detail.attachment.max-files-per-control.issue.6829",       XS_INTEGER_QNAME, "3"),
        PropertyParams(Map.empty, "oxf.fr.detail.hint.automatic.issue.6829",                         XS_BOOLEAN_QNAME, "true"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.attachment.show-quota.issue.6829",              XS_BOOLEAN_QNAME, "true")
      )

      val (processorService, docOpt, _) =
        runFormRunner(
          app        = "issue",
          form       = "6829",
          mode       = "new",
          query      = List("fr-language" -> "fr"),
          attributes = attrs
        )

      assert(docOpt.isDefined)
      val doc: XFormsContainingDocument = docOpt.get

      withTestExternalContext { ec =>
        attrs.foreach { case (k, v) => ec.getRequest.getAttributesMap.put(k, v) }
        withFormRunnerDocument(processorService, doc) {
          val frHint = controlHint("multi-docs-control").getOrElse("")
          assert(frHint.contains("Vous pouvez joindre jusqu'à 3 fichiers."))
          assert(! frHint.contains("{0}"))

          simulateUploadBytes("multi-docs-control", "rapport.pdf", "application/pdf", sizeBytes = 409600)
          val frHintAfter = controlHint("multi-docs-control").getOrElse("")
          assert(frHintAfter.contains("Vous pouvez joindre jusqu'à 3 fichiers (2 restants)."))
          assert(controlQuotaText("multi-docs-control").contains("624 KB sur 1 MB restant · 2 sur 3 fichiers restants"))
        }
      }
    }

    it("must discover fr:attachment show-quota in FormRunnerConfiguration and round-trip boolean properties") {
      val catalog      = FormRunnerConfiguration.ControlSettingsSupport.currentCatalog()
      val attachDesc   = catalog.byQName("fr:attachment")
      val showQuotaDef = attachDesc.resolveSetting("show-quota")

      assert(showQuotaDef.isDefined)
      assert(showQuotaDef.get.xsType == "xs:boolean")

      val inputPropsXml =
        """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
          |  <property as="xs:boolean" name="oxf.xforms.xbl.fr.attachment.show-quota.*.*" value="true"/>
          |</properties>""".stripMargin

      val configDoc     = FormRunnerConfiguration.propertiesDomToConfigurationDom(IOSupport.readOrbeonDom(inputPropsXml))
      val globalAttach  = configDoc.getRootElement.element("control-settings").elements.find(_.getQualifiedName == "fr:attachment").get
      assert(globalAttach.attributeValue("show-quota") == "true")

      val roundTripDoc  = FormRunnerConfiguration.configurationDomToPropertiesDom(configDoc)
      val exportedElems = roundTripDoc.getRootElement.elements("property")
      assert(
        exportedElems.exists(e =>
          e.attributeValue("name")  == "oxf.xforms.xbl.fr.attachment.show-quota.*.*" &&
          e.attributeValue("as")    == "xs:boolean"                                  &&
          e.attributeValue("value") == "true"
        )
      )
    }
  }

  private object Private {

    def outputValue(controlId: String): String =
      resolveObject[XFormsValueControl](controlId).get.getValue(EventCollector.Throw)

    def controlHint(controlId: String): Option[String] =
      resolveObject[XFormsControl](controlId).get.getHint(EventCollector.Throw)

    def findControlQuotaOutput(controlId: String): Option[XFormsOutputControl] =
      resolveObject[XFormsComponentControl](controlId).flatMap { comp =>
        ControlsIterator(comp, includeSelf = false)
          .collectFirst {
            case g: XFormsGroupControl
              if g.staticControl.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment-quota")) => g
          }
          .flatMap { group =>
            ControlsIterator(group, includeSelf = false).collectFirst {
              case o: XFormsOutputControl => o
            }
          }
      }

    def controlQuotaText(controlId: String): Option[String] =
      findControlQuotaOutput(controlId).filter(_.isRelevant).map(_.getValue(EventCollector.Throw))

    def findNavbarQuotaGroup: Option[XFormsGroupControl] =
      document.controls.getCurrentControlTree.rootOpt.flatMap { root =>
        ControlsIterator(root, includeSelf = false).collectFirst {
          case g: XFormsGroupControl
            if g.staticControl.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-navbar-attachment-quota")) => g
        }
      }

    def findNavbarQuotaLabel: Option[String] =
      findNavbarQuotaGroup.filter(_.isRelevant).flatMap { group =>
        ControlsIterator(group, includeSelf = false).collectFirst {
          case o: XFormsOutputControl if o.isRelevant => o.getValue(EventCollector.Throw)
        }
      }

    def simulateUploadBytes(
      controlId: String,
      filename : String,
      mediatype: String,
      sizeBytes: Int
    ): Unit = {
      val bytes    = new Array[Byte](sizeBytes)
      val tempPath = Files.createTempFile("orbeon-quota-test-", ".tmp")
      tempPath.toFile.deleteOnExit()
      Files.write(tempPath, bytes)

      val comp = resolveObject[XFormsComponentControl](controlId).get
      val uploadControl =
        document.controls.getCurrentControlTree.getRelevantUploadControls
          .find(_.effectiveId.startsWith(s"${comp.effectiveId}≡"))
          .get

      withAction {
        Dispatch.dispatchEvent(
          new XXFormsUploadStoreEvent(
            uploadControl,
            Map(
              "file"                     -> Some(tempPath.toUri.toString),
              "filename"                 -> Some(filename),
              Headers.ContentTypeLower   -> Some(mediatype),
              Headers.ContentLengthLower -> Some(sizeBytes.toString)
            )
          ),
          EventCollector.Throw
        )
      }
    }

    def findRemoveTriggers(controlId: String): List[XFormsTriggerControl] = {
      val comp = resolveObject[XFormsComponentControl](controlId).get
      ControlsIterator(comp, includeSelf = false)
        .collect {
          case t: XFormsTriggerControl
            if t.isRelevant &&
               t.staticControl.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment-remove")) => t
        }
        .toList
        .sortBy(_.effectiveId)
    }
  }
}
