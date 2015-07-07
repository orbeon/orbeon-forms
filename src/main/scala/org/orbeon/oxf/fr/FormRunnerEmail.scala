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

import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.XML._

trait FormRunnerEmail {


    private def bindingForSection(head: NodeInfo, section: NodeInfo) = {
        val mapping = sectionTemplateXBLBindingsByURIQualifiedName(head / XBLXBLTest)
        sectionTemplateBindingName(section) flatMap mapping.get
    }

    //@XPathFunction
    def searchHoldersForClassUseSectionTemplates(head: NodeInfo, view: NodeInfo, data: NodeInfo, className: String): SequenceIterator =
        for {
            section       ← findSectionsWithTemplates(view)
            sectionName   ← getControlNameOpt(section).toList
            sectionHolder ← findDataHoldersInDocument(view, sectionName, data.rootElement)
            binding       ← bindingForSection(head, section).toList
            control       ← binding.rootElement / XBLTemplateTest descendant * filter IsControl
            if control.attClasses(className)
            bindId        ← control /@ "bind" map (_.stringValue)
            bindName      ← controlNameFromIdOpt(bindId).toList
            holder        ← findDataHoldersInDocument(binding, bindName, sectionHolder)
        } yield
            holder

    //@XPathFunction
    def searchHoldersForClassTopLevelOnly(view: NodeInfo, data: NodeInfo, className: String): SequenceIterator =
        for {
            control       ← view descendant * filter IsControl
            if control.attClasses(className)
            bindId        ← control /@ "bind" map (_.stringValue)
            bindName      ← controlNameFromIdOpt(bindId).toList
            holder        ← findDataHoldersInDocument(view, bindName, data.rootElement)
        } yield
            holder
}
