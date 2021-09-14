package org.orbeon.oxf.xforms

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.test.{PipelineSupport, ResourceManagerSupport}
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.scalatest.funspec.AnyFunSpecLike


class XFormsStaticStateSerializerTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  ResourceManagerSupport

  describe("State serialization") {

    it("must serialize without errors") {
      withTestExternalContext { _ =>

        val documentURL = "oxf:/apps/xforms-compiler/forms/multiple-fields.xhtml"
//        val documentURL = "oxf:/apps/xforms-compiler/forms/hello.xhtml"

        val (template, staticState) = PartAnalysisBuilder.createFromDocument(ProcessorUtils.createDocumentFromURL(documentURL, null))

        XFormsStaticStateSerializer.serialize(template, staticState)
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
