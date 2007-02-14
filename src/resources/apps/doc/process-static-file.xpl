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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <p:param name="instance" type="input"/>
    <p:param name="source-document" type="input"/>
    <p:param name="html" type="output"/>

    <!-- Call view -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="doc-view.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#source-document"/>
        <p:output name="data" id="xhtml-page"/>
    </p:processor>

    <!-- Apply standard theme -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-type</include>
                <include>/request/request-path</include>
                <include>/request/headers/header[name = 'accept']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#xhtml-page"/>
        <p:input name="request" href="#request"/>
        <p:input name="config" href="oxf:/config/theme-plain.xsl"/>
        <p:output name="data" id="themed-data"/>
    </p:processor>

    <!-- Rewrite URLs -->
    <p:processor name="oxf:xhtml-rewrite">
        <p:input name="rewrite-in" href="#themed-data"/>
        <p:output name="rewrite-out" id="rewritten-data"/>
    </p:processor>

    <!-- Custom rewrite -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#rewritten-data"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="xhtml:link[starts-with(@href, '/config/')]">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="href" select="substring(@href, string-length('/config/') + 1)"/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xhtml:img[starts-with(@src, '/config/')]">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="src" select="substring(@src, string-length('/config/') + 1)"/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xhtml:img[starts-with(@src, '../apps/doc/images/')]">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="src" select="substring(@src, 13)"/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xhtml:a[@href = '/']" priority="100">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="href" select="'index.html'"/>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xhtml:a[@href = '../']" priority="100">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="href" select="'http://www.orbeon.com/ops/'"/>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xhtml:a[starts-with(@href, '#')]" priority="100">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="href" select="@href"/>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xhtml:a[not(contains(@href, '.'))]" priority="50">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="href" select="if (contains(@href, '#'))
                            then concat(substring-before(@href, '#'), '.html#', substring-after(@href, '#'))
                            else concat(@href, '.html')"/>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xhtml:a[contains(@href, 'OPS Tutorial.pdf')]" priority="100">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="href" select="'OPS Tutorial.pdf'"/>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xhtml:a[starts-with(@href, '/goto-example/')]" priority="100">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="href" select="concat('http://www.orbeon.com/ops', @href)"/>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="more-rewritten-data"/>
    </p:processor>

    <!-- Convert to HTML -->
    <p:processor name="oxf:qname-converter">
        <p:input name="config">
            <config>
                <match>
                    <uri>http://www.w3.org/1999/xhtml</uri>
                </match>
                <replace>
                    <uri></uri>
                    <prefix></prefix>
                </replace>
            </config>
        </p:input>
        <p:input name="data" href="#more-rewritten-data"/>
        <p:output name="data" ref="html"/>
    </p:processor>

</p:config>
