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

<!--    <p:processor name="oxf:url-generator">-->
<!--        <p:input name="config">-->
<!--            <config>oxf:/doc/book.xml</config>-->
<!--        </p:input>-->
<!--        <p:output name="data" id="book"/>-->
<!--    </p:processor>-->

    <p:for-each href="oxf:/doc/book.xml" select="//*[@href and @href != '/doc/pages/Tutorial.pdf']" root="dummy" id="dummy">

        <p:processor name="oxf:identity">
            <p:input name="data" href="current()"/>
            <p:output name="data" id="url"/>
        </p:processor>

        <p:processor name="oxf:xslt">
            <p:input name="data" href="#url"/>
            <p:input name="config">
                <xsl:stylesheet version="1.0">
                    <xsl:template match="/">
                        <config>oxf:/doc/pages/<xsl:value-of select="/*/@href"/>.xml</config>
                    </xsl:template>
                </xsl:stylesheet>
            </p:input>
            <p:output name="data" id="url-config"/>
        </p:processor>

        <p:processor name="oxf:url-generator">
            <p:input name="config" href="#url-config"/>
            <p:output name="data" id="body"/>
        </p:processor>


        <p:processor name="oxf:xslt">
            <p:input name="data" href="#url"/>
            <p:input name="config">
                <xsl:stylesheet version="1.0">
                    <xsl:template match="/">
                        <page>oxf:/doc/pages/<xsl:value-of select="/*/@href"/>.xml</page>
                    </xsl:template>
                </xsl:stylesheet>
            </p:input>
            <p:output name="data" id="page"/>
        </p:processor>


        <!-- Skin left navigation bar -->
        <p:processor name="oxf:xslt">
            <p:input name="data" href="aggregate('navigation', #page, oxf:/doc/book.xml)"/>
            <p:input name="config" href="oxf:/doc/skin/xslt/html/book2menu-link-html.xsl"/>
            <p:output name="data" id="menu-skinned"/>
        </p:processor>

        <!-- Skin body (based on page) -->
        <p:processor name="oxf:xslt">
            <p:input name="data" href="#body"/>
            <p:input name="config" href="oxf:/doc/skin/xslt/html/document2html-link-html.xsl"/>
            <p:output name="data" id="body-skinned"/>
        </p:processor>

        <!-- Put left bar and body together -->
        <p:processor name="oxf:xslt">
            <p:input name="data" href="aggregate('site', #menu-skinned, #body-skinned)"/>
            <p:input name="config" href="oxf:/doc/skin/xslt/html/site2xhtml.xsl"/>
            <p:output name="data" id="html"/>
        </p:processor>

        <p:processor name="oxf:xslt">
            <p:input name="data" href="#url"/>
            <p:input name="config">
                <xsl:stylesheet version="1.0">
                    <xsl:template match="/">
                        <config>
                            <content-type>text/html</content-type>
                            <directory>build/doc</directory>
                            <file><xsl:value-of select="/*/@href"/>.html</file>
                        </config>
                    </xsl:template>
                </xsl:stylesheet>
            </p:input>
            <p:output name="data" id="file-config"/>
        </p:processor>

        <p:processor name="oxf:file-serializer">
            <p:input name="config" href="#file-config"/>
            <p:input name="data" href="#html"/>
        </p:processor>

        <p:processor name="oxf:identity">
            <p:input name="data">
                <dummy/>
            </p:input>
            <p:output name="data" ref="dummy"/>
        </p:processor>
    </p:for-each>

    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#dummy"/>
    </p:processor>

</p:config>

