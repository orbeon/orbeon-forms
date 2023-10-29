package org.orbeon.xforms

import org.orbeon.xforms.facade.XBL
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("OrbeonXFormsXbl")
object XFormsXbl {

  def isObjectWithMethod(obj: js.Any, method: String): Boolean =
    obj.isInstanceOf[js.Object] &&                                                 // `obj instanceof Object`
      obj.asInstanceOf[js.Dynamic].selectDynamic(method).isInstanceOf[js.Function] // `obj[method] instanceof Function`

  @JSExport
  def isComponent(control: html.Element): Boolean =
    control.classList.contains("xbl-component")

  @JSExport
  def isJavaScriptLifecycle(control: html.Element): Boolean =
    isComponent(control) && control.classList.contains("xbl-javascript-lifecycle")

  @JSExport
  def isFocusable(control: html.Element): Boolean =
    isComponent(control) && control.classList.contains("xbl-focusable")

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

  @JSExport
  def createSubclass(superclass: js.Function): js.Dynamic = {

    val outer = js.Dynamic.literal(InnerClass = superclass.asInstanceOf[js.Dynamic]).asInstanceOf[OuterNativeTrait]

    class Subclass(containerElem: html.Element) extends outer.InnerClass(containerElem) {

      if (js.isUndefined(this.asInstanceOf[js.Dynamic].container)) // `js.Object.hasProperty()` only check enumerable properties
        js.Object.defineProperty(
          this,
          "container",
          new js.PropertyDescriptor {
            value        = containerElem
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
          if (superPrototypeAsDynamic.init.isInstanceOf[js.Function])
            super.init()
          if (! isJavaScriptLifecycle(containerElem))
            XBL.componentInitialized.fire(new js.Object {
              val container   = containerElem
              val constructor = js.constructorOf[Subclass]
            })
        }

      override def destroy(): Unit = {
        if (! destroyCalled) {
          destroyCalled = true
          if (superPrototypeAsDynamic.destroy.isInstanceOf[js.Function])
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