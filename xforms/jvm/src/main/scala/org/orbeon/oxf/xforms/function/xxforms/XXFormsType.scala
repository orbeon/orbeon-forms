/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.saxon.`type`.BuiltInAtomicType
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{NodeInfo, StandardNames}
import org.orbeon.saxon.value.{AtomicValue, QNameValue}

class XXFormsType extends XXFormsMIPFunction {

  override def evaluateItem(xpathContext: XPathContext): QNameValue =
    itemArgumentOrContextOpt(0)(xpathContext) match {
      case Some(atomicValue: AtomicValue) =>
        atomicValue.getItemType(null) match {
          case atomicType: BuiltInAtomicType =>
            val fingerprint = atomicType.getFingerprint
            new QNameValue(
              StandardNames.getPrefix(fingerprint),
              StandardNames.getURI(fingerprint),
              StandardNames.getLocalName(fingerprint),
              null
            )
          case _ =>
            null
        }
      case Some(node: NodeInfo) =>
        // Get type from node
        Option(InstanceData.getType(node)) match {
          case Some(typeQName) =>
            new QNameValue(
              "",
              typeQName.namespace.uri,
              typeQName.localName,
              null
            )
          case _ =>
            null
        }
      case _ =>
        null
    }
}