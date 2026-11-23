package org.orbeon.oxf.fr

import io.circe.Json
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.json.Converter
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl
import org.orbeon.oxf.xforms.event.events.XXFormsUploadStoreEvent
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.charset.StandardCharsets
import java.nio.file.Files


class ActionsAttachmentServiceTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  import Private.*

  describe("#8105: Base64 attachment content and `metadata` in `<fr:service-call>`") {

    it("must serialize empty, single, and multiple attachments in XML and JSON services") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "8105", "new")

      val doc = docOpt.get

      withTestExternalContext { implicit ec =>
        withFormRunnerDocument(processorService, doc) {

          activateControlWithEvent(resolveObject[XFormsControl]("call-services-control").get.effectiveId)

          val emptyXmlRoot = datasetRoot("xml-res")
          assert((emptyXmlRoot / "file-plain").stringValue == "")
          assert((emptyXmlRoot / "file-plain" /@ @*).isEmpty)
          assert((emptyXmlRoot / "file-all-meta").stringValue == "")
          assert((emptyXmlRoot / "file-all-meta" /@ @*).isEmpty)
          assert((emptyXmlRoot / "file-subset-meta").stringValue == "")
          assert((emptyXmlRoot / "file-subset-meta" /@ @*).isEmpty)
          assert((emptyXmlRoot / "files-plain" / *).isEmpty)
          assert((emptyXmlRoot / "files-meta" / *).isEmpty)
          assert((emptyXmlRoot / "text-val").stringValue == "abc")
          assert((emptyXmlRoot / "formula-val").stringValue == "abc!")

          val emptyJson = Converter.xmlToJson(datasetRoot("json-res"), strict = true)
          assert(
            emptyJson == Json.obj(
              "file-plain"       -> Json.Null,
              "file-all-meta"    -> Json.Null,
              "file-subset-meta" -> Json.Null,
              "my file"          -> Json.Null,
              "files-plain"      -> Json.arr(),
              "files-meta"       -> Json.arr(),
              "text-val"         -> Json.fromString("abc"),
              "formula-val"      -> Json.fromString("abc!")
            )
          )

          simulateUpload("single-file-control", "map.geojson", "application/geo+json", "Hello")

          val singleXmlRoot = datasetRoot("xml-res")
          assert((singleXmlRoot / "file-plain").stringValue == "SGVsbG8=")
          assert((singleXmlRoot / "file-plain" /@ @*).isEmpty)

          val singleAllMeta = (singleXmlRoot / "file-all-meta").head
          assert(singleAllMeta.stringValue == "SGVsbG8=")
          assert(singleAllMeta.attValueOpt("filename").contains("map.geojson"))
          assert(singleAllMeta.attValueOpt("mediatype").contains("application/geo+json"))
          assert(singleAllMeta.attValueOpt("size").contains("5"))

          val singleSubsetMeta = (singleXmlRoot / "file-subset-meta").head
          assert(singleSubsetMeta.stringValue == "SGVsbG8=")
          assert(singleSubsetMeta.attValueOpt("filename").contains("map.geojson"))
          assert(singleSubsetMeta.attValueOpt("mediatype").isEmpty)
          assert(singleSubsetMeta.attValueOpt("size").isEmpty)

          val singleJson = Converter.xmlToJson(datasetRoot("json-res"), strict = true)
          assert(
            singleJson == Json.obj(
              "file-plain"       -> Json.fromString("SGVsbG8="),
              "file-all-meta"    -> Json.obj(
                "content"   -> Json.fromString("SGVsbG8="),
                "filename"  -> Json.fromString("map.geojson"),
                "mediatype" -> Json.fromString("application/geo+json"),
                "size"      -> Json.fromInt(5)
              ),
              "file-subset-meta" -> Json.obj(
                "content"  -> Json.fromString("SGVsbG8="),
                "filename" -> Json.fromString("map.geojson")
              ),
              "my file"          -> Json.obj(
                "content" -> Json.fromString("SGVsbG8="),
                "size"    -> Json.fromInt(5)
              ),
              "files-plain"      -> Json.arr(),
              "files-meta"       -> Json.arr(),
              "text-val"         -> Json.fromString("abc"),
              "formula-val"      -> Json.fromString("abc!")
            )
          )

          simulateUpload("multi-files-control", "first.txt", "text/plain", "Hello")
          simulateUpload("multi-files-control", "second.txt", "text/plain", "World")
          simulateUpload("single-image-control", "photo.png", "image/png", "PNG!")

          val populatedXmlRoot = datasetRoot("xml-res")
          assert((populatedXmlRoot / "files-plain" / "_").map(_.stringValue).toList == List("SGVsbG8=", "V29ybGQ="))
          assert((populatedXmlRoot / "files-plain" / "_").forall(e => (e /@ @*).isEmpty))

          val xmlMetaItems = (populatedXmlRoot / "files-meta" / "_").toList
          assert(xmlMetaItems.size == 2)
          assert(xmlMetaItems.head.stringValue == "SGVsbG8=")
          assert(xmlMetaItems.head.attValueOpt("filename").contains("first.txt"))
          assert(xmlMetaItems.head.attValueOpt("mediatype").contains("text/plain"))
          assert(xmlMetaItems.head.attValueOpt("size").contains("5"))
          assert(xmlMetaItems(1).stringValue == "V29ybGQ=")
          assert(xmlMetaItems(1).attValueOpt("filename").contains("second.txt"))
          assert(xmlMetaItems(1).attValueOpt("mediatype").contains("text/plain"))
          assert(xmlMetaItems(1).attValueOpt("size").contains("5"))

          val imageMeta = (populatedXmlRoot / "image-meta").head
          assert(imageMeta.stringValue == "UE5HIQ==")
          assert(imageMeta.attValueOpt("filename").contains("photo.png"))
          assert(imageMeta.attValueOpt("mediatype").contains("image/png"))
          assert(imageMeta.attValueOpt("size").isEmpty)

          val populatedJson = Converter.xmlToJson(datasetRoot("json-res"), strict = true)
          assert(
            populatedJson == Json.obj(
              "file-plain"       -> Json.fromString("SGVsbG8="),
              "file-all-meta"    -> Json.obj(
                "content"   -> Json.fromString("SGVsbG8="),
                "filename"  -> Json.fromString("map.geojson"),
                "mediatype" -> Json.fromString("application/geo+json"),
                "size"      -> Json.fromInt(5)
              ),
              "file-subset-meta" -> Json.obj(
                "content"  -> Json.fromString("SGVsbG8="),
                "filename" -> Json.fromString("map.geojson")
              ),
              "my file"          -> Json.obj(
                "content" -> Json.fromString("SGVsbG8="),
                "size"    -> Json.fromInt(5)
              ),
              "files-plain"      -> Json.arr(
                Json.fromString("SGVsbG8="),
                Json.fromString("V29ybGQ=")
              ),
              "files-meta"       -> Json.arr(
                Json.obj(
                  "content"   -> Json.fromString("SGVsbG8="),
                  "filename"  -> Json.fromString("first.txt"),
                  "mediatype" -> Json.fromString("text/plain"),
                  "size"      -> Json.fromInt(5)
                ),
                Json.obj(
                  "content"   -> Json.fromString("V29ybGQ="),
                  "filename"  -> Json.fromString("second.txt"),
                  "mediatype" -> Json.fromString("text/plain"),
                  "size"      -> Json.fromInt(5)
                )
              ),
              "text-val"         -> Json.fromString("abc"),
              "formula-val"      -> Json.fromString("abc!")
            )
          )

          findRemoveTriggers("single-file-control").foreach(t => activateControlWithEvent(t.effectiveId))
          findRemoveTriggers("multi-files-control").headOption.foreach(t => activateControlWithEvent(t.effectiveId))
          findRemoveTriggers("multi-files-control").headOption.foreach(t => activateControlWithEvent(t.effectiveId))

          val clearedJson = Converter.xmlToJson(datasetRoot("json-res"), strict = true)
          assert(
            clearedJson == Json.obj(
              "file-plain"       -> Json.Null,
              "file-all-meta"    -> Json.Null,
              "file-subset-meta" -> Json.Null,
              "my file"          -> Json.Null,
              "files-plain"      -> Json.arr(),
              "files-meta"       -> Json.arr(),
              "text-val"         -> Json.fromString("abc"),
              "formula-val"      -> Json.fromString("abc!")
            )
          )
        }
      }
    }
  }

  private object Private {

    def datasetRoot(name: String)(implicit doc: XFormsContainingDocument): NodeInfo =
      doc.findInstance(s"fr-dataset-$name").get.rootElement

    def simulateUpload(
      controlId: String,
      filename : String,
      mediatype: String,
      content  : String
    )(implicit
      doc      : XFormsContainingDocument
    ): Unit = {
      val bytes    = content.getBytes(StandardCharsets.UTF_8)
      val tempPath = Files.createTempFile("orbeon-upload-test-", ".tmp")
      tempPath.toFile.deleteOnExit()
      Files.write(tempPath, bytes)

      val outerPrefix = resolveObject[XFormsControl](controlId).get.effectiveId + "≡"

      val uploadControl =
        doc.controls.getCurrentControlTree.getRelevantUploadControls
          .find(_.effectiveId.startsWith(outerPrefix))
          .get

      doc.withOutermostActionHandler {
        Dispatch.dispatchEvent(
          new XXFormsUploadStoreEvent(
            uploadControl,
            Map(
              "file"                     -> Some(tempPath.toUri.toString),
              "filename"                 -> Some(filename),
              Headers.ContentTypeLower   -> Some(mediatype),
              Headers.ContentLengthLower -> Some(bytes.length.toString)
            )
          ),
          EventCollector.Throw
        )
      }
    }

    def findRemoveTriggers(controlId: String)(implicit doc: XFormsContainingDocument): List[XFormsTriggerControl] = {
      val outerPrefix = resolveObject[XFormsControl](controlId).get.effectiveId + "≡"
      doc.controls.getCurrentControlTree.effectiveIdsToControls.values
        .collect {
          case t: XFormsTriggerControl
            if t.isRelevant &&
               t.effectiveId.startsWith(outerPrefix) &&
               t.staticControl.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment-remove")) => t
        }
        .toList
        .sortBy(_.effectiveId)
    }
  }
}
