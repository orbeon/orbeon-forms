package org.orbeon.xforms

import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global as g, newInstance as newJsInstance}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("OrbeonXFormsXbl")
object XFormsXbl {

  private val cssClassesToConstructors: mutable.Map[String, html.Element => js.Any] = mutable.Map.empty

  @JSExport
  lazy val componentInitialized: YUICustomEvent =
    newJsInstance(g.YAHOO.util.CustomEvent)(null, null, false, g.YAHOO.util.CustomEvent.FLAT).asInstanceOf[YUICustomEvent]

  @JSExport
  def instanceForControl(elem: html.Element): XBLCompanion =
    findInstanceForControl(elem).orNull

  private def findIdentifyingCssClass(classes: String): Option[String] =
    classes.split(" ").find { clazz =>
      clazz.startsWith("xbl-")   &&
        clazz != "xbl-component" &&
        clazz != "xbl-focusable" &&
        clazz != "xbl-javascript-lifecycle"
    }

  def findInstanceForControl(elem: html.Element): Option[XBLCompanion] =
    for {
      xblControlElem      <- elem.closestOpt(".xbl-component")
      identifyingCssClass <- findIdentifyingCssClass(xblControlElem.className)
      factory             <- cssClassesToConstructors.get(identifyingCssClass)
    } yield
      factory(xblControlElem).asInstanceOf[XBLCompanion]

  @JSExport
  def declareCompanion(name: String, prototypeOrClass: js.Dynamic): Unit = {
    val isClass      = js.typeOf(prototypeOrClass) == "function"

    val separatorIdx = name.indexOf('|')
    val head         = name.substring(0, separatorIdx)
    val tail         = name.substring(separatorIdx + 1)

    val cssClass     = s"xbl-$head-$tail"

    val xblClass: js.Dynamic =
      if (isClass)
        prototypeOrClass
      else {
        val klass = newJsInstance(g.Function)().asInstanceOf[js.Dynamic]
        js.Object.assign(
          klass.prototype.asInstanceOf[js.Object],
          prototypeOrClass.asInstanceOf[js.Object]
        )
        klass
      }

    declareClass(xblClass.asInstanceOf[js.Function], cssClass)
  }

  @JSExport
  def declareClass(xblClass: js.Function, cssClass: String): Unit = {
    var doNothingSingleton: js.Dynamic = null.asInstanceOf[js.Dynamic]
    cssClassesToConstructors(cssClass) = { targetElem =>

      val subclass = createSubclass(xblClass)

      val containerElemOpt: Option[html.Element] =
        if (targetElem == null || ! dom.document.contains(targetElem))
          None
        else
          targetElem.closestOpt(s".$cssClass")

      containerElemOpt match {
        case None =>
          if (doNothingSingleton == null) {
            doNothingSingleton = js.Dynamic.literal()
            for (methodName <- js.Object.keys(xblClass.asInstanceOf[js.Dynamic].prototype.asInstanceOf[js.Object])) {
              //g.console.trace(s"instanceForControl: creating `doNothingSingleton` for `$methodName`")
              doNothingSingleton.updateDynamic(methodName)((() => ()).asInstanceOf[js.Any])
            }
          }
          dom.console.debug(s"instanceForControl: returning mock `doNothingSingleton` for `$cssClass`")
          doNothingSingleton
        case Some(containerElem) =>

          val existingInst =
            $(containerElem).asInstanceOf[js.Dynamic].data("xforms-xbl-object")

          if (
            js.isUndefined(existingInst) ||
              existingInst == null       ||
              existingInst.container.asInstanceOf[html.Element] != containerElem
          ) {

            // Q: Under what circumstances can this happen?
            // Q: In this case, should we call `destroy()` on the class?
            if (! js.isUndefined(existingInst) && existingInst != null &&
                existingInst.container.asInstanceOf[html.Element] != containerElem)
              dom.console.debug(
                "instanceForControl: instance found in data but for different container: `" +
                existingInst.container.id +
                "` and `" + containerElem.id + "`"
              )
            val inst = js.Dynamic.newInstance(subclass)(containerElem)
            inst.init()

            // TODO: We remove those in `Form.destroy()`, but should we do it when the instance is destroyed
            //  here too?
            Page
              .getXFormsFormFromHtmlElemOrThrow(inst.container.asInstanceOf[html.Element])
              .xblInstances.push(inst.asInstanceOf[XBLCompanion])
            $(containerElem).asInstanceOf[js.Dynamic].data("xforms-xbl-object", inst)
            inst
          } else {
            existingInst
          }
      }
    }
  }


