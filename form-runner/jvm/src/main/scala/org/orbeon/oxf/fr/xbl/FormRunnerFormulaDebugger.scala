package org.orbeon.oxf.fr.xbl

import org.orbeon.oxf.fr.Names.FormModel
import org.orbeon.oxf.fr.{FormRunner, Names}
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.XPathAnalysis
import org.orbeon.oxf.xforms.analysis.model.DependencyAnalyzer.Vertex
import org.orbeon.oxf.xforms.analysis.model.{DependencyAnalyzer, ModelDefs}
import org.orbeon.oxf.xml.SaxonUtils
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

    val model =
      XFormsAPI.topLevelModel(FormModel).map(_.staticModel).getOrElse(throw new IllegalArgumentException("model not found"))

    val explanation =
      DependencyAnalyzer.determineEvaluationOrder(
        model = model,
        mip   = mip
      )._2()

    val instanceString = XPathAnalysis.buildInstanceString(Names.FormInstance)

    val allBindPaths = model.iterateAllBinds map { bind =>
      instanceString :: bind.ancestorOrSelfBinds.reverse.flatMap(b => FormRunner.controlNameFromIdOpt(b.staticId)) mkString "/"
    }

    val allBindPathsSet = allBindPaths.toSet

    val rows =
      explanation map { case vertex @ Vertex(name, refs, expr, xpa) =>
        SaxonUtils.newMapItem(
          Map[AtomicValue, ValueRepresentationType](
            (SaxonUtils.fixStringValue("name"), name),
            (SaxonUtils.fixStringValue("expr"), expr),
            (SaxonUtils.fixStringValue("projectionDeps"), xpa.exists(_.figuredOutDependencies)),
            (SaxonUtils.fixStringValue("xpath-analysis"), xpa.map(XPathAnalysis.toTinyTree(_, path => path.startsWith(instanceString) && ! allBindPathsSet(path))).getOrElse(EmptySequence.getInstance)),
            (SaxonUtils.fixStringValue("refs"), new SequenceExtent(refs.map(_.name).toList)),
            (SaxonUtils.fixStringValue("transitive-refs"), new SequenceExtent((vertex.transitiveRefs -- refs).map(_.name).toList))
          )
        )
      }

    SaxonUtils.newArrayItem(rows.toVector)
  }
}
