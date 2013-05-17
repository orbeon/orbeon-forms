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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <!-- Extract request path-->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config><include>/request/request-path</include></config>
        </p:input>
        <p:output name="data" id="request-path"/>
    </p:processor>

    <p:choose href="#request-path">
        <p:when test="/*/request-path = '/xforms-server-submit'">
            <!-- Hook-up XForms Submission Server for this special path -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="oxf:/ops/xforms/xforms-server-submit.xpl"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Dispatch to examples -->

            <!-- Extract example id (the first part of the path) -->
            <p:processor name="oxf:perl5-matcher">
                <p:input name="data" href="#request-path"/>
                <p:input name="config"><regexp>/(direct/|examples-standalone/)?([^/]*).*</regexp></p:input>
                <p:output name="data" id="regexp-result"/>
            </p:processor>

            <p:processor name="oxf:xslt">
                <p:input name="data" href="#regexp-result"/>
                <p:input name="config">
                    <config xsl:version="2.0">
                        <xsl:variable name="examples-list" select="document('examples-list.xml')" as="document-node()"/>
                        <xsl:variable name="path-prefix" select="/*/group[1]" as="xs:string"/>
                        <xsl:variable name="example-id" select="/*/group[2]" as="xs:string"/>
                        <xsl:variable name="example" select="$examples-list//example[@id = $example-id]" as="element()"/>
                        <url><xsl:value-of select="concat('oxf:/',
                                if ($path-prefix = 'examples-standalone/') then $path-prefix else 'examples/',
                                if ($example/@path) then $example/@path else $example/@id, '/page-flow.xml')"/></url>
                    </config>
                </p:input>
                <p:output name="data" id="url-config"/>
            </p:processor>

            <!-- Fetch page-flow.xml -->
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="#url-config"/>
                <p:output name="data" id="page-flow"/>
            </p:processor>

            <!-- Execute page flow -->
            <p:processor name="oxf:page-flow">
                <p:input name="controller" href="#page-flow"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
