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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xforms-utils="java:org.orbeon.oxf.processor.xforms.XFormsUtils"
    xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

    <xsl:variable name="model" select="doc('oxf:model')/xforms:model" as="element()"/>

    <!-- Form Controls -->

    <xsl:template match="xforms:input">
        <xhtml:input type="text" name="{xxforms:encrypt-name(@xxforms:name)}" value="{@xxforms:value}">
            <xsl:call-template name="copy-other-attributes"/>
        </xhtml:input>
    </xsl:template>

    <xsl:template match="xforms:secret">
        <xhtml:input type="password" name="{xxforms:encrypt-name(@xxforms:name)}" value="{@xxforms:value}">
            <xsl:call-template name="copy-other-attributes"/>
        </xhtml:input>
    </xsl:template>

    <xsl:template match="xforms:textarea">
        <xhtml:textarea name="{xxforms:encrypt-name(@xxforms:name)}">
            <xsl:call-template name="copy-other-attributes"/>
            <xsl:value-of select="@xxforms:value"/>
        </xhtml:textarea>
    </xsl:template>

    <xsl:template match="xforms:output">
        <xsl:value-of select="@xxforms:value"/>
        <xxforms:hidden xxforms:name="{@xxforms:name}" xxforms:value="{@xxforms:value}"/>
    </xsl:template>

    <xsl:template match="xforms:upload">
