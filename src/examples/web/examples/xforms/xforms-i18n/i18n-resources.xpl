<!--
    Copyright (C) 2005 Orbeon, Inc.

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

    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>locale</key>
                <scope>session</scope>
                <session-scope>application</session-scope>
            </config>
        </p:input>
        <p:output name="data" id="locale"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#locale"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <url>oxf:/examples/xforms/xforms-i18n/resources/<xsl:value-of select="/locale"/>.xml</url>
                <content-type>application/xml</content-type>
            </config>
        </p:input>
        <p:output name="data" id="url-generator-config"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-generator-config"/>
        <p:output name="data" id="resources"/>
    </p:processor>

    <!-- Convert and serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <indent>false</indent>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#resources"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
