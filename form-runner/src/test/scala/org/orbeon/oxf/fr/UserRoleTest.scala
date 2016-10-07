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
package org.orbeon.oxf.fr

import org.scalatest.FunSpecLike

class UserRoleTest extends FunSpecLike {

  val TestRoles = List(
    """clerk"""                        → SimpleRole("clerk"),
    """manager(organization="ca")"""   → ParametrizedRole("manager", """ca"""),
    """manager(organization="c\"a")""" → ParametrizedRole("manager", """c"a""")
  )

  describe("UserRole") {
    TestRoles.foreach { case (stringRole, adtRole) ⇒
      def can(operation: String) = s"can $operation `$stringRole`"
      it(can("parse"    ))(assert(UserRole.parse    (stringRole) === adtRole))
      it(can("serialize"))(assert(UserRole.serialize(adtRole   ) === stringRole))
    }
  }
}
