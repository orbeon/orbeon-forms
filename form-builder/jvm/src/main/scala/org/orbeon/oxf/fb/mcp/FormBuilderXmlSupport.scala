package org.orbeon.oxf.fb.mcp

import org.orbeon.dom.QName
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.{PipelineContext, ProcessorDefinition}
import org.orbeon.oxf.processor.{DOMSerializer, ProcessorImpl, XPLConstants}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath.*


private[mcp] object FormBuilderXmlSupport {

  def readXml(bytes: Array[Byte], systemId: String): DocumentInfo =
    TransformerUtils.readTinyTree(XPath.GlobalConfiguration, new java.io.ByteArrayInputStream(bytes), systemId, false, false)

  def serializeXml(node: NodeInfo): Array[Byte] =
    TransformerUtils.tinyTreeToOrbeonDom(node).serializeToString().getBytes(CharsetNames.Utf8)

  def findAppForm(form: NodeInfo): AppForm = {
    val ctx = new org.orbeon.oxf.fr.InDocFormRunnerDocContext(form)
    AppForm(
      app  = (ctx.metadataRootElem / "application-name").stringValue,
      form = (ctx.metadataRootElem / "form-name").stringValue
    )
  }

  def annotate(form: NodeInfo, bindings: NodeInfo)(implicit pc: PipelineContext): DocumentInfo =
    runXpl(
      "oxf:/forms/orbeon/builder/form/annotate.xpl",
      List(
        "data"             -> form,
        "bindings"         -> bindings,
        "is-readonly-mode" -> NodeConversions.elemToNodeInfo(<_/>)
      )
    )

  def deannotate(form: NodeInfo)(implicit pc: PipelineContext): DocumentInfo =
    runXpl("oxf:/forms/orbeon/builder/form/deannotate.xpl", List("data" -> form))

  private def runXpl(uri: String, inputs: List[(String, NodeInfo)])(implicit pc: PipelineContext): DocumentInfo = {
    val processorDefinition = new ProcessorDefinition(QName("pipeline", XPLConstants.OXF_PROCESSORS_NAMESPACE))
    processorDefinition.addInput(ProcessorImpl.INPUT_CONFIG, uri)
    inputs.foreach { case (name, node) =>
      processorDefinition.addInput(name, TransformerUtils.tinyTreeToOrbeonDom(node).getRootElement)
    }

    val processor = InitUtils.createProcessor(processorDefinition)
    processor.reset(pc)
    val output = processor.createOutput(ProcessorImpl.OUTPUT_DATA)
    val serializer = new DOMSerializer
    PipelineUtils.connect(processor, output.getName, serializer, ProcessorImpl.INPUT_DATA)
    new DocumentWrapper(serializer.runGetDocument(pc).normalizeTextNodes, null, XPath.GlobalConfiguration)
  }
}
