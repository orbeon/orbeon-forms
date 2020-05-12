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

import org.junit.Test
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.rest.LockInfo
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, Logging, NetUtils}
import org.scalatestplus.junit.AssertionsForJUnit

class LockUnlockTest extends ResourceManagerTestBase with AssertionsForJUnit with XMLSupport with Logging {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[LockUnlockTest]), true)

  @Test def lockUnlockTest(): Unit = {
    Connect.withOrbeonTables("lease") { (_, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      val dataURL = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
      val homerLockInfo = LockInfo("hsimpson", Some("simpsons"))
      val margeLockInfo = LockInfo("msimpson", Some("simpsons"))

      // Homer locks, Marge can't lock, and Homer can lock again
      HttpAssert.lock(dataURL, homerLockInfo, expectedCode = 200)
      HttpAssert.lock(dataURL, margeLockInfo, expectedCode = 423)
      HttpAssert.lock(dataURL, homerLockInfo, expectedCode = 200)

      // After Homer unlock, Marge can lock, and Homer can't lock
      HttpAssert.unlock(dataURL, homerLockInfo, expectedCode = 200)
      HttpAssert.lock  (dataURL, margeLockInfo, expectedCode = 200)
      HttpAssert.lock  (dataURL, homerLockInfo, expectedCode = 423)
      HttpAssert.unlock(dataURL, margeLockInfo, expectedCode = 200)
    }
  }

}
