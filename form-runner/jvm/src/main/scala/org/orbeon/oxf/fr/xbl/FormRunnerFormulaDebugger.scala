package org.orbeon.oxf.fr.xbl

import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.Names.FormModel
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.XPathAnalysis
import org.orbeon.oxf.xforms.analysis.XPathAnalysis.mapSetToIterable
import org.orbeon.oxf.xforms.analysis.model.DependencyAnalyzer.Vertex
import org.orbeon.oxf.xforms.analysis.model.{DependencyAnalyzer, ModelDefs}
import org.orbeon.oxf.xml.{SaxonUtils, XMLReceiver}
import org.orbeon.oxf.xml.XMLReceiverSupport.withDocument
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.value.{AtomicValue, EmptySequence, SequenceExtent}
import org.orbeon.scaxon.Implicits._


object FormRunnerFormulaDebugger {

  // Return an `array(xs:string+)` where:
  //
  // - array items are in order of depended on -> depending on
  // - each sequence contains a map with details
  //
  // TODO:
  //
  // - support section templates
  //
  //@XPathFunction
  def explainModel(mipName: String): Item = {

    val mip =
      ModelDefs.AllXPathMipsByName(mipName)

    val explanation =
      DependencyAnalyzer.determineEvaluationOrder(
        model   = XFormsAPI.topLevelModel(FormModel).map(_.staticModel).getOrElse(throw new IllegalArgumentException("model not found")),
        mip     = mip
      )._2()

    def xpathAnalysisDoc(xpa: XPathAnalysis) = {
      val (receiver, result) = StaticXPath.newTinyTreeReceiver
      implicit val rcv: XMLReceiver = receiver
      withDocument {
        XPathAnalysis.writeXPathAnalysis(xpa)
      }
      result()
    }

    val rows =
      explanation map { case vertex @ Vertex(name, refs, expr, xpa) =>
        SaxonUtils.newMapItem(
          Map[AtomicValue, ValueRepresentationType](
            (SaxonUtils.fixStringValue("name"), name),
            (SaxonUtils.fixStringValue("expr"), expr),
            (SaxonUtils.fixStringValue("projectionDeps"), xpa.exists(_.figuredOutDependencies)),
            (SaxonUtils.fixStringValue("xpath-analysis"), xpa.map(xpathAnalysisDoc).getOrElse(EmptySequence.getInstance)),
            (SaxonUtils.fixStringValue("refs"), new SequenceExtent(refs.map(_.name).toList)),
            (SaxonUtils.fixStringValue("transitive-refs"), new SequenceExtent((vertex.transitiveRefs -- refs).map(_.name).toList))
          )
        )
      }

    SaxonUtils.newArrayItem(rows.toVector)
  }
}
