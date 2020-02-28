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
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xh="http://www.w3.org/1999/xhtml"
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
                <xsl:template match="xf:model[1]">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <!-- Force client state mode -->
                        <xsl:attribute name="xxf:state-handling">client</xsl:attribute>
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
        <p:input name="data"><null xsi:nil="true"/></p:input>
        <p:input name="instance"><null xsi:nil="true"/></p:input>
        <p:output name="document" id="xhtml"/>
    </p:processor>

    <!-- Format -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#xhtml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">

                <!-- All elements which mark the start of a repeat/switch/group -->
                <xsl:variable
                    name="start-repeat-group"
                    select="//xh:*[p:classes() = 'xforms-repeat-begin-end' and starts-with(@id, 'repeat-begin-')
                                or p:classes() = 'xforms-group-begin-end'  and starts-with(@id, 'group-begin-')
                                or p:classes() = 'xforms-case-begin-end'   and starts-with(@id, 'xforms-case-begin-')]"/>

                <!-- All elements which are contained within repeat/switch/group delimiters (excluding begin/end markers)-->
                <xsl:variable
                    name="repeat-group-content"
                    select="for $s in $start-repeat-group
                            return ($s/following-sibling::* intersect $s/following-sibling::*[@id = replace($s/@id, '^(repeat|group)-begin-', '$1-end-')]/preceding-sibling::*)/generate-id()"/>

                <!-- Handle body content only -->
                <xsl:template match="/">
                    <controls>
                        <xsl:apply-templates select="/xh:html/xh:body/*"/>
                    </controls>
                </xsl:template>

                <!-- Swallow item templates -->
                <xsl:template match="xh:span[p:classes() = 'xforms-template']"/>

                <!-- Keep controls, LHHA, and elements with MIP classes like repeat/group elements in tables
                     Also keep content of elements with class xxforms-test-preserve-content and content of repeats. -->
                <xsl:template match="xh:*[p:classes() = ('xforms-control', 'xforms-label',
                                        'xforms-hint', 'xforms-help', 'xforms-alert', 'xforms-group', 'xforms-switch', 'xforms-group-begin-end',
                                        'xforms-invalid', 'xforms-required', 'xforms-readonly', 'xxforms-test-preserve-content',
                                        'xforms-repeat-begin-end', 'xforms-repeat-delimiter', 'xforms-repeat-selected-item-1', 'xforms-repeat-selected-item-2',
                                        'xforms-repeat-selected-item-3', 'xforms-repeat-selected-item-4') or generate-id(.) = $repeat-group-content]">
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
