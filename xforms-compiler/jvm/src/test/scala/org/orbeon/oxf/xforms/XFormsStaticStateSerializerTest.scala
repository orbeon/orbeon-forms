package org.orbeon.oxf.xforms

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.test.{PipelineSupport, ResourceManagerSupport}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.scalatest.funspec.AnyFunSpecLike


class XFormsStaticStateSerializerTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  describe("State serialization") {

    it("must serialize without errors") {

      val DocumentURL = "oxf:/apps/xforms-compiler/forms/multiple-fields.xhtml"

      withTestExternalContext { _ =>

        implicit val indentedLogger: IndentedLogger = Loggers.newIndentedLogger("compiler")

        val (template, staticState) = PartAnalysisBuilder.createFromDocument(ProcessorUtils.createDocumentFromURL(DocumentURL, null))

        import io.circe.parser.*

        parse(XFormsStaticStateSerializer.serialize(template, staticState))
          .getOrElse(throw new IllegalArgumentException("Invalid JSON"))
      }
    }
  }

  def withTestExternalContext[T](body: ExternalContext => T): T =
    InitUtils.withPipelineContext { pipelineContext =>
      body(
        PipelineSupport.setExternalContext(
          pipelineContext,
          PipelineSupport.DefaultRequestUrl
        )
      )
    }
}
