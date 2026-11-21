package org.orbeon.fr

import com.microsoft.playwright.{Locator, Page}
import com.microsoft.playwright.options.{FilePayload, SelectOption, WaitForSelectorState}
import org.orbeon.fr.DockerSupport.eventually
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.util.StringUtils.*
import org.scalatest.funspec.FixtureAnyFunSpecLike

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*


trait FormBuilderSchemaDialogTests {
  this: FixtureAnyFunSpecLike & ClientTestSupport =>

  import Private.*

  describe("Form Builder XML Schema dialog") {

    it("must validate uploads, stage changes safely, and discard staged schema on Cancel") { _ =>
      assume(Version.isPE)

      withNewFormBuilderForm { case FormRunnerWindow(xformsWindow, _) =>
        val page       = xformsWindow.page
        val ajaxServer = xformsWindow.ajaxServer

        switchToolboxTab(page, ajaxServer, "Advanced")

        val toolboxButton = page.locator(".fb-schema-toolbox-button")
        val toolboxBadge  = toolboxButton.locator(".fb-tool-badge")

        assert(toolboxButton.innerText().trimAllToEmpty == "XML Schema")
        assert(hasClass(toolboxBadge, "xforms-hidden"))

        val schemaDialog = openSchemaDialog(page, ajaxServer)
        val applyButton  = schemaDialog.locator("#fb-edit-schema-trigger button")

        // Enlarged dialog width (~780px)
        val dialogBox = schemaDialog.boundingBox()
        assert((dialogBox ne null) && dialogBox.width >= 700.0, s"Expected enlarged dialog width, got ${Option(dialogBox).map(_.width)}")

        // Empty state: description + dropzone visible, Apply disabled
        assert(schemaDialog.locator(".fb-schema-description").isVisible)
        assert(schemaDialog.locator(".fr-attachment").textContent().contains("Drag file here or select file."))
        assert(applyButton.isDisabled)

        // 1. Malformed XML upload shows inline error and keeps Apply disabled
        uploadInvalidSchemaPayload(schemaDialog, ajaxServer, "broken.xsd", "<xs:schema", expectedErrorSubstring = "not well-formed XML")
        assert(applyButton.isDisabled)

        // 2. Non-schema XML upload shows inline error and keeps Apply disabled
        uploadInvalidSchemaPayload(schemaDialog, ajaxServer, "other.xml", "<foo/>", expectedErrorSubstring = "not a valid XML Schema")
        assert(applyButton.isDisabled)

        // 3. Valid schema with xs:include stages cleanly with New badge, include warning, and 3-column types table
        uploadValidSchemaPayload(schemaDialog, ajaxServer, "books.xsd", BooksSchemaXml)
        assert(! schemaDialog.locator(".alert-error").isVisible)
        assert(schemaDialog.locator(".fb-schema-status-badge").textContent().trimAllToEmpty == "New")
        assert(schemaDialog.locator(".fb-schema-namespace-value").textContent().trimAllToEmpty == "http://example.org/books")
        assert(schemaDialog.locator(".fb-schema-prefix-badge").textContent().trimAllToEmpty == "bk")

        // Warning about stripped xs:include
        val includeWarning = schemaDialog.locator(".alert-warning").filter(new Locator.FilterOptions().setHasText("imports or includes"))
        assert(includeWarning.isVisible)

        // 3-column Simple Types table
        val rows = simpleTypeRows(schemaDialog)
        assert(rows.count() == 4)
        assertTypeRow(rows.nth(0), expectedType = "bk:rating",           expectedRestriction = "xs:integer (1..5)",    expectedInUse = "—")
        assertTypeRow(rows.nth(1), expectedType = "bk:publication-year", expectedRestriction = "xs:gYear (1450..2100)", expectedInUse = "—")
        assertTypeRow(rows.nth(2), expectedType = "bk:language",         expectedRestriction = "xs:string (enum: 4)",  expectedInUse = "—")
        assertTypeRow(rows.nth(3), expectedType = "bk:link",             expectedRestriction = "xs:anyURI (pattern)",  expectedInUse = "—")
        assert(! applyButton.isDisabled)

        // 4. Cancel discards staged upload without mutating the form model
        schemaDialog.locator("#fb-cancel-schema-trigger button").click()
        ajaxServer.waitForAllEventsProcessed()
        schemaDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        assert(hasClass(toolboxBadge, "xforms-hidden"))

        // Re-opening returns to the clean empty state
        openSchemaDialog(page, ajaxServer)
        assert(! schemaDialog.locator(".fb-schema-card").isVisible)
        assert(schemaDialog.locator(".fb-schema-description").isVisible)
        assert(applyButton.isDisabled)
      }
    }

    it("must track control type usage, warn on dangling schema types, report in Check Form, and optionally reset to String") { _ =>
      assume(Version.isPE)

      withNewFormBuilderForm { case FormRunnerWindow(xformsWindow, _) =>
        val page       = xformsWindow.page
        val ajaxServer = xformsWindow.ajaxServer

        val toolboxBadge = page.locator(".fb-schema-toolbox-button .fb-tool-badge")

        // 1. Attach books.xsd and verify toolbox count badge ("1")
        switchToolboxTab(page, ajaxServer, "Advanced")
        val schemaDialog = openSchemaDialog(page, ajaxServer)
        uploadValidSchemaPayload(schemaDialog, ajaxServer, "books.xsd", BooksSchemaXml)
        schemaDialog.locator("#fb-edit-schema-trigger button").click()
        ajaxServer.waitForAllEventsProcessed()
        schemaDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        assert(! hasClass(toolboxBadge, "xforms-hidden"))
        assert(toolboxBadge.textContent().trimAllToEmpty == "1")

        // 2. Insert a Text Field control (control-1) and bind it to bk:publication-year in Control Settings
        switchToolboxTab(page, ajaxServer, "Controls")
        page.locator(".fr-grid-td").first().click()
        ajaxServer.waitForAllEventsProcessed()

        val textFieldBtn = page.locator("#fb-toolbox-tabs button[title='Text Field']")
        textFieldBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE))
        textFieldBtn.click()
        ajaxServer.waitForAllEventsProcessed()
        page.locator("[id='fb≡section-1-section≡grid-1-grid≡control-1-control']").waitFor(
          new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
        )

        page.locator(".fr-grid-td").first().hover()
        page.locator(".fb-control-editor-left .fa-cog, [title*='Control Settings']").first().click()
        ajaxServer.waitForAllEventsProcessed()

        val csDialog = page.locator("dialog.fb-dialog-control-settings[open]")
        csDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE))

        switchTabbableTab(csDialog, ajaxServer, "Validations and Alerts")

        val schemaSelect = csDialog.locator("select:has(option:has-text('bk:publication-year'))").first()
        schemaSelect.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE))
        schemaSelect.focus()
        schemaSelect.selectOption(new SelectOption().setLabel("bk:publication-year"))
        schemaSelect.blur()
        ajaxServer.waitForAllEventsProcessed()

        csDialog.locator(".fr-dialog-buttons button:has-text('Apply')").first().click()
        ajaxServer.waitForAllEventsProcessed()
        csDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        // 3. Re-open XML Schema dialog: verify Download link, disabled Apply, and "1 control" active pill
        switchToolboxTab(page, ajaxServer, "Advanced")
        openSchemaDialog(page, ajaxServer)

        val applyButton = schemaDialog.locator("#fb-edit-schema-trigger button")
        assert(applyButton.isDisabled)
        assert(schemaDialog.locator(".fb-schema-download-link a").isVisible)

        val rows = simpleTypeRows(schemaDialog)
        assertTypeRow(rows.nth(1), expectedType = "bk:publication-year", expectedRestriction = "xs:gYear (1450..2100)", expectedInUse = "1 control")

        // 4. Removing and clicking Cancel preserves the saved schema
        schemaDialog.locator("#fb-schema-remove-trigger button").click()
        ajaxServer.waitForAllEventsProcessed()
        assert(schemaDialog.locator(".alert-info").filter(new Locator.FilterOptions().setHasText("will be removed")).isVisible)
        assert(schemaDialog.locator(".fb-schema-orphaned-warning").isVisible)

        schemaDialog.locator("#fb-cancel-schema-trigger button").click()
        ajaxServer.waitForAllEventsProcessed()
        schemaDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        // 5. Re-open and Replace with books-v2.xsd WITHOUT checking "Reset data type of affected controls to String"
        openSchemaDialog(page, ajaxServer)
        assertTypeRow(rows.nth(1), expectedType = "bk:publication-year", expectedRestriction = "xs:gYear (1450..2100)", expectedInUse = "1 control")

        schemaDialog.locator("#fb-schema-replace-trigger button").click()
        ajaxServer.waitForAllEventsProcessed()
        assert(schemaDialog.locator(".fr-attachment").isVisible)

        uploadValidSchemaPayload(schemaDialog, ajaxServer, "books-v2.xsd", BooksSchemaV2Xml)
        assert(schemaDialog.locator(".fb-schema-status-badge").textContent().trimAllToEmpty == "Modified")
        assert(rows.count() == 2)

        val orphanedWarning = schemaDialog.locator(".fb-schema-orphaned-warning")
        val resetCheckbox   = schemaDialog.locator("#fb-schema-reset-orphaned-checkbox input[type='checkbox']")
        assert(orphanedWarning.isVisible)
        assert(schemaDialog.locator(".fb-schema-orphaned-message").textContent().contains("control-1 (bk:publication-year)"))
        assert(! resetCheckbox.isChecked)

        // Apply without resetting orphaned control types -> must close cleanly with zero error popups
        applyButton.click()
        ajaxServer.waitForAllEventsProcessed()
        schemaDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))
        assert(page.locator("dialog.xforms-error-panel[open]").count() == 0)

        // 6. Check Form must flag the dangling XML Schema type
        val checkDialog = openCheckFormDialog(page, ajaxServer)
        assert(checkDialog.locator(".alert-error").isVisible)
        assert(checkDialog.locator(".alert-error").textContent().contains("""Control "control-1" uses unknown XML Schema type "bk:publication-year""""))
        closeCheckFormDialog(checkDialog, ajaxServer)

        // 6b. Control Settings on a control with a dangling schema type must show "Use schema type (bk:publication-year)"
        //     and keep built-in types (like String) selectable (rather than locking to "Use predefined type")
        page.locator(".fr-grid-td").first().hover()
        page.locator(".fb-control-editor-left .fa-cog, [title*='Control Settings']").first().click()
        ajaxServer.waitForAllEventsProcessed()
        csDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE))
        switchTabbableTab(csDialog, ajaxServer, "Validations and Alerts")

        val builtinSelect = csDialog.locator("select:has(option:has-text('String'))").first()
        builtinSelect.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE))
        assert(builtinSelect.locator("option:has-text('Use schema type (bk:publication-year)')").count() == 1)
        assert(builtinSelect.locator("option:has-text('Use predefined type')").count() == 0)

        builtinSelect.focus()
        builtinSelect.selectOption(new SelectOption().setLabel("String"))
        builtinSelect.blur()
        ajaxServer.waitForAllEventsProcessed()
        assert(builtinSelect.locator("option:has-text('Use schema type (bk:publication-year)')").count() == 0)

        csDialog.locator(".fr-dialog-buttons a:has-text('Cancel'), .fr-dialog-buttons button:has-text('Cancel')").first().click()
        ajaxServer.waitForAllEventsProcessed()
        csDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        // 7. Re-open XML Schema dialog, check "Reset data type of affected controls to String", and Apply
        switchToolboxTab(page, ajaxServer, "Advanced")
        openSchemaDialog(page, ajaxServer)
        assert(orphanedWarning.isVisible)
        assert(applyButton.isDisabled)

        resetCheckbox.check()
        ajaxServer.waitForAllEventsProcessed()
        assert(! applyButton.isDisabled)

        applyButton.click()
        ajaxServer.waitForAllEventsProcessed()
        schemaDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        // 8. Check Form must now report zero issues
        openCheckFormDialog(page, ajaxServer)
        assert(checkDialog.locator(".alert-success").isVisible)
        assert(checkDialog.locator(".alert-success").textContent().contains("No issues were found in this form definition."))
        closeCheckFormDialog(checkDialog, ajaxServer)

        // 9. Removing the schema now produces no orphaned warning and hides the toolbox badge on Apply
        openSchemaDialog(page, ajaxServer)
        schemaDialog.locator("#fb-schema-remove-trigger button").click()
        ajaxServer.waitForAllEventsProcessed()
        assert(! orphanedWarning.isVisible)

        applyButton.click()
        ajaxServer.waitForAllEventsProcessed()
        schemaDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))
        assert(hasClass(toolboxBadge, "xforms-hidden"))
      }
    }

    it("must show and update the attached PDF Templates count badge in the toolbox") { _ =>
      assume(Version.isPE)

      withNewFormBuilderForm { case FormRunnerWindow(xformsWindow, _) =>
        val page       = xformsWindow.page
        val ajaxServer = xformsWindow.ajaxServer

        switchToolboxTab(page, ajaxServer, "Advanced")

        val pdfButton = page.locator(".fb-pdf-toolbox-button")
        val pdfBadge  = pdfButton.locator(".fb-tool-badge")

        // Initially hidden when 0 PDF templates are attached
        assert(hasClass(pdfBadge, "xforms-hidden"))

        // 1. Attach 1 PDF template and click Apply -> badge shows "1"
        val pdfDialog = openPdfDialog(page, ajaxServer)
        uploadPdfPayload(pdfDialog, ajaxServer, rowIndex = 0, filename = "template-1.pdf", templateName = "main")
        pdfDialog.locator("[id$='save-trigger'] button").click()
        ajaxServer.waitForAllEventsProcessed()
        pdfDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        assert(! hasClass(pdfBadge, "xforms-hidden"))
        assert(pdfBadge.textContent().trimAllToEmpty == "1")

        // 2. Add a 2nd PDF template, verify Cancel keeps "1", then Apply -> badge shows "2"
        openPdfDialog(page, ajaxServer)
        pdfDialog.locator("[id$='fr-grid-add'] button").click()
        ajaxServer.waitForAllEventsProcessed()
        uploadPdfPayload(pdfDialog, ajaxServer, rowIndex = 1, filename = "template-2.pdf", templateName = "summary")

        pdfDialog.locator("[id$='cancel-trigger'] button").click()
        ajaxServer.waitForAllEventsProcessed()
        pdfDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))
        assert(pdfBadge.textContent().trimAllToEmpty == "1")

        openPdfDialog(page, ajaxServer)
        pdfDialog.locator("[id$='fr-grid-add'] button").click()
        ajaxServer.waitForAllEventsProcessed()
        uploadPdfPayload(pdfDialog, ajaxServer, rowIndex = 1, filename = "template-2.pdf", templateName = "summary")

        pdfDialog.locator("[id$='save-trigger'] button").click()
        ajaxServer.waitForAllEventsProcessed()
        pdfDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        assert(! hasClass(pdfBadge, "xforms-hidden"))
        assert(pdfBadge.textContent().trimAllToEmpty == "2")

        // 3. Remove both PDF attachments and Apply -> badge hides again
        openPdfDialog(page, ajaxServer)
        while (pdfDialog.locator(".fr-attachment-remove button").count() > 0) {
          pdfDialog.locator(".fr-attachment-remove button").first().click()
          ajaxServer.waitForAllEventsProcessed()
        }
        pdfDialog.locator("[id$='save-trigger'] button").click()
        ajaxServer.waitForAllEventsProcessed()
        pdfDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))

        assert(hasClass(pdfBadge, "xforms-hidden"))
      }
    }
  }

  private object Private {

    val BooksSchemaXml: String =
      """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
        |           targetNamespace="http://example.org/books"
        |           xmlns:bk="http://example.org/books"
        |           elementFormDefault="qualified">
        |  <xs:include schemaLocation="other.xsd"/>
        |  <xs:simpleType name="rating">
        |    <xs:restriction base="xs:integer">
        |      <xs:minInclusive value="1"/>
        |      <xs:maxInclusive value="5"/>
        |    </xs:restriction>
        |  </xs:simpleType>
        |  <xs:simpleType name="publication-year">
        |    <xs:restriction base="xs:gYear">
        |      <xs:minInclusive value="1450"/>
        |      <xs:maxInclusive value="2100"/>
        |    </xs:restriction>
        |  </xs:simpleType>
        |  <xs:simpleType name="language">
        |    <xs:restriction base="xs:string">
        |      <xs:enumeration value="en"/>
        |      <xs:enumeration value="fr"/>
        |      <xs:enumeration value="de"/>
        |      <xs:enumeration value="es"/>
        |    </xs:restriction>
        |  </xs:simpleType>
        |  <xs:simpleType name="link">
        |    <xs:restriction base="xs:anyURI">
        |      <xs:pattern value="https?://.+"/>
        |    </xs:restriction>
        |  </xs:simpleType>
        |</xs:schema>""".stripMargin

    val BooksSchemaV2Xml: String =
      """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
        |           targetNamespace="http://example.org/books"
        |           xmlns:bk="http://example.org/books"
        |           elementFormDefault="qualified">
        |  <xs:simpleType name="rating">
        |    <xs:restriction base="xs:integer">
        |      <xs:minInclusive value="1"/>
        |      <xs:maxInclusive value="5"/>
        |    </xs:restriction>
        |  </xs:simpleType>
        |  <xs:simpleType name="language">
        |    <xs:restriction base="xs:string">
        |      <xs:enumeration value="en"/>
        |      <xs:enumeration value="fr"/>
        |    </xs:restriction>
        |  </xs:simpleType>
        |</xs:schema>""".stripMargin

    def hasClass(locator: Locator, className: String): Boolean =
      Option(locator.getAttribute("class"))
        .exists(_.splitTo[Set]().contains(className))

    def simpleTypeRows(schemaDialog: Locator): Locator =
      schemaDialog.locator(".fb-schema-types-table tbody tr:has(.fb-schema-col-type)")

    def switchToolboxTab(page: Page, ajaxServer: AjaxServer, tabLabel: String): Unit =
      switchTabbableTab(page.locator("#fb-toolbox-tabs"), ajaxServer, tabLabel)

    def switchTabbableTab(tabbableRoot: Locator, ajaxServer: AjaxServer, tabLabel: String): Unit = {
      val liLocator = tabbableRoot
        .locator(".nav-tabs > li")
        .filter(new Locator.FilterOptions().setHasText(tabLabel))
        .first()
      val aLocator = liLocator.locator("a[data-toggle='tabbable']")

      eventually(200.millis, 10.seconds) {
        if (! hasClass(liLocator, "active")) {
          aLocator.click()
          ajaxServer.waitForAllEventsProcessed()
        }
        assert(hasClass(liLocator, "active"), s"Expected tab `$tabLabel` to become active")
      }
    }

    def openSchemaDialog(page: Page, ajaxServer: AjaxServer): Locator = {
      page.locator(".fb-schema-toolbox-button button").click()
      ajaxServer.waitForAllEventsProcessed()
      val dialog = page.locator("#fb-schema-upload-dialog")
      dialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE))
      dialog
    }

    def openCheckFormDialog(page: Page, ajaxServer: AjaxServer): Locator = {
      page.locator(".fb-check-form-button button").click()
      ajaxServer.waitForAllEventsProcessed()
      val dialog = page.locator("#fb-check-form-dialog")
      dialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE))
      dialog
    }

    def closeCheckFormDialog(checkDialog: Locator, ajaxServer: AjaxServer): Unit = {
      checkDialog.locator("#fb-check-form-close button").click()
      ajaxServer.waitForAllEventsProcessed()
      checkDialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN))
    }

    // Multipart uploads run asynchronously via `/xforms-server-upload` before firing `xxforms-upload-done`
    def uploadValidSchemaPayload(schemaDialog: Locator, ajaxServer: AjaxServer, filename: String, xmlContent: String): Unit = {
      val fileInput = schemaDialog.locator("#fb-schema-attachment input[type='file']")
      fileInput.setInputFiles(new FilePayload(filename, "application/xml", xmlContent.getBytes(StandardCharsets.UTF_8)))
      eventually(200.millis, 30.seconds) {
        assert(schemaDialog.locator(".fb-schema-card-title").isVisible)
        assert(schemaDialog.locator(".fb-schema-card-title").textContent().trimAllToEmpty == filename)
      }
      ajaxServer.waitForAllEventsProcessed()
    }

    def uploadInvalidSchemaPayload(
      schemaDialog          : Locator,
      ajaxServer            : AjaxServer,
      filename              : String,
      xmlContent            : String,
      expectedErrorSubstring: String
    ): Unit = {
      val fileInput = schemaDialog.locator("#fb-schema-attachment input[type='file']")
      fileInput.setInputFiles(new FilePayload(filename, "application/xml", xmlContent.getBytes(StandardCharsets.UTF_8)))
      eventually(200.millis, 30.seconds) {
        val errorBanner = schemaDialog.locator(".alert-error")
        assert(errorBanner.isVisible)
        assert(errorBanner.textContent().contains(expectedErrorSubstring))
      }
      ajaxServer.waitForAllEventsProcessed()
    }

    def assertTypeRow(row: Locator, expectedType: String, expectedRestriction: String, expectedInUse: String): Unit = {
      assert(row.locator(".fb-schema-col-type").textContent().trimAllToEmpty == expectedType)
      assert(row.locator(".fb-schema-col-restriction").textContent().trimAllToEmpty == expectedRestriction)
      assert(row.locator(".fb-schema-col-in-use").textContent().trimAllToEmpty == expectedInUse)
    }

    val MinimalPdfBytes: Array[Byte] =
      "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII)

    def openPdfDialog(page: Page, ajaxServer: AjaxServer): Locator = {
      page.locator(".fb-pdf-toolbox-button button").click()
      ajaxServer.waitForAllEventsProcessed()
      val dialog = page.locator("dialog.fb-pdf-dialog[open]")
      dialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE))
      dialog
    }

    def uploadPdfPayload(
      pdfDialog   : Locator,
      ajaxServer  : AjaxServer,
      rowIndex    : Int,
      filename    : String,
      templateName: String
    ): Unit = {
      val attachment = pdfDialog.locator(".xbl-fr-attachment").nth(rowIndex)
      attachment.locator("input[type='file']").setInputFiles(new FilePayload(filename, "application/pdf", MinimalPdfBytes))
      eventually(200.millis, 30.seconds) {
        val filenameLink = attachment.locator(".fr-attachment-filename a")
        assert(filenameLink.isVisible)
        assert(filenameLink.textContent().trimAllToEmpty == filename)
      }
      ajaxServer.waitForAllEventsProcessed()

      val nameInput = pdfDialog.locator("tbody [id*='attachment-name-control'] input, .fr-grid-tr [id*='attachment-name-control'] input").nth(rowIndex)
      nameInput.fill(templateName)
      nameInput.blur()
      ajaxServer.waitForAllEventsProcessed()
    }
  }
}
