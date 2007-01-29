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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="view-account-model.xpl"/>
        <p:output name="data" id="balance"/>
    </p:processor>

    <p:choose href="aggregate('root', #instance, #balance)">
        <p:when test="if(/root/balance castable as xs:int and /root/amount castable as xs:int) then
              (xs:int(/root/balance) >= xs:int(/root/amount)) else false()">
            <!-- Use the Scope serializer to store the "balance" document into the session -->
            <p:processor name="oxf:scope-serializer">
                <p:input name="config">
                    <config>
                        <key>pfc-atm-balance</key>
                        <scope>session</scope>
                    </config>
                </p:input>
                <p:input name="data" href="aggregate('balance',
                    aggregate('root', #instance, #balance)#xpointer(/root/balance - /root/amount))"/>
            </p:processor>
            <!-- Return success -->
            <p:processor name="oxf:identity">
                <p:input name="data"><success>true</success></p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Return failure -->
            <p:processor name="oxf:identity">
                <p:input name="data"><success>false</success></p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>
</p:config>