<!--        <xsl:message>-->
<!--            <form>-->
<!--                <xsl:copy-of select="."/>-->
<!--            </form>-->
<!--        </xsl:message>-->
        <xhtml:input type="file" name="$upload^{xxforms:encrypt-name(@xxforms:name)}^{@xxforms:value}^{xxforms:encrypt-name(xforms:filename/@xxforms:name)}^{xforms:filename/@xxforms:value}^{xxforms:encrypt-name(xforms:mediatype/@xxforms:name)}^{xforms:mediatype/@xxforms:value}^{xxforms:encrypt-name(xxforms:size/@xxforms:name)}^{xforms:size/@xxforms:value}">
            <xsl:call-template name="copy-other-attributes"/>
        </xhtml:input>
    </xsl:template>

    <xsl:template match="xforms:range">
        <!-- TODO -->
        <xsl:message terminate="yes">Range control is not supported</xsl:message>
    </xsl:template>

    <xsl:template match="xforms:trigger">
        <!-- TODO -->
        <xsl:message terminate="yes">Trigger control is not supported</xsl:message>
    </xsl:template>

    <xsl:template match="xforms:submit">
        <xsl:variable name="name" as="xs:string" select="xxforms:submit-name(.)"/>
        <xsl:variable name="name-javascript" as="xs:string" select="replace($name, '''', '\\''')"/>
        <xsl:variable name="form-id" as="xs:string" select="xxforms:form-id(ancestor::xforms:group[last()])"/>
        <xsl:choose>
            <xsl:when test="@xxforms:appearance = 'link'">
                <xhtml:a href="" onclick="document.getElementById('wsrp_rewrite_action_{$form-id}').name += '{$name-javascript}';
                        document.forms['wsrp_rewrite_form_{$form-id}'].submit();
                        event.returnValue=false;
                        return false">
                    <xsl:call-template name="copy-other-attributes"/>
                    <xsl:value-of select="xforms:label"/>
                </xhtml:a>
            </xsl:when>
            <xsl:when test="@xxforms:appearance = 'image'">
                <xhtml:input type="image" name="{$name}" src="{xxforms:img/@src}" alt="{xforms:label}">
                    <xsl:call-template name="copy-other-attributes"/>
                    <xsl:copy-of select="xxforms:img/@* except xxforms:img/@src"/>
                </xhtml:input>
            </xsl:when>
            <xsl:otherwise>
                <xhtml:input type="submit" name="{$name}" value="{xforms:label}">
                    <xsl:call-template name="copy-other-attributes"/>
                </xhtml:input>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="xforms:select">
        <xsl:variable name="name" as="xs:string" select="xxforms:encrypt-name(@xxforms:name)"/>
        <xsl:variable name="values" as="xs:string*" select="tokenize(@xxforms:value, ' ')"/>
        <xsl:choose>
            <xsl:when test="not(@appearance) or @appearance = 'compact'">
                <xhtml:select name="{$name}" multiple="multiple">
                    <xsl:call-template name="copy-other-attributes"/>
                    <xsl:apply-templates>
                        <xsl:with-param name="name" select="$name" tunnel="yes"/>
                        <xsl:with-param name="values" select="$values" tunnel="yes"/>
                        <xsl:with-param name="appearance" select="if (@appearance) then xs:string(@appearance) else 'compact'" tunnel="yes"/>
                        <xsl:with-param name="select-type" select="'select'" tunnel="yes"/>
                    </xsl:apply-templates>
                </xhtml:select>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates>
                    <xsl:with-param name="name" select="$name" tunnel="yes"/>
                    <xsl:with-param name="values" select="$values" tunnel="yes"/>
                    <xsl:with-param name="appearance" select="xs:string(@appearance)" tunnel="yes"/>
                    <xsl:with-param name="select-type" select="'select'" tunnel="yes"/>
                </xsl:apply-templates>
            </xsl:otherwise>
        </xsl:choose>
        <xxforms:hidden xxforms:name="{@xxforms:name}" xxforms:value=""/>
    </xsl:template>

    <xsl:template match="xforms:select1">
        <xsl:variable name="name" as="xs:string" select="xxforms:encrypt-name(@xxforms:name)"/>
        <xsl:variable name="value" as="xs:string" select="@xxforms:value"/>

        <xsl:choose>
            <xsl:when test="not(@appearance) or @appearance = 'compact' or @appearance = 'minimal'">
                <xhtml:select name="{$name}">
                    <xsl:call-template name="copy-other-attributes"/>
                    <xsl:apply-templates>
                        <xsl:with-param name="name" select="$name" tunnel="yes"/>
                        <xsl:with-param name="value" select="$value" tunnel="yes"/>
                        <xsl:with-param name="appearance" select="if (@appearance) then xs:string(@appearance) else 'compact'" tunnel="yes"/>
                        <xsl:with-param name="select-type" select="'select1'" tunnel="yes"/>
                    </xsl:apply-templates>
                </xhtml:select>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates>
                    <xsl:with-param name="name" select="$name" tunnel="yes"/>
                    <xsl:with-param name="value" select="$value" tunnel="yes"/>
                    <xsl:with-param name="appearance" select="xs:string(@appearance)" tunnel="yes"/>
                    <xsl:with-param name="select-type" select="'select1'" tunnel="yes"/>
                </xsl:apply-templates>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="xforms:item">
        <xsl:param name="name" as="xs:string" tunnel="yes"/>
        <xsl:param name="values" as="xs:string*" select="()" tunnel="yes"/>
        <xsl:param name="value" as="xs:string" select="''" tunnel="yes"/>
        <xsl:param name="appearance" as="xs:string?" tunnel="yes"/>
        <xsl:param name="select-type" as="xs:string" tunnel="yes"/>

        <xsl:choose>
            <xsl:when test="$select-type = 'select'">
                <xsl:choose>
                    <xsl:when test="$appearance = 'compact'">
                        <!-- List -->
                        <xhtml:option value="{xforms:value}">
                            <xsl:call-template name="copy-other-attributes"/>
                            <xsl:if test="$values = xs:string(xforms:value)">
                                <xsl:attribute name="selected" select="'selected'"/>
                            </xsl:if>
                            <xsl:value-of select="xforms:label"/>
                        </xhtml:option>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- Check box -->
                        <xhtml:input type="checkbox" name="{$name}^{escape-uri(xforms:value, true())}" value="on">
                            <xsl:call-template name="copy-other-attributes"/>
                            <xsl:if test="$values = xs:string(xforms:value)">
                                <xsl:attribute name="checked" select="'checked'"/>
                            </xsl:if>
                            <xsl:value-of select="xforms:label"/>
                        </xhtml:input>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
            <xsl:otherwise>
                <xsl:choose>
                    <xsl:when test="$appearance = 'compact' or $appearance = 'minimal'">
                        <!-- Combo box -->
                        <xhtml:option value="{xforms:value}">
                            <xsl:call-template name="copy-other-attributes"/>
                            <xsl:if test="$value = xs:string(xforms:value)">
                                <xsl:attribute name="selected" select="'selected'"/>
                            </xsl:if>
                            <xsl:value-of select="xforms:label"/>
                        </xhtml:option>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- Radio button -->
                        <xhtml:input type="radio" name="{$name}" value="{xforms:value}">
                            <xsl:call-template name="copy-other-attributes"/>
                            <xsl:if test="$value = xs:string(xforms:value)">
                                <xsl:attribute name="checked" select="'checked'"/>
                            </xsl:if>
                            <xsl:value-of select="xforms:label"/>
                        </xhtml:input>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Alert -->
    <xsl:template match="xforms:*[@xxforms:valid = 'false' and local-name() != 'group']" priority="3">
        <xsl:param name="show-errors" tunnel="yes"/>
        <xsl:choose>
            <xsl:when test="$show-errors = 'false'">
                <xsl:next-match/>
            </xsl:when>
            <xsl:otherwise>
                <xhtml:table border="0" cellpadding="0" cellspacing="0" style="margin: 0">
                    <xhtml:tr>
                        <xhtml:td><xsl:next-match/></xhtml:td>
                        <xhtml:td style="padding-left: 1em; color: red">
                            <xhtml:img src="/images/error.gif"/>
                        </xhtml:td>
                    </xhtml:tr>
                </xhtml:table>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="xhtml:body">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:variable name="invalid-controls" as="element()*" select=".//xforms:*[@xxforms:valid = 'false'
                and not(ancestor-or-self::xforms:*[@xxforms:relevant = 'false'])]"/>
            <!-- FIXME: this test will probably not be very efficient on large documents -->
            <xsl:if test="exists($invalid-controls) and not((.//xforms:group)[1]/@xxforms:show-errors = 'false')">
                <f:alerts>
                    <xsl:for-each select="$invalid-controls">
                        <xsl:if test="xforms:alert">
                            <f:alert><xsl:copy-of select="xforms:alert/node()"/></f:alert>
                        </xsl:if>
                    </xsl:for-each>
                </f:alerts>
            </xsl:if>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <!-- Help, Hint -->
    <xsl:template match="xforms:*[xforms:help]" priority="2">
        <xhtml:table border="0" cellpadding="0" cellspacing="0" style="margin: 0px">
            <xhtml:tr>
                <!-- Display control -->
                <xhtml:td><xsl:next-match/></xhtml:td>
                <!-- Display help -->
                <xhtml:td style="padding-left: 1em">
                    <xsl:variable name="help" as="element()">
                        <html>
                            <head>
                                <title>Help</title>
                                <link rel="stylesheet" type="text/css" href="oxf-theme/style/theme.css"/>
                            </head>
                            <body style="background: white">
                                <table border="0" cellpadding="0" cellspacing="0" width="100%">
                                    <tr>
                                        <td valign="top"><img src="IMAGEURL"/></td>
                                        <td valign="top" style="padding-left: 1em"><xsl:copy-of select="xforms:help"/></td>
                                    </tr>
                                    <tr>
                                        <td colspan="2" align="right" style="padding-top: .5em">
                                            <a href="javascript:window.close()">Close</a>
                                        </td>
                                    </tr>
                                </table>
                            </body>
                        </html>
                    </xsl:variable>
                    <xsl:variable name="help-string-nourl" as="xs:string" select="replace(saxon:serialize($help, ''), '''', '\\''')"/>
                    <!-- We replace the URL here, otherwise &amp; gets escaped once more and rewriting cannot take place -->
                    <xsl:variable name="help-string" as="xs:string" select="replace($help-string-nourl, 'IMAGEURL', context:rewriteResourceURL('/images/bulb-large.gif'))"/>

                    <xhtml:img src="/images/help-small.gif" onclick="var w = window.open('', '_blank',
                        'height=150,width=250,status=no,toolbar=no,menubar=no,location=no,dependent=yes');
                        var d = w.document; d.write('{replace(replace($help-string, '&#xd;', ' '), '&#xa;', ' ')}'); d.close();" style="cursor: pointer"/>
                </xhtml:td>
            </xhtml:tr>
        </xhtml:table>
    </xsl:template>

    <xsl:template match="xforms:*[xforms:hint]" priority="4">
        <xsl:variable name="content"><xsl:next-match/></xsl:variable>
        <xhtml:div title="{xforms:hint}" style="padding: 0px; margin: 0px">
            <xsl:copy-of select="$content"/>
        </xhtml:div>
    </xsl:template>

    <!-- XForms Conditionals -->
    <xsl:template match="xxforms:if">
         <xsl:if test="@xxforms:value = 'true'">
             <xsl:apply-templates/>
         </xsl:if>
     </xsl:template>

    <xsl:template match="xxforms:choose">
        <xsl:variable name="when-true" select="xxforms:when[@xxforms:value = 'true']"/>
        <xsl:for-each select="$when-true">
            <xsl:apply-templates select="node()"/>
        </xsl:for-each>
        <xsl:if test="count($when-true) = 0">
            <xsl:apply-templates select="xxforms:otherwise/node()"/>
        </xsl:if>
    </xsl:template>

    <!-- Root Group Module -->
    <xsl:template match="xforms:group[not(ancestor::xforms:group)]">
        <xsl:variable name="form-id" as="xs:string" select="xxforms:form-id(.)"/>
        <xhtml:form id="wsrp_rewrite_form_{$form-id}" xxforms:contains-hidden="true">
            <xsl:call-template name="copy-other-attributes"/>
            <!-- Add submission attributes -->
            <xsl:variable name="submission" as="element()?" select="$model/xforms:submission"/>
            <xsl:if test="$submission/@method"><xsl:attribute name="method" select="$submission/@method"/></xsl:if>
            <xsl:if test="$submission/@action"><xsl:attribute name="action" select="$submission/@action"/></xsl:if>
            <xsl:if test="$submission/@encoding"><xsl:attribute name="enctype" select="$submission/@encoding"/></xsl:if>
            <!-- Hidden field set by JavaScript when the form is submitted -->
            <xhtml:input type="hidden" id="wsrp_rewrite_action_{$form-id}" name="" value=""/>
            <xhtml:input type="hidden" name="$submitted" value="true"/>
            <xsl:apply-templates>
                <xsl:with-param name="show-errors" select="@xxforms:show-errors" tunnel="yes"/>
            </xsl:apply-templates>
        </xhtml:form>
    </xsl:template>

    <xsl:template match="xforms:group[ancestor::xforms:group]">
        <!-- Don't output sub-xforms:group elements -->
        <xsl:apply-templates/>
    </xsl:template>

    <!-- Do not render if non-relevant -->
    <xsl:template match="xforms:*[@xxforms:relevant = 'false']" priority="4">
        <xsl:apply-templates select="." mode="no-rendering"/>
    </xsl:template>

    <!-- If we do not render a control, we need to output a hidden field instead -->
    <xsl:template match="xforms:input | xforms:secret | xforms:textarea
            | xforms:upload | xforms:filename | xforms:mediatype | xxforms:size
            | xforms:range | xforms:select | xforms:select1 | xforms:output" mode="no-rendering">
        <xxforms:hidden xxforms:name="{@xxforms:name}" xxforms:value="{@xxforms:value}"/>
    </xsl:template>
    <xsl:template match="text()" mode="no-rendering"/>

    <!-- If those have not been processed, ignore them -->
    <xsl:template match="xforms:hint|xforms:help"/>

    <xsl:template match="xforms:*">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template name="copy-other-attributes">
        <xsl:copy-of select="@* except (@xxforms:* | @*[namespace-uri() = ''])"/>
    </xsl:template>
    
    <!-- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -->

    <!-- Generates a unique id for the given form. The id is equal to the position of the form
         in the page starting with 1. -->
    <xsl:function name="xxforms:form-id">
        <xsl:param name="element" as="element()"/>
        <xsl:value-of select="count($element/preceding::xforms:group[not(ancestor::xforms:group)]) + 1"/>
    </xsl:function>

    <xsl:function name="xxforms:submit-name">
        <xsl:param name="submit-control" as="element()"/>

        <xsl:variable name="action-strings" as="xs:string*">
            <xsl:for-each select="$submit-control/(xforms:setvalue | xforms:insert | xforms:delete)">
                <!-- Optional parameters -->
                <xsl:variable name="ref-param"
                    select="if (@ref) then ('ref', escape-uri(@xxforms:ref-xpath, true())) else ()"/>
                <xsl:variable name="value-param"
                    select="if (@value) then ('value', escape-uri(@xxforms:value-xpath, true())) else ()"/>
                <xsl:variable name="nodeset-param"
                    select="if (@nodeset) then ('nodeset', escape-uri(@xxforms:nodeset-xpath, true())) else ()"/>
                <xsl:variable name="at-param"
                    select="if (@at) then ('at', escape-uri(@xxforms:at-xpath, true())) else ()"/>
                <xsl:variable name="content-param" select="('content', escape-uri(string(.), true()))"/>
                <!-- Join parameters -->
                <xsl:sequence
                    select="escape-uri(string-join((local-name(), $ref-param, $value-param, $nodeset-param,
                        $at-param, $content-param), '&amp;'), true())"/>
            </xsl:for-each>
        </xsl:variable>

        <xsl:variable name="prefix" as="xs:string"
            select="if ($submit-control/@xxforms:appearance = 'image') then '$actionImg^' else '$action^'"/>
        <xsl:sequence select="concat($prefix, string-join($action-strings, '&amp;'))"/>
    </xsl:function>

    <xsl:function name="xxforms:encrypt-name" as="xs:string">
        <xsl:param name="name" as="xs:string"/>
        <xsl:value-of select="if (xforms-utils:is-name-encryption-enabled())
            then xforms-utils:encrypt($name) else $name"/>
    </xsl:function>

</xsl:stylesheet>