  def isObjectWithMethod(obj: js.Any, method: String): Boolean =
    obj.isInstanceOf[js.Object] && {
      val methodField = obj.asInstanceOf[js.Dynamic].selectDynamic(method).asInstanceOf[js.Any]
      methodField.isInstanceOf[js.Function]
    }

  def isComponent(control: html.Element): Boolean =
    control.hasClass("xbl-component")

  def isJavaScriptLifecycle(control: html.Element): Boolean =
    isComponent(control) && control.hasClass("xbl-javascript-lifecycle")

  def isFocusable(control: html.Element): Boolean =
    isComponent(control) && control.hasClass("xbl-focusable")

  // See:
  //
  // - https://github.com/orbeon/orbeon-forms/issues/5635
  // - https://github.com/scala-js/scala-js/blob/9a2df76c65b37b4d0a3a8a51ef3795994e58cd1a/test-suite/js/src/test/scala/org/scalajs/testsuite/jsinterop/NestedJSClassTest.scala#L651-L678
  // - https://github.com/scala-js/scala-js/issues/4801
  // - https://www.scala-js.org/doc/interoperability/sjs-defined-js-classes.html
  //
  @js.native
  private trait OuterNativeTrait extends js.Object {
    @js.native
    class InnerClass(containerElem: html.Element) extends js.Object {
      def init(): Unit = js.native
      def destroy(): Unit = js.native
    }
  }

  private def createSubclass(superclass: js.Function): js.Dynamic = {

    val outer = js.Dynamic.literal(InnerClass = superclass.asInstanceOf[js.Dynamic]).asInstanceOf[OuterNativeTrait]

    class Subclass(containerElem: html.Element) extends outer.InnerClass(containerElem) {

      if (js.isUndefined(this.asInstanceOf[js.Dynamic].container)) // `js.Object.hasProperty()` only check enumerable properties
        js.Object.defineProperty(
          this,
          "container",
          new js.PropertyDescriptor {
            value        = js.defined(containerElem)
            writable     = false
            enumerable   = true
            configurable = true
          }
        )

      private var initCalled    = false
      private var destroyCalled = false

      private def superPrototypeAsDynamic: js.Dynamic =
        superclass.asInstanceOf[js.Dynamic].prototype

      override def init(): Unit =
        if (! initCalled) {
          initCalled = true
          if (superPrototypeAsDynamic.init.asInstanceOf[js.Any].isInstanceOf[js.Function])
            super.init()
          if (! isJavaScriptLifecycle(containerElem))
            XFormsXbl.componentInitialized.fire(new js.Object {
              val container   = containerElem
              val constructor = js.constructorOf[Subclass]
            })
        }

      override def destroy(): Unit = {
        if (! destroyCalled) {
          destroyCalled = true
          if (superPrototypeAsDynamic.destroy.asInstanceOf[js.Any].isInstanceOf[js.Function])
            super.destroy()
          // We can debate whether the following clean-up should happen here or next to the caller of `destroy()`.
          // However, legacy users might call `destroy()` manually, in which case it's better to clean-up here.
          $(containerElem).removeData("xforms-xbl-object")
        }
      }
    }

    js.constructorOf[Subclass]
  }
}