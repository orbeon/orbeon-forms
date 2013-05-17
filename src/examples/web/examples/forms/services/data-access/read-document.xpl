<!--
    Copyright (C) 2006 Orbeon, Inc.

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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb">

    <p:param name="document-id" type="input" schema-href="document-id.rng"/>
    <p:param name="document-info" type="output" schema-href="document-info-optional.rng"/>

    <!-- Dynamically build query -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#document-id"/>
        <p:input name="config">
            <xdb:query collection="/db/ops/dmv-example" create-collection="true" xsl:version="2.0">
                xquery version "1.0";
                <document-info>
                {(/document-info[document-id = '<xsl:value-of select="/document-id"/>'])[1]/*}
                </document-info>
            </xdb:query>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Run query and return document-info -->
    <p:processor name="oxf:xmldb-query">
        <p:input name="datasource" href="datasource.xml"/>
        <p:input name="query" href="#query"/>
        <p:output name="data" ref="document-info"/>
    </p:processor>

</p:config>
