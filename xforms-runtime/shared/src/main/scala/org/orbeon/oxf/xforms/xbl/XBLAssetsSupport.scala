package org.orbeon.oxf.xforms.xbl

import org.orbeon.xforms.HeadElement

import scala.collection.compat._
import scala.collection.mutable


object XBLAssetsSupport {

  private val XFormsBaselineCssClass = Some("xforms-baseline")

  // Output baseline, remaining, and inline assets
  def outputAssets(
    outputElement : (Option[String], Option[String], Option[String]) => Unit,
    baselineAssets: Iterable[String],
    bindingAssets : Iterable[HeadElement],
  ): Unit = {

    // For now, actual builtin assets always include the baseline builtin assets

    // Output baseline assets with a CSS class
    baselineAssets.foreach(s => outputElement(Some(s), XFormsBaselineCssClass, None))

    // This is in the order defined by XBLBindings.orderedHeadElements
    val xblUsed = bindingAssets.iterator.collect{ case e: HeadElement.Reference => e.src }.to(mutable.LinkedHashSet)

    // Output remaining assets if any, with no CSS class
    (xblUsed -- baselineAssets).foreach(s => outputElement(Some(s), None, None))

    // Output inline XBL assets
    bindingAssets.collect{ case e: HeadElement.Inline => outputElement(None, None, Option(e.text)) }
  }
}
