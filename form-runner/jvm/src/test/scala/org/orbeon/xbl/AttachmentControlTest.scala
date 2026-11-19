package org.orbeon.xbl

import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.controls.{XFormsRepeatControl, XFormsTriggerControl, XFormsUploadControl}
import org.orbeon.oxf.xforms.event.events.{XXFormsDndEvent, XXFormsUploadStoreEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.Constants.DummyImageUri
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.charset.StandardCharsets
import java.nio.file.Files


class AttachmentControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  import Private.*

  describe("fr:attachment and fr:image-attachment xforms-value-changed") {

    it("must fire on upload and removal, and not on initial load, reorder, or persistence URL rewrite") {

      val initialUri = XFormsUploadControl.hmacURL(DummyImageUri, Some("init.png"), Some("image/png"), Some("128"))
      val xhtml      = buildTestDocument(initialUri = initialUri)

      withTestExternalContext { implicit ec =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {

            assert(eventCount("prepopulated-files") == 0)
            assert(eventCount("single-file")        == 0)
            assert(eventCount("multiple-files")     == 0)
            assert(eventCount("single-image")       == 0)
            assert(eventCount("multiple-images")    == 0)

            simulateUpload("multiple-files-control", "alpha.txt", "text/plain", "Alpha")
            val multiUri1 = childValues("multiple-files")
            assert(multiUri1.size == 1)
            assert(eventCount("multiple-files") == 1)
            assert(lastEventValue("multiple-files") == multiUri1.mkString(" "))

            simulateUpload("multiple-files-control", "beta.txt", "text/plain", "Beta")
            val multiUrisAfterSecond = childValues("multiple-files")
            assert(multiUrisAfterSecond.size == 2)
            assert(eventCount("multiple-files") == 2)
            assert(lastEventValue("multiple-files") == multiUrisAfterSecond.mkString(" "))

            dispatchDnd("multiple-files-control", start = 1, end = 2)
            assert(childValues("multiple-files") == multiUrisAfterSecond.reverse)
            assert(eventCount("multiple-files") == 2)

            val persistedMultiUri = "/fr/service/persistence/crud/acme/attachment-test/data/doc1/file1.bin"
            withAction {
              val firstItem = (doc.findInstance("fr-form-instance").get.rootElement / "multiple-files" / "_").head
              XFormsAPI.setvalue(List(firstItem), persistedMultiUri)
            }
            assert(eventCount("multiple-files") == 2)

            val removeTriggers = findRemoveTriggers("multiple-files-control")
            assert(removeTriggers.size == 2)
            activateControlWithEvent(removeTriggers.head.effectiveId)
            assert(childValues("multiple-files") == List(multiUrisAfterSecond.head))
            assert(eventCount("multiple-files") == 3)
            assert(lastEventValue("multiple-files") == multiUrisAfterSecond.head)

            val remainingTriggers = findRemoveTriggers("multiple-files-control")
            assert(remainingTriggers.size == 1)
            activateControlWithEvent(remainingTriggers.head.effectiveId)
            assert(childValues("multiple-files").isEmpty)
            assert(eventCount("multiple-files") == 4)
            assert(lastEventValue("multiple-files") == "")

            simulateUpload("single-file-control", "doc.txt", "text/plain", "Hello")
            val singleFileUri = elementValue("single-file")
            assert(singleFileUri.nonAllBlank)
            assert(eventCount("single-file") == 1)
            assert(lastEventValue("single-file") == singleFileUri)

            withAction {
              val singleFileElem = doc.findInstance("fr-form-instance").get.rootElement / "single-file"
              XFormsAPI.setvalue(singleFileElem, "/fr/service/persistence/crud/acme/attachment-test/data/doc1/single.bin")
            }
            assert(eventCount("single-file") == 1)

            val singleFileRemove = findRemoveTriggers("single-file-control")
            assert(singleFileRemove.size == 1)
            activateControlWithEvent(singleFileRemove.head.effectiveId)
            assert(elementValue("single-file") == "")
            assert(eventCount("single-file") == 2)
            assert(lastEventValue("single-file") == "")

            simulateUpload("multiple-images-control", "img1.png", "image/png", "PNG1")
            val imgUris1 = childValues("multiple-images")
            assert(imgUris1.size == 1)
            assert(eventCount("multiple-images") == 1)
            assert(lastEventValue("multiple-images") == imgUris1.mkString(" "))

            simulateUpload("multiple-images-control", "img2.png", "image/png", "PNG2")
            val imgUris2 = childValues("multiple-images")
            assert(imgUris2.size == 2)
            assert(eventCount("multiple-images") == 2)
            assert(lastEventValue("multiple-images") == imgUris2.mkString(" "))

            val multiImgRemove = findRemoveTriggers("multiple-images-control")
            assert(multiImgRemove.size == 2)
            activateControlWithEvent(multiImgRemove.head.effectiveId)
            assert(eventCount("multiple-images") == 3)
            assert(lastEventValue("multiple-images") == imgUris2.last)

            simulateUpload("single-image-control", "photo.png", "image/png", "PHOTO")
            val singleImgUri = elementValue("single-image")
            assert(singleImgUri.nonAllBlank)
            assert(eventCount("single-image") == 1)
            assert(lastEventValue("single-image") == singleImgUri)

            val singleImgRemove = findRemoveTriggers("single-image-control")
            assert(singleImgRemove.size == 1)
            activateControlWithEvent(singleImgRemove.head.effectiveId)
            assert(elementValue("single-image") == "")
            assert(eventCount("single-image") == 2)
            assert(lastEventValue("single-image") == "")
          }
        }
      }
    }
  }

  private object Private {

    def buildTestDocument(initialUri: String) =
      <xh:html
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <xh:head>
          <xf:model
            id="fr-form-model"
            xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary"
            xxf:custom-mips="upload-mediatypes">
            <xf:instance id="fr-form-instance">
              <form>
                <prepopulated-files>
                  <_ filename="init.png" mediatype="image/png" size="128">{initialUri}</_>
                </prepopulated-files>
                <single-file filename="" mediatype="" size=""/>
                <multiple-files/>
                <single-image filename="" mediatype="" size=""/>
                <multiple-images/>
                <events>
                  <prepopulated-files count="0"/>
                  <single-file count="0"/>
                  <multiple-files count="0"/>
                  <single-image count="0"/>
                  <multiple-images count="0"/>
                </events>
              </form>
            </xf:instance>
            <xf:bind ref="single-file" type="xf:anyURI"/>
            <xf:bind ref="single-image" type="xf:anyURI" constraint="xxf:upload-mediatypes('image/*')"/>
            <xf:bind ref="multiple-images" constraint="xxf:upload-mediatypes('image/*')"/>

            <xf:action event="xforms-value-changed" observer="prepopulated-files-control" target="#observer">
              <xf:setvalue ref="instance('fr-form-instance')/events/prepopulated-files/@count" value="xs:integer(.) + 1"/>
              <xf:setvalue ref="instance('fr-form-instance')/events/prepopulated-files" value="event('xxf:value')"/>
            </xf:action>
            <xf:action event="xforms-value-changed" observer="single-file-control" target="#observer">
              <xf:setvalue ref="instance('fr-form-instance')/events/single-file/@count" value="xs:integer(.) + 1"/>
              <xf:setvalue ref="instance('fr-form-instance')/events/single-file" value="event('xxf:value')"/>
            </xf:action>
            <xf:action event="xforms-value-changed" observer="multiple-files-control" target="#observer">
              <xf:setvalue ref="instance('fr-form-instance')/events/multiple-files/@count" value="xs:integer(.) + 1"/>
              <xf:setvalue ref="instance('fr-form-instance')/events/multiple-files" value="event('xxf:value')"/>
            </xf:action>
            <xf:action event="xforms-value-changed" observer="single-image-control" target="#observer">
              <xf:setvalue ref="instance('fr-form-instance')/events/single-image/@count" value="xs:integer(.) + 1"/>
              <xf:setvalue ref="instance('fr-form-instance')/events/single-image" value="event('xxf:value')"/>
            </xf:action>
            <xf:action event="xforms-value-changed" observer="multiple-images-control" target="#observer">
              <xf:setvalue ref="instance('fr-form-instance')/events/multiple-images/@count" value="xs:integer(.) + 1"/>
              <xf:setvalue ref="instance('fr-form-instance')/events/multiple-images" value="event('xxf:value')"/>
            </xf:action>
          </xf:model>
          <xf:model id="fr-parameters-model">
            <xf:instance id="fr-parameters-instance">
              <request>
                <app>acme</app>
                <form>attachment-test</form>
                <form-version>1</form-version>
                <document/>
                <mode>new</mode>
              </request>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <fr:attachment id="prepopulated-files-control" ref="prepopulated-files" multiple="true" class="fr-attachment">
            <xf:label>Prepopulated Files</xf:label>
          </fr:attachment>
          <fr:attachment id="single-file-control" ref="single-file" class="fr-attachment">
            <xf:label>Single File</xf:label>
          </fr:attachment>
          <fr:attachment id="multiple-files-control" ref="multiple-files" multiple="true" class="fr-attachment">
            <xf:label>Multiple Files</xf:label>
          </fr:attachment>
          <fr:image-attachment id="single-image-control" ref="single-image" class="fr-attachment">
            <xf:label>Single Image</xf:label>
          </fr:image-attachment>
          <fr:image-attachment id="multiple-images-control" ref="multiple-images" multiple="true" class="fr-attachment">
            <xf:label>Multiple Images</xf:label>
          </fr:image-attachment>
        </xh:body>
      </xh:html>.toDocument

    def eventCount(name: String)(implicit doc: XFormsContainingDocument): Int =
      (doc.findInstance("fr-form-instance").get.rootElement / "events" / name /@ "count").stringValue.toInt

    def lastEventValue(name: String)(implicit doc: XFormsContainingDocument): String =
      (doc.findInstance("fr-form-instance").get.rootElement / "events" / name).stringValue

    def elementValue(name: String)(implicit doc: XFormsContainingDocument): String =
      (doc.findInstance("fr-form-instance").get.rootElement / name).stringValue

    def childValues(name: String)(implicit doc: XFormsContainingDocument): List[String] =
      (doc.findInstance("fr-form-instance").get.rootElement / name / "_").map(_.stringValue).toList

    def simulateUpload(
      controlId  : String,
      filename   : String,
      mediatype  : String,
      content    : String
    )(implicit
      doc        : XFormsContainingDocument
    ): Unit = {
      val bytes    = content.getBytes(StandardCharsets.UTF_8)
      val tempPath = Files.createTempFile("orbeon-upload-test-", ".tmp")
      tempPath.toFile.deleteOnExit()
      Files.write(tempPath, bytes)

      val uploadControl =
        doc.controls.getCurrentControlTree.getRelevantUploadControls
          .find(_.effectiveId.startsWith(s"$controlId≡"))
          .get

      withAction {
        Dispatch.dispatchEvent(
          new XXFormsUploadStoreEvent(
            uploadControl,
            Map(
              "file"                    -> Some(tempPath.toUri.toString),
              "filename"                -> Some(filename),
              Headers.ContentTypeLower   -> Some(mediatype),
              Headers.ContentLengthLower -> Some(bytes.length.toString)
            )
          ),
          EventCollector.Throw
        )
      }
    }

    def dispatchDnd(
      controlId: String,
      start    : Int,
      end      : Int
    )(implicit
      doc      : XFormsContainingDocument
    ): Unit = {
      val repeatControl =
        doc.controls.getCurrentControlTree.effectiveIdsToControls.values
          .collect {
            case r: XFormsRepeatControl
              if r.effectiveId.startsWith(s"$controlId≡") && r.staticControl.isDnD => r
          }
          .head

      withAction {
        Dispatch.dispatchEvent(
          new XXFormsDndEvent(
            repeatControl,
            Map(
              "dnd-start" -> Some(start.toString),
              "dnd-end"   -> Some(end.toString)
            )
          ),
          EventCollector.Throw
        )
      }
    }

    def findRemoveTriggers(controlId: String)(implicit doc: XFormsContainingDocument): List[XFormsTriggerControl] =
      doc.controls.getCurrentControlTree.effectiveIdsToControls.values
        .collect {
          case t: XFormsTriggerControl
            if t.isRelevant &&
               t.effectiveId.startsWith(s"$controlId≡") &&
               t.staticControl.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment-remove")) => t
        }
        .toList
        .sortBy(_.effectiveId)
  }
}
