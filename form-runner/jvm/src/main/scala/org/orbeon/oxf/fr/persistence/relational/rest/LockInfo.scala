/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.rest

import java.io.{InputStream, OutputStream, OutputStreamWriter}
import org.orbeon.dom.Document
import org.orbeon.oxf.util
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.UserAndGroup
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath._

import scala.xml.Elem


case class LockInfo(userAndGroup: UserAndGroup)

object LockInfo {

  def elem(lockInfo: LockInfo): Elem =
    <d:lockinfo xmlns:d="DAV:" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <d:lockscope><d:exclusive/></d:lockscope>
        <d:locktype><d:write/></d:locktype>
        <d:owner>
            <fr:username>{lockInfo.userAndGroup.username}</fr:username>
            <fr:groupname>{lockInfo.userAndGroup.groupname.getOrElse("")}</fr:groupname>
        </d:owner>
    </d:lockinfo>

  def toDom4j(lockInfo: LockInfo): Document =
    NodeConversions.elemToDom4j(elem(lockInfo))

  def serialize(lockInfo: LockInfo, outputStream: OutputStream): Unit = {
    val el     = elem(lockInfo)
    val writer = new OutputStreamWriter(outputStream)
    useAndClose(writer)(_.write(el.toString()))
  }

  def parse(lockInfo: InputStream): LockInfo = {
    val document  = TransformerUtils.readTinyTree(util.XPath.GlobalConfiguration, lockInfo, "", false, false)
    val owner     = document / "lockinfo" /  "owner"
    val username  = owner / "username"
    val groupname = owner / "groupname"
    LockInfo(
      UserAndGroup.fromStringsOrThrow(username.stringValue, groupname.stringValue)
    )
  }
}
