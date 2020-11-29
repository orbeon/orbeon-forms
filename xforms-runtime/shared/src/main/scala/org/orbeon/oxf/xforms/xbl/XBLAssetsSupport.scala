package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.xforms.AssetPath
import org.orbeon.xforms.HeadElement

import scala.collection.compat._
import scala.collection.mutable


object XBLAssetsSupport {

  // Output baseline, remaining, and inline resources
  def outputResources(
    outputElement : (Option[String], Option[String], Option[String]) => Unit,
    builtin       : List[AssetPath],
    headElements  : Iterable[HeadElement],
    xblBaseline   : Iterable[String],
    minimal       : Boolean
  ): Unit = {

    // For now, actual builtin resources always include the baseline builtin resources
    val builtinBaseline: mutable.LinkedHashSet[String] = builtin.iterator.map(_.assetPath(minimal)).to(mutable.LinkedHashSet)
    val allBaseline = builtinBaseline ++ xblBaseline

    // Output baseline resources with a CSS class
    allBaseline foreach (s => outputElement(Some(s), Some("xforms-baseline"), None))

    // This is in the order defined by XBLBindings.orderedHeadElements
    val xbl = headElements

    val builtinUsed: mutable.LinkedHashSet[String] = builtin.iterator.map(_.assetPath(minimal)).to(mutable.LinkedHashSet)
    val xblUsed: List[String] = xbl.iterator.collect({ case e: HeadElement.Reference => e.src }).to(List)

    // Output remaining resources if any, with no CSS class
    builtinUsed ++ xblUsed -- allBaseline foreach (s => outputElement(Some(s), None, None))

    // Output inline XBL resources
    xbl collect { case e: HeadElement.Inline => outputElement(None, None, Option(e.text)) }
  }
}
