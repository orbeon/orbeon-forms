package org.orbeon.oxf.fr

import org.orbeon.connection.StreamedContent
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.{ContentTypes, HtmlParsing, StaticXPath, XPathCache}
import org.orbeon.saxon.om
import org.orbeon.xml.NamespaceMapping
import org.scalatest.funspec.AnyFunSpecLike

import scala.jdk.CollectionConverters.*


class XmlInitialDataTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("XML initial data") {

    describe("XML POST") {

      val Xml =
        """<form xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          |      fr:data-format-version="4.0.0">
          |    <section-1>
          |        <operations/>
          |    </section-1>
          |</form>
          |""".stripMargin

      def runFormRunnerGetFieldValue(mode: String, documentIdOpt: Option[String], params: Iterable[(String, String)]): String = {
        val (_, bufferedContent, _) =
          runFormRunnerReturnContent(
            "issue",
            "7240",
            mode,
            documentId = documentIdOpt,
            query      = params,
            background = false,
            content    = Some(StreamedContent.fromBytes(Xml.getBytes(CharsetNames.Utf8), Some(ContentTypes.XmlContentType)))
          )

        val (receiver, result) = StaticXPath.newTinyTreeReceiver

        HtmlParsing.parseHtmlString(
          new String(bufferedContent.body, CharsetNames.Utf8),
          receiver
        )

        val resultDoc = result()

        XPathCache.evaluateAsString(
          contextItems       = List(resultDoc: om.Item).asJava,
          contextPosition    = 1,
          xpathString        = "//*[@id = 'section-1-section≡grid-1-grid≡operations-control']/(*:input/@value/string(), *:span[tokenize(@class, '\\s+') = 'xforms-field']/string())",
          namespaceMapping   = NamespaceMapping.EmptyMapping,
          variableToValueMap = Map.empty.asJava,
          functionLibrary    = null,
          functionContext    = null,
          baseURI            = null,
          locationData       = null,
          reporter           = null
        )
      }

      val Expected = List(
        ("new",  Nil,                                                                                                                           "create"),
        ("edit", Nil,                                                                                                                           "create"),
        ("view", Nil,                                                                                                                           "create"),
        ("new",  List(FormRunner.InternalAuthorizedOperationsParam -> FormRunnerOperationsEncryption.encryptOperations(Set("read", "update"))), "create"),
        ("edit", List(FormRunner.InternalAuthorizedOperationsParam -> FormRunnerOperationsEncryption.encryptOperations(Set("read", "update"))), "read update"),
        ("view", List(FormRunner.InternalAuthorizedOperationsParam -> FormRunnerOperationsEncryption.encryptOperations(Set("read", "update"))), "read update"),
      )

      for {
        (mode, params, expected) <- Expected
        documentIdOpt            <- List(None, Some("12345"))
      } locally {
        it (s"must handle mode=$mode/documentIdOpt=$documentIdOpt/params=$params") {
          assert(runFormRunnerGetFieldValue(mode, documentIdOpt, params) == expected)
        }
      }
    }
  }
}
