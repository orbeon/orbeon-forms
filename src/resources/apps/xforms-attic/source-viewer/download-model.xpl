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
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param type="input" name="instance"/>

    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:xslt" href="#instance">
            <config xsl:version="2.0">
                <xsl:variable name="applications-list" select="document('oxf:/apps-list.xml')" as="document-node()"/>
                <xsl:variable name="application-id" select="/*/application-id" as="xs:string"/>
                <!-- Take first one in case the app id appears more than once in the app list -->
                <xsl:variable name="application" select="($applications-list//application[@id = $application-id])[1]" as="element()"/>
                <xsl:variable name="url" select="concat('oxf:/apps/', $application/@id, '/', string(/*/source-url))" as="xs:string"/>
                <url><xsl:value-of select="$url"/></url>
                <mode>binary</mode>
            </config>
        </p:input>
        <p:output name="data" id="source-file"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <header>
                    <name>Content-Disposition</name>
                    <value>attachment</value>
                </header>
            </config>
        </p:input>
        <p:input name="data" href="#source-file"/>
    </p:processor>

</p:config>
