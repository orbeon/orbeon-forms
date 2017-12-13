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


import enumeratum.EnumEntry.Lowercase
import enumeratum._
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

sealed trait UndoAction { val name: String}

object UndoAction {
  case class Rename                (oldName   : String,            newName : String)     extends UndoAction { val name = "rename"                   }
  case class DeleteControl         (position  : ControlPosition,   xcv     : NodeInfo)   extends UndoAction { val name = "delete-control"           }
  case class DeleteContainer       (position  : ContainerPosition, xcv     : NodeInfo)   extends UndoAction { val name = "delete-container"         }

  case class MoveControl           (insert    : UndoAction,        delete  : UndoAction) extends UndoAction { val name = "move-control"             }

  case class InsertControl         (controlId : String)                                  extends UndoAction { val name = "insert-control"           }
  case class InsertSection         (sectionId : String)                                  extends UndoAction { val name = "insert-section"           }
  case class InsertGrid            (gridId    : String)                                  extends UndoAction { val name = "insert-grid"              }

  case class InsertSectionTemplate (sectionId : String)                                  extends UndoAction { val name = "merge-section-template"   }
  case class MergeSectionTemplate  (sectionId : String,
                                    xcv       : NodeInfo,
                                    prefix    : String,
                                    suffix    : String)                                  extends UndoAction { val name = "merge-section-template"   }

  case class UnmergeSectionTemplate(sectionId : String,
                                    prefix    : String,
                                    suffix    : String)                                  extends UndoAction { val name = "unmerge-section-template" }

//  case class UndoMoveContainer() extends UndoAction
//  case class UndoUpdateSettings() extends UndoAction
}

object Undo {

  import Private._

  def pushUndoAction(action: UndoAction)(implicit ctx: FormBuilderDocContext): Unit =
    pushAction(UndoOrRedo.Undo, action, action.name)

  def popUndoAction()(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    popAction(UndoOrRedo.Undo)

  def pushRedoAction(action: UndoAction, name: String)(implicit ctx: FormBuilderDocContext): Unit =
    pushAction(UndoOrRedo.Redo, action, name)

  def popRedoAction()(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    popAction(UndoOrRedo.Redo)

  object JsonConverter {

    import cats.syntax.either._
    import io.circe.generic.auto._
    import io.circe.syntax._
    import io.circe.{parser, _}

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

  private object Private {

    sealed trait UndoOrRedo extends EnumEntry with Lowercase
    object UndoOrRedo extends Enum[UndoOrRedo] {

      val values = findValues

      case object Undo extends UndoOrRedo
      case object Redo extends UndoOrRedo
    }

    def pushAction(undoOrRedo: UndoOrRedo, action: UndoAction, name: String)(implicit ctx: FormBuilderDocContext): Unit = {

      val encoded = JsonConverter.encode(action)
      val undos   = ctx.undoRootElem / (undoOrRedo.entryName + "s")

      XFormsAPI.insert(
        into   = undos,
        after  = undos / *,
        origin = elementInfo(undoOrRedo.entryName, List(attributeInfo("name", name), encoded))
      )
    }

    def popAction(undoOrRedo: UndoOrRedo)(implicit ctx: FormBuilderDocContext): Option[UndoAction] = {
      ctx.undoRootElem / (undoOrRedo.entryName + "s") lastChildOpt * flatMap { lastUndoOrRedo ⇒

        val encoded = lastUndoOrRedo.stringValue
        val result  = JsonConverter.decode(encoded).toOption

        XFormsAPI.delete(lastUndoOrRedo)
        result
      }
    }
  }
}
