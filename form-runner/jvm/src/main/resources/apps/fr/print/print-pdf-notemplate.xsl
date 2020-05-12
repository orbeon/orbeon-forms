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
<xsl:transform
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <xsl:variable name="app"              select="doc('input:parameters')/*/app/string()"/>
    <xsl:variable name="form"             select="doc('input:parameters')/*/form/string()"/>
    <xsl:variable name="mode"             select="doc('input:parameters')/*/mode/string()"/>
    <xsl:variable name="hyperlinks"       select="p:property(string-join(('oxf.fr.detail.pdf.hyperlinks', $app, $form), '.')) = true()"/>

    <!-- MAYBE: Support URL parameters as well for #4206. Should they be trusted? -->
    <xsl:variable name="metadata"         select="frf:metadataInstanceRootOpt(doc('input:xforms'))"/>
    <xsl:variable name="page-orientation" select="frf:optionFromMetadataOrPropertiesXPath($metadata, 'rendered-page-orientation', $app, $form, $mode)"/>
    <xsl:variable name="page-size"        select="frf:optionFromMetadataOrPropertiesXPath($metadata, 'rendered-page-size',        $app, $form, $mode)"/>

    <!--
        Remove portlet namespace from ids if present. Do this because in a portlet environment, the CSS
        retrieved by oxf:xhtml-to-pdf doesn't know about the namespace. Not doing so, the CSS won't apply
        and also this can cause a ClassCastException in Flying Saucer.
     -->
    <xsl:template match="@id | @for" mode="#all">
        <xsl:attribute name="{name()}" select="substring-after(., doc('input:request')/*/container-namespace)"/>
    </xsl:template>

    <!-- While we are at it filter out scripts as they won't be used -->
    <xsl:template match="*:script | *:noscript" mode="#all"/>

    <xsl:template match="xh:html/xh:head" mode="#all">
        <head>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
            <!-- https://github.com/orbeon/orbeon-forms/issues/4206 -->
            <style type="text/css">
                @page {
                    size: <xsl:value-of select="string-join(($page-size, $page-orientation), ' ')"/>;
                }
            </style>
        </head>
    </xsl:template>

    <!-- https://github.com/orbeon/orbeon-forms/issues/3096 -->
    <xsl:template
        match="
            *[
                p:has-class('xforms-label')
            ]/*:br[
                (exists(preceding-sibling::*) or p:non-blank(..)) and (: avoid removing a single `br` :)
                empty(following-sibling::*)                       and
                p:is-blank(following-sibling::text())
            ]"
        mode="#all"/>

    <!--
        Hyperlinks, see:

        https://github.com/orbeon/orbeon-forms/issues/1694
        https://github.com/orbeon/orbeon-forms/issues/1288
        https://github.com/orbeon/orbeon-forms/issues/1515
        https://github.com/orbeon/orbeon-forms/issues/264
     -->
    <xsl:template match="*:a" mode="#all">
        <xsl:element name="{local-name()}">
            <!--
                Filter all attributes specific to <a> as per HTML 5 except @href unless excluded.

                HTML 5 says [1]: "If the a element has no href attribute, then the element represents a
                placeholder for where a link might otherwise have been placed". So instead of putting a
                <span> when we don't want links, we still put an <a> without the @href attribute. That
                probably indicates the intent better.

                [1] http://www.whatwg.org/specs/web-apps/current-work/multipage/text-level-semantics.html#the-a-element
             -->
            <xsl:apply-templates
                select="@* except (@href[not($hyperlinks)], @shape, @target, @download, @ping, @rel, @hreflang, @type) | node()"
                mode="#current"/>
        </xsl:element>
    </xsl:template>

    <!-- Hyperlink URLs in fields -->
    <xsl:template
        match="
            *:pre[
                p:has-class('xforms-textarea', ..)
            ]
            |
            *:span[
                p:has-class('xforms-field') and p:has-class('xforms-input', ..)
            ]"
        mode="#all">

        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@*" mode="#current"/>
            <xsl:apply-templates select="saxon:parse(frf:hyperlinkURLs(string(), $hyperlinks))" mode="#current"/>
        </xsl:element>
    </xsl:template>

    <!-- See https://github.com/orbeon/orbeon-forms/issues/2347 -->
    <xsl:template
        priority="10"
        match="
            *:span[
                p:has-class('xforms-field') and empty(*) and normalize-space() = ''
            ]"
        mode="#all">
         <xsl:element name="{local-name()}">
             <xsl:apply-templates select="@*" mode="#current"/>
             <xsl:text>&#160;</xsl:text>
         </xsl:element>
     </xsl:template>

    <!-- Start grid content -->
    <xsl:template match="*:div[p:has-class('xbl-fr-grid')]" mode="#all">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@* | node()" mode="in-grid"/>
        </xsl:element>
    </xsl:template>

    <!-- These are unneeded and can make iText choke (values too long) -->
    <xsl:template match="*:input[@type = 'hidden']" mode="#all"/>

    <!-- Remove xforms-initially-hidden class on the form, normally removed by the script -->
    <xsl:template match="*:form" mode="#all">
        <xsl:element name="{local-name()}">
            <xsl:attribute name="class" select="string-join(p:classes()[. != 'xforms-initially-hidden'], ' ')"/>
            <xsl:apply-templates select="@* except @class | node()" mode="#current"/>
        </xsl:element>
    </xsl:template>

    <!-- For https://github.com/orbeon/orbeon-forms/issues/2573. This appears to solve one of the problems. -->
    <xsl:template
        match="*[
                p:has-class('xforms-label') and
                empty(*)                    and
                p:is-blank(.)
            ]"
        mode="#all"/>

    <!-- Not a big deal but this is extra unneeded markup -->
    <xsl:template
        match="
            *:span[
                p:has-class('xforms-case-selected') and
                empty(*)                            and
                p:is-blank(.)]"
        mode="#all"/>

    <xsl:template
        match="
            *[
                p:has-class('xforms-disabled')                   or
                p:has-class('xforms-case-deselected')            or
                p:has-class('xforms-case-begin-end')             or
                p:has-class('xforms-hidden')                     or
                p:has-class('fr-dialog')                         or
                p:has-class('xforms-error-dialogs')              or
                p:has-class('xforms-help-panel')                 or
                p:has-class('popover-container-right')           or
                p:has-class('popover-container-left')            or
                p:has-class('xforms-template')]"
        mode="#all"/>

    <!-- We could remove the nested `div` but it doesn't seem to make a difference for page breaks -->
    <!--<xsl:template match="*[p:has-class('xbl-fr-section')]/*:div[1][p:has-class('xforms-group')]" mode="#all">-->
        <!--<xsl:apply-templates select="node()" mode="#current"/>-->
    <!--</xsl:template>-->

    <!-- We could remove grouping switches but it doesn't seem to make a difference for page breaks -->
    <!--<xsl:template match="*[p:has-class('xforms-switch')]" mode="#all">-->
        <!--<xsl:apply-templates select="node()" mode="#current"/>-->
    <!--</xsl:template>-->

    <!-- Enabling this breaks the `page-break` CSS rules -->
    <!--<xsl:template match="*:span[p:has-class('xforms-case-selected') and empty(node())]" mode="#all"/>-->

    <!-- Remove all prefixes because Flying Saucer doesn't like them -->
    <xsl:template match="*" mode="#all">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@* | node()" mode="#current"/>
        </xsl:element>
    </xsl:template>

    <!-- Make a copy of useful information so it can be moved, via CSS, to headers and footers -->
    <xsl:template match="*:body" mode="#all">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@*" mode="#current"/>
            <xsl:variable name="title" select="/*/*:head/*:title/string()"/>
            <div class="fr-header-title xforms-hidden"><div><xsl:value-of select="$title"/></div></div>
            <div class="fr-footer-title xforms-hidden"><div><xsl:value-of select="$title"/></div></div>
            <xsl:apply-templates select="node()" mode="#current"/>
        </xsl:element>
    </xsl:template>
</xsl:transform>