package org.orbeon

import org.scalajs.jquery.JQuery

import scala.scalajs.js

object Select2 {
  implicit def toJQuerySelect2(jQuery: JQuery): JQuerySelect2 =
    jQuery.asInstanceOf[JQuerySelect2]

  @js.native
  trait JQuerySelect2 extends JQuery {
    def select2(options: Options): Unit = js.native
  }

  class Options extends js.Object {
    var placeholder    : Option             = _
    var ajax           : Ajax               = _
    var allowClear     : Boolean            = false
    var dropdownParent : js.UndefOr[JQuery] = _
    var width          : String             = _
  }

  trait Option extends js.Object {
    val id   : String
    val text : String
  }

  trait ParamsData extends js.Object {
    val term: js.UndefOr[String]
    val page: js.UndefOr[Int]
  }

  trait Params extends js.Object {
    val data: ParamsData
  }

  trait Data extends js.Object {
    val results    : js.Array[Option]
    val pagination : Pagination
  }

  trait Pagination extends js.Object {
    val more: Boolean
  }

  type Success = js.Function1[Data, Unit]

  trait Ajax extends js.Object {
    val delay: Int
    def transport(
      params  : Params,
      success : Success,
      failure : js.Function0[Unit]
    ): Unit
  }

}
