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

    <p:for-each href="book.xml" select="(/*/menu[descendant::menu-item[not(@printed-book='false')]])" id="aggregated-document" root="document">

        <p:for-each href="current()" select="(//menu-item[@href and not(@printed-book='false') and not(ends-with(@href, '.pdf')) and not (starts-with(@href, 'http:'))])" id="aggregated-document-1" root="document">

            <p:processor name="oxf:identity">
                <p:input name="data" href="current()"/>
                <p:output name="data" id="url"/>
            </p:processor>

            <!-- Create instance -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#url"/>
                <p:input name="config">
                    <form xsl:version="2.0">
                        <page><xsl:value-of select="/*/@href"/></page>
                    </form>
                </p:input>
                <p:output name="data" id="instance"/>
            </p:processor>

            <!-- Call model -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="doc-model.xpl"/>
                <p:input name="instance" href="#instance"/>
                <p:output name="data" id="source-document"/>
            </p:processor>

            <!-- Convert into section -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#source-document"/>
                <p:input name="config">
                    <xsl:stylesheet version="2.0">
                        <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                        <xsl:template match="/">
                            <section>
                                <title><xsl:value-of select="/document/header/title"/></title>
                                <xsl:apply-templates select="/document/body/*"/>
                            </section>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" ref="aggregated-document-1"/>
            </p:processor>

        </p:for-each>

        <p:processor name="oxf:xslt">
            <p:input name="data" href="#aggregated-document-1"/>
            <p:input name="current-menu" href="current()"/>
            <p:input name="config">
                <xsl:stylesheet version="2.0">
                    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                    <xsl:template match="/">
                        <section>
                            <title><xsl:value-of select="doc('input:current-menu')/*/@label"/></title>
                            <xsl:apply-templates select="/document/*"/>
                        </section>
                    </xsl:template>
                </xsl:stylesheet>
            </p:input>
            <p:output name="data" ref="aggregated-document"/>
        </p:processor>

    </p:for-each>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#aggregated-document"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="/">
                    <document>
                        <header>
                            <title>The Orbeon Forms Book</title>
                        </header>
                        <body>
                            <xsl:apply-templates select="/document/*"/>
                        </body>
                    </document>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="final-document"/>
    </p:processor>

    <!-- Process file -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="process-static-file.xpl"/>
        <p:input name="source-document" href="#final-document"/>
        <p:input name="instance">
            <form/>
        </p:input>
        <p:output name="html" id="html-data"/>
    </p:processor>
    
<!--    <p:processor name="oxf:xupdate">-->
<!--        <p:input name="config">-->
<!--            <xu:transformations xmlns:xu="http://www.xmldb.org/xupdate"/>-->
<!--        </p:input>-->
<!--        <p:input name="data" href="#html-data"/>-->
<!--        <p:output name="data" id="html-data-2"/>-->
<!--    </p:processor>-->

    <!-- Remove all namespace nodes (hack to fix the work done by the QName converter) -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#html-data"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="*" priority="1000">
                    <xsl:element name="{local-name()}">
                        <xsl:copy-of select="@*"/>
                        <xsl:apply-templates/>
                    </xsl:element>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="html-data-no-namespaces"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#html-data-no-namespaces"/>
<!--        <p:input name="data" href="#html-data"/>-->
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="/">
                    <xsl:apply-templates/>
                </xsl:template>
                <xsl:template match="body">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>

                        <table style="width: 100%; border: 0px; table-layout: fixed;" class="maincontent">
<!--                            <thead style="display: table-header-group;">-->
<!--                                <tr>-->
<!--                                    <th>This is a header</th>-->
<!--                                </tr>-->
<!--                            </thead>-->
                            <tbody style="display:table-row-group;">
                                <tr>
                                    <td>
                                        <xsl:apply-templates select="(//h2)[1]/../*"/>
                                    </td>
                                </tr>
                            </tbody>
<!--                            <tfoot style="display: table-footer-group;">-->
<!--                                <tr>-->
<!--                                    <th>This is a footer</th>-->
<!--                                </tr>-->
<!--                            </tfoot>-->
                        </table>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="h1">
                    <h2>Table of Contents</h2>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="final-html"/>
    </p:processor>

    <!-- Convert and serialize to XML -->
    <p:processor name="oxf:html-converter">
        <p:input name="config">
            <config>
                <public-doctype>-//W3C//DTD HTML 4.0//EN</public-doctype>
                <version>4.01</version>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#final-html"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <p:processor name="oxf:file-serializer">
        <p:input name="config">
            <config>
                <directory>build/doc/reference</directory>
                <file>single-file-doc.html</file>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>

