package org.orbeon.xforms.offline.demo

import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.XFormsStaticStateDeserializer
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.xforms._
import org.orbeon.xforms.offline.OfflineSupport._
import org.scalajs.dom.html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("OrbeonOffline")
object OfflineDemo extends App {

  def onOrbeonApiLoaded(): Unit = {
    XFormsApp.onOrbeonApiLoaded(LocalClientServerChannel)

    info("Orbeon API loaded")

    // Expose the API in the usual place
    val orbeonDyn = g.window.ORBEON
    orbeonDyn.xforms.Offline = js.Dynamic.global.OrbeonOffline

    // Initialize logging
//    setLoggerThreshold("", LevelThreshold(Info))
  }

  def onPageContainsFormsMarkup(): Unit =
    XFormsApp.onPageContainsFormsMarkup()

  @JSExport
  def helloDemoForm: String = DemoForms.HelloForm

  @JSExport
  def multipleFieldsDemoForm: String = DemoForms.MultipleFieldsForm

  @JSExport
  def destroyForm(container: html.Element): Unit =
    EmbeddingSupport.destroyForm(container)
    // TODO: Remove from `XFormsStateManager`.

  @JSExport
  def testLoadAndRenderForm(
    container : html.Element,
    formName  : String
  ): Unit =
    fetchSerializedFormForTesting(s"$findBasePathForTesting/xforms-compiler/service/compile/$formName.xhtml") foreach { serializedForm =>
      renderDemoForm(container, serializedForm.asInstanceOf[SerializedForm])
    }

  @JSExport
  def renderDemoForm(
    container    : html.Element,
    serializedForm : SerializedForm,
  ): RuntimeForm =
    renderCompiledForm(container, serializedForm, XFormsFunctionLibraryList, None)

  @JSExport
  def compileForm(
    serializedForm  : SerializedForm,
    functionLibrary : FunctionLibrary
  ): CompiledForm =
    withDebug("form deserialization") {
      XFormsStaticStateDeserializer.deserialize(serializedForm, functionLibrary)
    }
}
