<!--
  Copyright (C) 2009 Orbeon, Inc.

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
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <p:param name="document" type="input"/>
    <p:param name="response" type="output"/>

    <!-- Update input document -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#document"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="xforms:model[1]">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <!-- Force client state mode -->
                        <xsl:attribute name="xxforms:state-handling">client</xsl:attribute>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="updated-document"/>
    </p:processor>

    <!-- Native XForms Initialization -->
    <p:processor name="oxf:xforms-to-xhtml">
        <p:input name="annotated-document" href="#updated-document"/>
        <p:input name="namespace"><request><container-namespace/></request></p:input>
        <p:input name="data"><null xsi:nil="true"/></p:input>
        <p:input name="instance"><null xsi:nil="true"/></p:input>
        <p:output name="document" id="xhtml"/>
    </p:processor>

    <!-- Format -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#xhtml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">

                <!-- Handle body content only -->
                <xsl:template match="/">
                    <controls>
                        <xsl:apply-templates select="/xhtml:html/xhtml:body/*"/>
                    </controls>
                </xsl:template>

                <!-- Keep repeat templates, controls, LHHA, and elements with MIP classes like repeat/group elements in tables
                     Also keep content of elements with class xxforms-test-preserve-content -->
                <xsl:template match="xhtml:*[tokenize(@class, '\s+') = ('xforms-repeat-template', 'xforms-control', 'xforms-label',
                                        'xforms-hint', 'xforms-help', 'xforms-alert', 'xforms-help-image', 'xforms-group', 'xforms-group-begin-end',
                                        'xforms-invalid', 'xforms-required', 'xforms-readonly', 'xxforms-test-preserve-content')]">
                    <xsl:copy-of select="."/>
                </xsl:template>

                <!-- Recurse into children elements -->
                <xsl:template match="*">
                    <xsl:apply-templates select="*"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="response"/>
    </p:processor>

</p:config>
