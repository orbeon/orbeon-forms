/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.fb


import org.orbeon.datatypes.Coordinate1
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.NodeInfoFactory._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._


case class ControlPosition  (gridName: String,     coordinate: Coordinate1)
case class ContainerPosition(into: Option[String], after: Option[String])

sealed trait UndoAction

object UndoAction {
//  case class UndoInsert(controlName: String)              extends UndoAction
  case class UndoRename(oldName: String, newName: String)                    extends UndoAction
  case class UndoDeleteControl  (position: ControlPosition,   xcv: NodeInfo) extends UndoAction
  case class UndoDeleteContainer(position: ContainerPosition, xcv: NodeInfo) extends UndoAction
//  case class UndoMoveContainer() extends UndoAction
//  case class UndoUpdateSettings() extends UndoAction
}

object Undo {

  def pushUndoAction(action: UndoAction)(implicit ctx: FormBuilderDocContext): Unit = {

    val encoded = JsonConverter.encode(action)
    val undos   = ctx.undoRootElem / "undos"

    XFormsAPI.insert(into = undos, after = undos / *, origin = elementInfo("undo", List(encoded)))
  }

  def popUndoAction()(implicit ctx: FormBuilderDocContext): Option[UndoAction] = {
    ctx.undoRootElem / "undos" lastChildOpt * flatMap { lastUndo ⇒

      val encoded = lastUndo.stringValue
      val result  = JsonConverter.decode(encoded).toOption

      // TODO: Add to redo actions.
      XFormsAPI.delete(lastUndo)
      result
    }
  }

  object JsonConverter {

    import cats.syntax.either._
    import io.circe.generic.auto._
    import io.circe.{parser, _}
    import io.circe.syntax._

    import scala.util.{Failure, Success, Try}

    // NOTE: Encoder/decoder for `NodeInfo` could be placed in a reusable location.

    implicit val encodeNodeInfo: Encoder[NodeInfo] = Encoder.encodeString.contramap[NodeInfo](TransformerUtils.tinyTreeToString)

    implicit val decodeNodeInfo: Decoder[NodeInfo] = Decoder.decodeString.emap { encoded ⇒
      Either.catchNonFatal(
        TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, encoded, false, false).rootElement
      ).leftMap(_.getMessage)
    }

    def encode(state: UndoAction) : String          = state.asJson.noSpaces
    def decode(jsonString: String): Try[UndoAction] = parser.decode[UndoAction](jsonString).fold(Failure.apply, Success.apply)
  }
}
