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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:session-generator">
        <p:input name="config"><key>logged</key></p:input>
        <p:output name="data" id="logged"/>
    </p:processor>

    <p:choose href="#logged">
        <p:when test="/logged != 'true'">
            <!-- Redirect to login page -->
            <p:processor name="oxf:redirect">
                <p:input name="data">
                    <redirect-url>
                        <path-info>login</path-info>
                        <parameters>
                            <parameter>
                                <name>form/path-info</name>
                                <value>checkout</value>
                            </parameter>
                        </parameters>
                    </redirect-url>
                </p:input>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Empty cart -->
            <p:processor name="oxf:session-serializer">
                <p:input name="data"><cart/></p:input>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
