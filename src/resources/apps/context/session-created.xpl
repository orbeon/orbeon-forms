<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:null-serializer">
        <p:input name="data" debug="message">
            <message>Session created.</message>
        </p:input>
    </p:processor>

    <p:processor name="oxf:scope-serializer">
        <p:input name="config">
            <config>
                <key>test-session-creation</key>
                <scope>session</scope>
            </config>
        </p:input>
        <p:input name="data">
            <document>
                This is a document stored in the session at creation time.
            </document>
        </p:input>
    </p:processor>

</p:config>
