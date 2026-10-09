package org.orbeon.oxf.xforms

import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.test.{PipelineSupport, ResourceManagerSupport}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.processor.XFormsCompiler
import org.scalatest.funspec.AnyFunSpecLike


class XFormsStaticStateSerializerTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  describe("State serialization") {

    it("must exclude server-only XForms properties and include client-eligible XForms properties") {
      val excluded = List(
        "oxf.crypto.password",
        "oxf.http.forward-cookies",
        "oxf.xforms.password",
        "oxf.xforms.assets.baseline",
        "oxf.xforms.resources.baseline",
        "oxf.xforms.whitespace.preserve",
        "oxf.xforms.cache.documents",
        "oxf.xforms.store.application.uri",
        "oxf.xforms.xbl.library",
        "oxf.xforms.xbl.mapping.orbeon",
        "oxf.xforms.minimal-resources",
        "oxf.xforms.combine-resources",
        "oxf.xforms.replication",
        "oxf.xforms.gzip-state",
        "oxf.xforms.local-submission-forward",
        "oxf.xforms.local-submission-include",
        "oxf.xforms.local-instance-include",
        "oxf.xforms.optimize-get-all",
        "oxf.xforms.forward-submission-headers",
        "oxf.xforms.sanitize"
      )
      for (name <- excluded)
        assert(! XFormsCompiler.isClientPropertyName(name), s"expected `$name` to be excluded")

      val included = List(
        "oxf.xforms.format.output.date",
        "oxf.xforms.label.appearance",
        "oxf.xforms.readonly-appearance.static.select",
        "oxf.xforms.xbl.fr.map.provider",
        "oxf.xforms.xbl.fr.currency.prefix.*.*"
      )
      for (name <- included)
        assert(XFormsCompiler.isClientPropertyName(name), s"expected `$name` to be included")
    }

    it("must serialize without errors and filter client properties") {

      val DocumentURL = "oxf:/apps/xforms-compiler/forms/multiple-fields.xhtml"

      PipelineSupport.withPipelineContextAndTestExternalContext() { (_, _) =>

        implicit val indentedLogger: IndentedLogger = Loggers.newIndentedLogger("compiler")

        val (template, staticState) = PartAnalysisBuilder.createFromDocument(ProcessorUtils.createDocumentFromURL(DocumentURL, null))

        import io.circe.parser.*

        val json =
          parse(XFormsStaticStateSerializer.serialize(template, staticState))
            .getOrElse(throw new IllegalArgumentException("Invalid JSON"))

        val propNames =
          json.hcursor
            .downField("properties")
            .values
            .getOrElse(Nil)
            .flatMap(_.hcursor.get[String]("name").toOption)
            .toList

        assert(propNames.nonEmpty)
        assert(propNames.forall(XFormsCompiler.isClientPropertyName(_)))
      }
    }
  }
}
