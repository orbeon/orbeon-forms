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
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:delegation="http://orbeon.org/oxf/xml/delegation"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config" xmlns:m="http://ws.invesbot.com/">
            <delegation:execute service="stock-quote" operation="GetQuote" xsl:version="2.0">
                <m:symbol><xsl:value-of select="/symbol"/></m:symbol>
            </delegation:execute>
        </p:input>
        <p:output name="data" id="call"/>
    </p:processor>

    <p:processor name="oxf:delegation">
        <p:input name="interface">
            <config>
                <service id="stock-quote" type="webservice" style="rpc"
                        endpoint="http://ws.invesbot.com/stockquotes.asmx">
                    <operation nsuri="http://ws.invesbot.com/" name="GetQuote" soap-action="http://ws.invesbot.com/GetQuote"/>
                </service>
            </config>
        </p:input>
        <p:input name="call" href="#call"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
