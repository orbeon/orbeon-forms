package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.xforms.AssetPath
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport.{withElement, _}
import org.orbeon.xforms.HeadElement

import scala.collection.compat._
import scala.collection.mutable


object AssetsSupport {

  def outputJsAssets(
    xhtmlPrefix   : String,
    baselineAssets: List[AssetPath],
    bindingAssets : List[HeadElement],
    isMinimal     : Boolean
  )(implicit
    receiver          : XMLReceiver
  ): Unit = {

    def outputScriptElement(resource: Option[String], cssClass: Option[String], content: Option[String]): Unit =
      withElement(
        localName = "script",
        prefix    = xhtmlPrefix,
        uri       = XHTML_NAMESPACE_URI,
        atts      = ScriptBaseAtts ::: valueOptToList("src", resource) ::: valueOptToList("class", cssClass)
      ) {
        content.foreach(text)
      }

    outputAssets(
      outputElement  = outputScriptElement,
      baselineAssets = baselineAssets.map(_.assetPath(tryMin = isMinimal)),
      bindingAssets  = bindingAssets,
    )
  }

  def outputCssAssets(
    xhtmlPrefix   : String,
    baselineAssets: List[AssetPath],
    bindingAssets : List[HeadElement],
    isMinimal     : Boolean
  )(implicit
    receiver          : XMLReceiver
  ): Unit = {

    def outputLinkOrStyleElement(resource: Option[String], cssClass: Option[String], content: Option[String]): Unit = {

      withElement(
        localName = resource match {
          case Some(_) => "link"
          case None    => "style"
        },
        prefix    = xhtmlPrefix,
        uri       = XHTML_NAMESPACE_URI,
        atts      = (resource match {
          case Some(resource) => ("href"  -> resource) :: LinkBaseAtts
          case None           => StyleBaseAtts
        }) ::: valueOptToList("class", cssClass)
      ) {
        content.foreach(text)
      }
    }

    outputAssets(
      outputElement  = outputLinkOrStyleElement,
      baselineAssets = baselineAssets.map(_.assetPath(tryMin = isMinimal)),
      bindingAssets  = bindingAssets,
    )
  }

  // Output baseline, remaining, and inline assets
  private def outputAssets(
    outputElement : (Option[String], Option[String], Option[String]) => Unit,
    baselineAssets: Iterable[String],
    bindingAssets : Iterable[HeadElement],
  ): Unit = {

    // For now, actual builtin assets always include the baseline builtin assets

    // Output baseline assets with a CSS class
    baselineAssets.foreach(s => outputElement(Some(s), XFormsBaselineCssClass, None))

    val xblUsed = bindingAssets.iterator.collect{ case e: HeadElement.Reference => e.src }.to(mutable.LinkedHashSet)

    // Output remaining assets if any, with no CSS class
    (xblUsed -- baselineAssets).foreach(s => outputElement(Some(s), None, None))

    // Output inline XBL assets
    bindingAssets.collect{ case e: HeadElement.Inline => outputElement(None, None, Option(e.text)) }
  }

  private def valueOptToList(name: String, value: Option[String]): List[(String, String)] =
    value.toList map (name -> _)

  private val XFormsBaselineCssClass = Some("xforms-baseline")

  private val ScriptBaseAtts =
    List(
      "type"  -> "text/javascript"
    )

  private val StyleBaseAtts =
    List(
      "type"  -> "text/css",
      "media" -> "all"
    )

  private val LinkBaseAtts =
    List(
      "rel"   -> "stylesheet",
      "type"  -> "text/css",
      "media" -> "all"
    )
}
