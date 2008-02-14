<!--
    Copyright (C) 2007 Orbeon, Inc.
  
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
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output" debug="out"/>

    <!-- Create config with scope -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <scope>
                    <xsl:value-of select="/input/scope"/>
                </scope>
            </config>
        </p:input>
        <p:output name="data" id="file-serializer-config"/>
    </p:processor>

    <!-- Write if necessary -->
    <p:choose href="#instance">
        <p:when test="/input/operation = ('write', 'write-read')">
            <!-- Serialize XML -->
            <p:processor name="oxf:xml-converter">
                <p:input name="config">
                    <config>
                        <encoding>utf-8</encoding>
                    </config>
                </p:input>
                <p:input name="data" href="#instance#xpointer(/input/text)"/>
                <p:output name="data" id="converted"/>
            </p:processor>
            <p:processor name="oxf:file-serializer">
                <p:input name="config" href="#file-serializer-config"/>
                <p:input name="data" href="#converted"/>
                <p:output name="data" id="url-written"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data" href="#instance#xpointer(/input/url)"/>
                <p:output name="data" id="url-written"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- Read if necessary -->
    <p:choose href="#instance">
        <p:when test="/input/operation = ('read', 'write-read')">
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#url-written"/>
                <p:input name="config">
                    <config xsl:version="2.0">
                        <xsl:copy-of select="/url"/>
						<content-type>text/plain</content-type>
						<force-content-type>true</force-content-type>
                    </config>
                </p:input>
                <p:output name="data" id="url-generator-config"/>
            </p:processor>
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="#url-generator-config"/>
                <p:output name="data" id="binary-read" debug="binary read"/>
            </p:processor>
            <p:processor name="oxf:to-xml-converter">
                <p:input name="data" href="#binary-read"/>
                <p:input name="config">
                    <config/>
                </p:input>
                <p:output name="data" id="text-read" debug="text read"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data"><text/></p:input>
                <p:output name="data" id="text-read"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- Build result with text read and URL -->
    <p:processor name="oxf:xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="url" href="#url-written"/>
        <p:input name="text" href="#text-read"/>
        <p:input name="config">
            <output xsl:version="2.0">
                <xsl:copy-of select="doc('input:url')"/>
                <xsl:copy-of select="doc('input:text')"/>
            </output>
        </p:input>
        <p:output name="data" ref="data" debug="returned"/>
    </p:processor>

</p:config>
