package org.orbeon.xforms

import org.orbeon.facades.HTMLFacades._
import org.orbeon.oxf.util.StringUtils._
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.html

import scala.collection.compat._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}


object EmbeddingSupport {

  def destroyForm(container: html.Element): Unit = {
    Option(container.querySelector("form")).foreach { formElem =>
      val form = Page.getForm(formElem.id)
      form.xblInstances.foreach(_.destroy())
      form.xblInstances.clear()
      Page.unregisterForm(form)
    }
    container.childNodes.foreach(container.removeChild)
  }

  def findAndDetachCssToLoad(container: dom.NodeSelector): List[String] = {
      container
        .querySelectorAll("link")
        .map(_.asInstanceOf[html.Link])
        .map { link =>
          link.parentNode.removeChild(link)
          link.href
        }.to(List)
    }

    def findAndDetachJsToLoad(container: dom.NodeSelector): List[HeadElement] = {
      // Find scripts
      val innerScripts =
        container
          .querySelectorAll("script")
          .map(_.asInstanceOf[html.Script])
          .map { script =>
            script.parentNode.removeChild(script)
            if (script.src.nonAllBlank)
              HeadElement.Reference(script.src)
            else
              HeadElement.Inline(script.innerHTML)
          }.toList

      // Find scripts we already have in the page, so not to include the same script more than once
      val existingReferenceScripts = {

        val domScripts =
          dom.document.head
            .querySelectorAll("script")
            .map(_.asInstanceOf[html.Script])
            .collect {
              case script if script.src.nonAllBlank =>
                HeadElement.Reference(script.src)
            }

        // Assume that first script that starts with `orbeon-` is the baseline
        val baselineScript = innerScripts.collect {
          case script @ HeadElement.Reference(src)
            if src.splitTo[List]("/").lastOption.exists(_.startsWith("orbeon-")) => script
        }

        domScripts ++ baselineScript
      }

      innerScripts.filterNot(existingReferenceScripts.contains)
    }

    def moveChildren(source: dom.Node, target: dom.Element): Unit =
      while (source.hasChildNodes())
        target.appendChild(source.firstChild)

    def loadStylesheets(stylesheetsToLoad: List[String]): Future[Unit] = {
      Future.sequence(
        stylesheetsToLoad.map { stylesheetToLoad =>
          val headLink =
            dom.document
              .createElement("link")
              .asInstanceOf[html.Link]

          val future = headLink.onloadF
          headLink.rel    = "stylesheet"
          headLink.`type` = "text/css"
          headLink.href   = stylesheetToLoad
          dom.document.head.appendChild(headLink)
          future
        }
      ).map(_ => ())
    }

    // Add new scripts to the `<head>`
    def loadScripts(scriptsToLoad: List[HeadElement]): Future[Unit] = {
      val promise = Promise[Unit]()
      def worker(scriptsToLoad: List[HeadElement]): Unit = {
        scriptsToLoad match {
          case firstScriptToLoad :: restScriptsToLoad =>
            // Insert first script and wait for it to be loaded
            val headScript =
              dom.document
                .createElement("script")
                .asInstanceOf[html.Script]

            firstScriptToLoad match {
              case HeadElement.Reference(src) =>
                headScript.src = src
                headScript.onload = _ => worker(restScriptsToLoad)
              case HeadElement.Inline(text) =>
                headScript.text = text // Q: 1. Does it work? and 2. Is it synchronous?
                worker(restScriptsToLoad)
            }

            dom.document.head.appendChild(headScript)
          case Nil =>
            promise.success(())
        }
      }
      worker(scriptsToLoad)
      promise.future
    }
}
