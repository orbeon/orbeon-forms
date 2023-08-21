<!--
    Copyright (C) 2008 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the form -->
    <p:param type="input" name="data"/>

    <p:param type="output" name="data"/>

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../unroll-form.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="unrolled-form"/>
    </p:processor>

    <p:choose href="#unrolled-form">
        <p:when test="
            (: TODO: Form may not allow for disabling PDF template! :)
            not(p:get-request-parameter('fr-use-pdf-template') = 'false') and
            (/*/xh:head//xf:instance[@id = 'fr-form-attachments']/*/pdf/p:trim() != '')">
            <!-- A PDF template is attached to the form and its use is enabled -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="print-pdf-template.xpl"/>
                <p:input name="xforms" href="#unrolled-form"/>
                <p:input name="parameters" href="#instance"/>
                <p:output name="data" id="pdf-data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- No PDF template attached -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="print-pdf-notemplate.xpl"/>
                <p:input name="xforms" href="#unrolled-form"/>
                <p:input name="parameters" href="#instance"/>
                <p:output name="data" id="pdf-data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:choose
            href="#instance"
            xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
            xmlns:version="java:org.orbeon.oxf.common.Version">
        <p:when test="/*/mode = 'tiff' and (version:isPE() or frf:sendError(404))">
            <p:processor name="oxf:pdf-to-image">
                <p:input name="data" href="#pdf-data"/>
                <p:input name="config" transform="oxf:unsafe-xslt" href="#instance">
                    <config xsl:version="2.0">

                        <xsl:variable name="app"  select="/*/app/string()"/>
                        <xsl:variable name="form" select="/*/form/string()"/>

                        <xsl:variable
                            name="compression-type"
                            select="p:property(string-join(('oxf.fr.detail.tiff.compression.type', $app, $form), '.'))"/>

                        <xsl:variable
                            name="compression-quality"
                            select="p:property(string-join(('oxf.fr.detail.tiff.compression.quality', $app, $form), '.'))"/>

                        <xsl:variable
                            name="scale"
                            select="p:property(string-join(('oxf.fr.detail.tiff.scale', $app, $form), '.'))"/>

                        <format>tiff</format>
                        <xsl:if test="$compression-type or $compression-quality">
                            <compression>
                                <xsl:if test="$compression-type">
                                    <type><xsl:value-of select="$compression-type"/></type>
                                </xsl:if>
                                <xsl:if test="$compression-quality">
                                    <quality><xsl:value-of select="$compression-quality"/></quality>
                                </xsl:if>
                            </compression>
                        </xsl:if>
                        <xsl:if test="$scale">
                            <scale><xsl:value-of select="$scale"/></scale>
                        </xsl:if>

                    </config>
                </p:input>
                <p:output name="data" id="rendered"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Just produce the PDF document -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="#pdf-data"/>
                <p:output name="data" id="rendered"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#rendered"/>
        <p:input name="config">
            <document
                xsl:version="2.0"
                disposition-type="inline"
                filename="{p:get-request-parameter('fr-rendered-filename')}">
                <xsl:copy-of select="/document/(@* | node())"/>
            </document>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
