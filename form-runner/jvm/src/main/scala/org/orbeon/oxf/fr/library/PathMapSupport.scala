package org.orbeon.oxf.fr.library

import org.orbeon.dom.QName
import org.orbeon.oxf.fr.Names.FormInstance
import org.orbeon.oxf.fr.{Names, XMLNames}
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.ElementAnalysisTreeXPathAnalyzer.SimplePathMapContext
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.function.xxforms.XXFormsInstance
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.{AxisExpression, Container, PathMap, StringLiteral}
import org.orbeon.saxon.om.{Axis, StructuredQName}
import org.orbeon.saxon.pattern.NameTest
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.xbl.Scope

import scala.util.chaining.*


object PathMapSupport {

  def addAbsoluteInstancePath(
    pathMap           : PathMap,
    instancePrefixedId: String,
    path              : Iterable[QName],
    isAttribute       : Boolean
  )(implicit
    exprContainer     : Container
  ): Unit = {

    // Here we create an `xxf:instance()` expression with an absolute instance id as a literal argument.
    // This will allow searching for instances globally. Even though we create an absolute id, this is
    // made from a prefixed id and never has repeat iterations. But this allows
    // `PathMapXPathAnalysisBuilder` to just keep the id as is instead of searching for the instance.
    val newXxfInstanceExpression: XXFormsInstance =
      (new XXFormsInstance) // Use `XXFormsInstance` so that we'll search ancestor models for dependencies
        .tap(_.setFunctionName(new StructuredQName("xxf", XMLNames.XXF, "instance")))
        .tap(_.setArguments(Array(new StringLiteral(XFormsId.effectiveIdToAbsoluteId(instancePrefixedId)))))
        .tap(_.setContainer(exprContainer))

    var target = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(newXxfInstanceExpression))

    val pool = StaticXPath.GlobalNamePool

    val pathLength = path.size

    path.zipWithIndex.foreach { case (pathElemQName, index) =>

      val useAttribute = isAttribute && index == pathLength - 1

      val axisExpression =
        new AxisExpression(
          if (useAttribute) Axis.ATTRIBUTE else Axis.CHILD,
          new NameTest(
            if (useAttribute) Type.ATTRIBUTE else Type.ELEMENT,
            pool.allocate(pathElemQName.namespace.prefix, pathElemQName.namespace.uri, pathElemQName.localName),
            pool
          )
        )

      target = axisExpression.addToPathMap(pathMap, target)
    }

    target.setAtomized()
  }

  def findInstancePath(
    scope            : Scope,
    mainModelStaticId: String,
    controlName      : String
  )(implicit
    pathMapCtx       : SimplePathMapContext
  ): Option[(String, List[QName])] = {
    val modelsInScope = pathMapCtx.partAnalysisCtx.getModelsForScope(scope)
    for {
      mainModel    <- modelsInScope.find(_.staticId == mainModelStaticId)
      mainInstance <- mainModel.instances.get(FormInstance)
      controlBind  <- mainModel.bindsByName.get(controlName)
      path         = controlBind.ancestorOrSelfBinds.flatMap(_.nameOpt).map(QName.apply)
    } yield
      mainInstance.prefixedId -> path.reverse
  }

  def findSectionTemplateComponent(
    sectionTemplateStaticId: String
  )(implicit
    pathMapCtx             : SimplePathMapContext
  ): Option[ComponentControl] = {

    def fromIndex =
      pathMapCtx.partAnalysisCtx
        .findControlAnalysis(sectionTemplateStaticId)
        .filter(_.scope.isTopLevelScope)

    def fromSearch =
      pathMapCtx.partAnalysisCtx
        .controlAnalysisMap
        .collectFirst { case (_, analysis) if analysis.staticId == sectionTemplateStaticId && analysis.scope.isTopLevelScope =>
          analysis
        }

    fromIndex.orElse(fromSearch).collect { case c: ComponentControl => c }
  }

  def findInstancePathInScope(
    startScope : Scope,
    controlName: String
  )(implicit
    pathMapCtx : SimplePathMapContext
  ): Option[(String, List[QName])] = {

    val mainModelStaticId =
      if (startScope.isTopLevelScope)
        Names.FormModel
      else
        ElementAnalysis.ancestorsIterator(pathMapCtx.element, includeSelf = true)
          .collectFirst { case cc: ComponentControl if cc.bindingOrThrow.innerScope == startScope =>
            s"${cc.localName}-model"
          }
          .getOrElse(throw new IllegalStateException(s"Couldn't find component control for non-top-level scope `${startScope.scopeId} for control `$controlName`"))

    // Check that the model exists
    pathMapCtx.partAnalysisCtx
      .getModelsForScope(startScope)
      .find(_.staticId == mainModelStaticId)
      .getOrElse(s"Couldn't find main model `$mainModelStaticId` in scope `${startScope.scopeId}` for control `$controlName`")

    PathMapSupport.findInstancePath(startScope, mainModelStaticId, controlName)
  }
}
