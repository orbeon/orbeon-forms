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
package org.orbeon.oxf.fr.persistence.test

import org.orbeon.oxf.externalcontext.UserAndGroup

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import org.orbeon.oxf.fr.persistence.relational.rest.LockInfo
import org.scalatest.funspec.AnyFunSpecLike


class LockInfoTest extends AnyFunSpecLike {

  describe("Dealing with `d:lockinfo` documents") {
    it("can parse what we serialized") {
      val username  =      "hsimpson"
      val groupname = Some("simpsons")

      // Serialized
      val outputStream = new ByteArrayOutputStream()
      LockInfo.serialize(LockInfo(UserAndGroup(username, groupname)), outputStream)

      // Parse
      val inputStream = new ByteArrayInputStream(outputStream.toByteArray)
      val lockInfo    = LockInfo.parse(inputStream)

      assert(lockInfo.userAndGroup == UserAndGroup(username, groupname) )
    }
  }
}
