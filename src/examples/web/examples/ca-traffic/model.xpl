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
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:delegation="http://orbeon.org/oxf/xml/delegation"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:choose href="#instance" xmlns:v="http://orbeon.org/oxf/xml/validation">
        <p:when test="//v:error">
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <return>You didn't enter a valid highway number. Please try again.</return>
                </p:input>
                <p:output name="data" id="traffic"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#instance"/>
                <p:input name="config">
                    <delegation:execute service="ca-traffic" operation="getTraffic" xsl:version="2.0">
                        <hwynums><xsl:value-of select="/highway"/></hwynums>
                    </delegation:execute>
                </p:input>
                <p:output name="data" id="call"/>
            </p:processor>
            <p:processor name="oxf:delegation">
                <p:input name="interface">
                    <config>
                        <service id="ca-traffic" type="webservice"
                            endpoint="http://services.xmethods.net/soap/servlet/rpcrouter">
                            <operation nsuri="urn:xmethods-CATraffic" name="getTraffic"/>
                        </service>
                    </config>
                </p:input>
                <p:input name="data"><dummy/></p:input>
                <p:input name="call" href="#call"/>
                <p:output name="data" id="traffic"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('root', #traffic, #instance)"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
