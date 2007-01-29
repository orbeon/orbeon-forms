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

    <p:param name="data" type="output"/>

    <!-- Use the Scope generator to retrieve the "balance" document from the session -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>pfc-atm-balance</key>
                <scope>session</scope>
            </config>
        </p:input>
        <p:output name="data" id="balance"/>
    </p:processor>

    <p:choose href="#balance">
        <p:when test="/*/@xsi:nil = 'true'" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <!-- If the document does not exist, generate an initial "balance" document -->
            <p:processor name="oxf:identity">
                <p:input name="data"><balance>100</balance></p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Otherwise, just return the document found -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="#balance"/>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
