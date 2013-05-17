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

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <!-- Read example descriptor from the particular example's directory -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <xsl:variable name="examples-list" select="document('oxf:/examples/examples-list.xml')" as="document-node()"/>
                <xsl:variable name="example-id" select="/*/example-id" as="xs:string"/>
                <xsl:variable name="example" select="$examples-list//example[@id = $example-id]" as="element()"/>
                <url><xsl:value-of select="concat('oxf:/', if ($example/@standalone = 'true') then 'examples-standalone/' else 'examples/', if ($example/@path) then $example/@path else $example/@id, '/example-descriptor.xml')"/></url>
            </config>
        </p:input>
        <p:output name="data" id="url-config"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-config"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
