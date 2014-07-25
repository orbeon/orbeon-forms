/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.http

import jcifs.ntlmssp.Type1Message
import jcifs.ntlmssp.Type2Message
import jcifs.ntlmssp.Type3Message
import jcifs.util.Base64
import org.apache.http.impl.auth.NTLMEngine
import org.apache.http.impl.auth.NTLMEngineException
import java.io.IOException

class JCIFSEngine extends NTLMEngine {

    def generateType1Msg(domain: String, workstation: String): String = {
        val t1m = new Type1Message(Type1Message.getDefaultFlags, domain, workstation)
        Base64.encode(t1m.toByteArray)
    }

    def generateType3Msg(username: String, password: String, domain: String, workstation: String, challenge: String): String = {
        val t2m =
            try
                new Type2Message(Base64.decode(challenge))
            catch {
                case ex: IOException ⇒
                    throw new NTLMEngineException("Invalid Type2 message", ex)
            }

        val t3m =
            new Type3Message(t2m, password, domain, username, workstation, 0)

        Base64.encode(t3m.toByteArray)
    }
}