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
        <p:input name="config">
            <delegation:execute service="stock-quote" operation="get-quote" xsl:version="2.0">
                <m:GetQuote xmlns:m="http://ws.cdyne.com/">
                    <m:StockSymbol><xsl:value-of select="/symbol"/></m:StockSymbol>
                    <m:LicenseKey>0</m:LicenseKey>
                </m:GetQuote>
            </delegation:execute>
        </p:input>
        <p:output name="data" id="call"/>
    </p:processor>

    <p:processor name="oxf:delegation">
        <p:input name="interface">
            <config>
                <service id="stock-quote" type="webservice" style="document"
                    endpoint="http://ws.cdyne.com/delayedstockquote/delayedstockquote.asmx">
                    <operation name="get-quote" soap-action="http://ws.cdyne.com/GetQuote"/>
                </service>
            </config>
        </p:input>
        <p:input name="call" href="#call"/>
        <p:output name="data" ref="data" debug="fkdjfkd"/>
    </p:processor>

</p:config>
