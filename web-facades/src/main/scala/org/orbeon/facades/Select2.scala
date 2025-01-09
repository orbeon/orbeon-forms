/**
 * Copyright (C) 2020 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.facades

import io.udash.wrappers.jquery.JQuery
import org.scalajs.dom.html

import scala.scalajs.js

object Select2 {
  implicit def toJQuerySelect2(jQuery: JQuery): JQuerySelect2 =
    jQuery.asInstanceOf[JQuerySelect2]

  @js.native
  trait JQuerySelect2 extends JQuery {
    def select2(options: Options): Unit = js.native
    def select2(method: String): Unit = js.native
  }

  trait Options extends js.Object {
    val placeholder        : js.UndefOr[Option]
    val ajax               : Ajax
    val allowClear         : Boolean
    val dropdownParent     : js.UndefOr[html.Element]
    val width              : String
    val tags               : Boolean
    val minimumInputLength : Int
  }

  implicit class OptionsOps(val o: Options) extends AnyVal {
    def copy(
      newPlaceholder: js.UndefOr[Option] = o.placeholder,
      newAjax: Ajax = o.ajax,
      newAllowClear: Boolean = o.allowClear,
      newDropdownParent: js.UndefOr[html.Element] = o.dropdownParent,
      newWidth: String = o.width,
      newTags: Boolean = o.tags,
      newMinimumInputLength: Int = o.minimumInputLength
    ): Options = {
      new Options {
        val placeholder = newPlaceholder
        val ajax = newAjax
        val allowClear = newAllowClear
        val dropdownParent = newDropdownParent
        val width = newWidth
        val tags = newTags
        val minimumInputLength = newMinimumInputLength
      }
    }
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
    val delay     : Int
    val transport : js.Function3[Params, Success, js.Function0[Unit], Unit]
  }
}
