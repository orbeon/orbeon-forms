/**
 * Copyright (C) 2015 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.XML._

trait FormRunnerEmail {

  // Given a form body and instance data:
  //
  // - find all controls with the given conjunction of class names
  // - for each control, find the associated bind
  // - return all data holders in the instance data to which the bind would apply
  //
  // The use case is, for example, to find all data holders pointed to by controls with the class
  // `fr-email-recipient` and, optionally, `fr-email-attachment`.
  //
  //@XPathFunction
  def searchHoldersForClassTopLevelOnly(
    body       : NodeInfo,
    data       : NodeInfo,
    classNames : String
  ): SequenceIterator =
    searchControlsTopLevelOnly(
      body      = body,
      data      = Option(data),
      predicate = hasAllClassesPredicate(classNames.splitTo[List]())
    ) flatMap {
      case ControlBindPathHolders(_, _, _, Some(holders)) ⇒ holders
      case ControlBindPathHolders(_, _, _, None)          ⇒ Nil
    }

  // Given a form head, form body and instance data:
  //
  // - find all section templates in use
  // - for each section
  //   - determine the associated data holder in instance data
  //   - find the inline binding associated with the section template
  //   - find all controls with the given conjunction of class names in the section template
  //   - for each control, find the associated bind in the section template
  //   - return all data holders in the instance data to which the bind would apply
  //
  // The use case is, for example, to find all data holders pointed to by controls with the class
  // `fr-email-recipient` and, optionally, `fr-email-attachment`, which appear within section templates.
  //
  //@XPathFunction
  def searchHoldersForClassUseSectionTemplates(
    head       : NodeInfo,
    body       : NodeInfo,
    data       : NodeInfo,
    classNames : String
  ): SequenceIterator =
    searchControlsUnderSectionTemplates(
      head      = head,
      body      = body,
      data      = Option(data),
      predicate = hasAllClassesPredicate(classNames.splitTo[List]())
    ) flatMap {
      case ControlBindPathHolders(_, _, _, Some(holders)) ⇒ holders
      case ControlBindPathHolders(_, _, _, None)          ⇒ Nil
    }
}
