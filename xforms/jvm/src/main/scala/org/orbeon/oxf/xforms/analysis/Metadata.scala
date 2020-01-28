/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import java.util.{Map => JMap}

import org.orbeon.dom.io.DocumentSource
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl._
import org.orbeon.oxf.xml.{NamespaceMapping, SAXStore, TransformerUtils}
import org.orbeon.xforms.XFormsId

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Container for element metadata gathered during document annotation/extraction:
 *
 * - id generation
 * - namespace mappings
 * - automatic XBL mappings
 * - full update marks
 *
 * There is one distinct Metadata instance per part.
 *
 * Split into traits for modularity.
 */
class Metadata(
  val idGenerator    : IdGenerator,
  val isTopLevelPart : Boolean
) extends NamespaceMappings
     with BindingMetadata
     with Marks

object Metadata {

  def apply(idGenerator: IdGenerator = new IdGenerator, isTopLevelPart: Boolean = true): Metadata =
    new Metadata(idGenerator, isTopLevelPart)

  def apply(staticStateDocument: StaticStateDocument, template: Option[AnnotatedTemplate]): Metadata = {

    // Restore generator with last id
    val metadata = new Metadata(new IdGenerator(staticStateDocument.lastId), isTopLevelPart = true)

    // Restore namespace mappings and ids
    TransformerUtils.sourceToSAX(new DocumentSource(staticStateDocument.xmlDocument), new XFormsAnnotator(metadata))

    // Restore marks if there is a template
    template foreach { template =>
      for (mark <- template.saxStore.getMarks.asScala)
        metadata.putMark(mark)
    }

    metadata
  }
}

// Handling of template marks
trait Marks {

  private val marks = new mutable.HashMap[String, SAXStore#Mark]

  def putMark(mark: SAXStore#Mark): Unit                  = marks += mark.id -> mark
  def getMark(prefixedId: String) : Option[SAXStore#Mark] = marks.get(prefixedId)

  def hasTopLevelMarks: Boolean =
    marks exists { case (prefixedId, _) => XFormsId.isTopLevelId(prefixedId) }
}

// Handling of namespaces
trait NamespaceMappings {

  private val mappingsForPrefixedIds = new mutable.HashMap[String, NamespaceMapping]

  def addNamespaceMapping(prefixedId: String, mapping: Map[String, String]): Unit =
    mappingsForPrefixedIds += prefixedId -> NamespaceMapping(mapping)

  def removeNamespaceMapping(prefixedId: String): Unit =
    mappingsForPrefixedIds -= prefixedId

  def getNamespaceMapping(prefixedId: String): Option[NamespaceMapping] =
    mappingsForPrefixedIds.get(prefixedId)
}
