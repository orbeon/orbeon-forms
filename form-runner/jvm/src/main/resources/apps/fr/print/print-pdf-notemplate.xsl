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
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

    <xsl:variable name="app"                    select="doc('input:parameters')/*/app/string()"/>
    <xsl:variable name="form"                   select="doc('input:parameters')/*/form/string()"/>
    <xsl:variable name="mode"                   select="doc('input:parameters')/*/mode/string()"/>
    <xsl:variable name="hyperlinks"             select="p:property(string-join(('oxf.fr.detail.pdf.hyperlinks', $app, $form), '.')) = true()"/>
    <xsl:variable name="long-content-threshold" select="p:property(string-join(('oxf.fr.detail.pdf.long-content-threshold', $app, $form), '.'))"/>

    <xsl:variable
        name="color-mode"
        select="
            (
                p:property(string-join(('oxf.fr.detail.pdf.color-mode', $app, $form), '.'))[
                    . = ('black-and-white', 'keep-field-colors', 'color')
                ],
                'black-and-white'
            )[1]"/>

    <!-- MAYBE: Support URL parameters as well for #4206. Should they be trusted? -->
    <xsl:variable name="metadata"               select="frf:metadataInstanceRootOpt(doc('input:xforms'))"/>
    <xsl:variable name="page-orientation"       select="frf:optionFromMetadataOrPropertiesXPath($metadata, 'rendered-page-orientation', $app, $form, $mode)"/>
    <xsl:variable name="page-size"              select="frf:optionFromMetadataOrPropertiesXPath($metadata, 'rendered-page-size',        $app, $form, $mode)"/>

    <xsl:variable name="title" select="/*/*:head/*:title/string()"/>

    <xsl:variable
        name="empty-grids-ids"
        select="
            //*:div[
                p:has-class('xbl-fr-grid') and
                empty(
                    .//*[
                        p:has-class('fr-grid-td') and (
                            exists(*[not(p:has-class('xforms-disabled'))]) or
                            p:non-blank(.)
                        )
                    ]
                )
            ]/generate-id()"/>

    <!--
        Remove portlet namespace from ids if present. Do this because in a portlet environment, the CSS
        retrieved by oxf:xhtml-to-pdf doesn't know about the namespace. Not doing so, the CSS won't apply
        and also this can cause a ClassCastException in Flying Saucer.
     -->
    <xsl:template match="@id | @for">
        <xsl:attribute name="{name()}" select="substring-after(., doc('input:request')/*/container-namespace)"/>
    </xsl:template>

    <!-- While we are at it filter out scripts as they won't be used -->
    <xsl:template match="*:script | *:noscript"/>

    <xsl:template match="xh:html/xh:head">
        <head>
            <xsl:apply-templates select="@* | node()"/>
            <!-- https://github.com/orbeon/orbeon-forms/issues/4206 -->
            <style type="text/css">
                @page {
                    size: <xsl:value-of select="string-join(($page-size, $page-orientation), ' ')"/>;
                }
            </style>

            <xsl:variable
                xmlns:FormRunnerPdfConfig="java:org.orbeon.oxf.fr.pdf.PdfConfig20231"
                name="pdf-header-footer-config-elem"
                select="FormRunnerPdfConfig:getHeaderFooterConfigXml($app, $form)/*"/>

            <xsl:if test="exists($pdf-header-footer-config-elem/pages/*/*/*/values)">

                <xsl:variable
                    name="pdf-header-footer-details"
                    select="../*:body//*[p:has-class('fr-pdf-header-footer-details')]"/>

                <style type="text/css">
                    <xsl:variable name="css">
                        <xsl:for-each select="$pdf-header-footer-config-elem/pages/*">
                            <xsl:variable name="header-footer-page-type" select="name()"/>
                            <xsl:value-of select="
                                if      ($header-footer-page-type = 'all')   then '@page {'
                                else if ($header-footer-page-type = 'first') then '@page :first {'
                                else if ($header-footer-page-type = 'odd')   then '@page :right {'
                                else if ($header-footer-page-type = 'even')  then '@page :left {'
                                else ''"/>

                            <xsl:for-each select="*">
                                <xsl:variable name="header-footer-type" select="name()"/>
                                <xsl:variable name="prefix" select="
                                    if      ($header-footer-type = 'header') then '@top'
                                    else if ($header-footer-type = 'footer') then '@bottom'
                                    else ''"/>

                                <xsl:for-each select="*">
                                    <xsl:variable name="header-footer-position" select="name()"/>
                                    <xsl:value-of select="concat($prefix, '-', $header-footer-position, ' { content: ')"/>

                                    <xsl:choose>
                                        <xsl:when test="empty(values)">
                                            <xsl:text>''</xsl:text>
                                        </xsl:when>
                                        <xsl:otherwise>

                                            <xsl:variable
                                                name="class-name"
                                                select="
                                                    string-join(
                                                        (
                                                            'fr',
                                                             $header-footer-page-type,
                                                             $header-footer-type,
                                                             $header-footer-position
                                                         ),
                                                         '-'
                                                        )"/>

                                            <!-- NOTE: "Non-Latin characters must be encoded using their Unicode escape
                                                 sequences: for example, \000A9 represents the copyright symbol." (MDN)
                                                 Q: Is this true? -->
                                            <xsl:value-of
                                                select="$pdf-header-footer-details/*[p:has-class($class-name)]/*"/>

                                        </xsl:otherwise>
                                    </xsl:choose>

                                    <xsl:value-of select="';'"/>
                                    <xsl:value-of select="css"/>
                                    <xsl:value-of select="';'"/>

                                    <!-- Apparently, the CSS doesn't combine with the default :( -->
                                    <xsl:choose>
                                        <xsl:when test="$header-footer-type = 'header'">
                                            <xsl:text>
                                                padding-bottom: 10px;
                                                margin-bottom: 0;
                                                vertical-align: bottom;
                                            </xsl:text>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:text>
                                                padding-top: 10px;
                                                margin-top: 0;
                                                vertical-align: top;
                                            </xsl:text>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                    <xsl:text>
                                        font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
                                        font-size: 14.3px;
                                        line-height: 26px;
                                    </xsl:text>
                                    <xsl:value-of select="
                                        if      ($header-footer-position = 'left')   then 'text-align: left;'
                                        else if ($header-footer-position = 'center') then 'text-align: center;'
                                        else 'padding-left: 30px; text-align: right;'"/>

                                    <xsl:value-of select="'}&#xa;'"/>
                                </xsl:for-each>
                            </xsl:for-each>

                            <xsl:value-of select="'}'"/>
                        </xsl:for-each>
                    </xsl:variable>
                    <xsl:value-of select="$css"/>
                </style>
            </xsl:if>

            <bookmarks>
                <xsl:variable name="processed-body-content">
                    <xsl:apply-templates select="/xh:html/*:body/*"/>
                </xsl:variable>
                <xsl:apply-templates select="$processed-body-content" mode="bookmarks"/>
            </bookmarks>
        </head>
    </xsl:template>

    <!-- Produce nested `<bookmark>` elements for Open HTML to PDF, based on `h1`, `h2`… -->
    <xsl:template
        mode="bookmarks"
        priority="2"
        match="
            *[
                *[
                    position() = 1 and
                    matches(local-name(), 'h[1-9]') and
                    (
                        p:has-class('fr-section-iteration-title') or
                        exists(.//span[@class = 'btn-link'])
                    )
                ]
            ]">
        <xsl:variable
            name="title-elem"
            select="
                (
                    *[1][p:has-class('fr-section-iteration-title')]//*[p:has-class('xforms-output-output')],
                    *[1]//span[@class = 'btn-link']
                )[1]"/>
        <bookmark name="{$title-elem}" href="#{$title-elem/@id}">
            <xsl:apply-templates mode="bookmarks" select="*"/>
        </bookmark>

    </xsl:template>
    <xsl:template mode="bookmarks" match="*" priority="1">
        <xsl:apply-templates mode="bookmarks" select="*"/>
    </xsl:template>

    <!-- Buttons in `h1`, etc. take space and we don't need them so replace them -->
    <xsl:template match="*:button">
        <span>
            <xsl:apply-templates select="@* | node()"/>
        </span>
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
       />

    <!--
        Hyperlinks, see:

        https://github.com/orbeon/orbeon-forms/issues/1694
        https://github.com/orbeon/orbeon-forms/issues/1288
        https://github.com/orbeon/orbeon-forms/issues/1515
        https://github.com/orbeon/orbeon-forms/issues/264
     -->
    <xsl:template match="*:a">
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
               />
        </xsl:element>
    </xsl:template>

    <!-- 1. Hyperlink URLs in fields -->
    <!-- 2. Adds a class `fr-long-content` on fields whose value exceeds a certain threshold, so the CSS
            can allow breaking only those fields across pages -->
    <xsl:template
        match="
            *:pre[
                p:has-class('xforms-textarea', ..)
            ]
            |
            *:span[
                p:has-class('xforms-field')
            ]"
       >

        <xsl:element name="{local-name()}">

            <xsl:variable name="new-content" select="saxon:parse(frf:hyperlinkURLs(string(), $hyperlinks))"/>
            <xsl:variable name="is-long-content" select="string-length($new-content) > $long-content-threshold"/>
            <xsl:apply-templates select="@* except @class"/>
            <xsl:if test="$is-long-content or exists(@class)">
                <xsl:attribute name="class" select="
                    string-join(
                        (
                            if ($is-long-content) then 'fr-long-content' else (),
                            @class
                        ),
                        ' '
                    )"/>
            </xsl:if>
            <xsl:apply-templates select="$new-content"/>

        </xsl:element>
    </xsl:template>

    <!-- Add the class `fr-grid-tr-with-long-content` on table rows, then used in CSS t0 avoid breaking the content of
         table rows that don't contain long content (we'll be able to avoid adding this class when we upgrade
         to a PDF rendered that supports `:has()`) -->
    <xsl:template match="*:tr[p:has-class('fr-grid-tr', .)]">
        <xsl:variable name="applied-content">
            <xsl:apply-templates select="node()"/>
        </xsl:variable>
        <xsl:variable
            name="has-long-content"
            select="$applied-content//*[p:has-class('fr-long-content', .)]"/>
        <xsl:element name="{local-name()}">
            <xsl:attribute name="class" select="
                string-join(
                    (
                        if ($has-long-content) then 'fr-grid-tr-with-long-content' else (),
                        @class
                    ),
                    ' '
                )"/>
            <xsl:copy-of select="$applied-content"/>
        </xsl:element>
    </xsl:template>

    <!-- See https://github.com/orbeon/orbeon-forms/issues/2347 -->
    <xsl:template
        priority="10"
        match="
            *:span[
                p:has-class('xforms-field') and empty(*) and normalize-space() = ''
            ]"
       >
         <xsl:element name="{local-name()}">
             <xsl:apply-templates select="@*"/>
             <xsl:text>&#160;</xsl:text>
         </xsl:element>
     </xsl:template>

    <!-- Remove empty grids -->
    <!-- See https://github.com/orbeon/orbeon-forms/issues/5051 -->
    <xsl:template
        match="*:div[p:has-class('xbl-fr-grid') and $empty-grids-ids = generate-id()]"
        priority="100"/>

    <!-- Start grid content -->
    <xsl:template match="*:div[p:has-class('xbl-fr-grid')]">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@* | node()"/>
        </xsl:element>
    </xsl:template>

    <!-- These are unneeded and can make iText choke (values too long) -->
    <xsl:template match="*:input[@type = 'hidden']"/>

    <!-- Remove xforms-initially-hidden class on the form, normally removed by the script -->
    <xsl:template match="*:form">
        <xsl:element name="{local-name()}">
            <xsl:attribute name="class" select="string-join(p:classes()[. != 'xforms-initially-hidden'], ' ')"/>
            <xsl:apply-templates select="@* except @class | node()"/>
        </xsl:element>
    </xsl:template>

    <!-- For https://github.com/orbeon/orbeon-forms/issues/2573. This appears to solve one of the problems. -->
    <xsl:template
        match="*[
                p:has-class('xforms-label') and
                empty(*)                    and
                p:is-blank(.)
            ]"
       />

    <!-- Not a big deal but this is extra unneeded markup -->
    <xsl:template
        match="
            *:span[
                p:has-class('xforms-case-selected') and
                empty(*)                            and
                p:is-blank(.)]"
       />

    <!-- https://github.com/orbeon/orbeon-forms/issues/4596 -->
    <xsl:template
        priority="10"
        match="
            *[
                p:has-class('xforms-disabled')                   or
                p:has-class('xforms-case-deselected')            or
                p:has-class('xforms-case-begin-end')             or
                p:has-class('xforms-hidden')                     or
                p:has-class('fr-dialog')                         or
                p:has-class('xforms-error-dialogs')              or
                p:has-class('xforms-help-panel')                 or
                p:has-class('xforms-template')]"
       />

    <!-- We could remove the nested `div` but it doesn't seem to make a difference for page breaks -->
    <!--<xsl:template match="*[p:has-class('xbl-fr-section')]/*:div[1][p:has-class('xforms-group')]">-->
        <!--<xsl:apply-templates select="node()"/>-->
    <!--</xsl:template>-->

    <!-- We could remove grouping switches but it doesn't seem to make a difference for page breaks -->
    <!--<xsl:template match="*[p:has-class('xforms-switch')]">-->
        <!--<xsl:apply-templates select="node()"/>-->
    <!--</xsl:template>-->

    <!-- Enabling this breaks the `page-break` CSS rules -->
    <!--<xsl:template match="*:span[p:has-class('xforms-case-selected') and empty(node())]"/>-->

    <!-- Remove all prefixes because Flying Saucer doesn't like them -->
    <xsl:template match="*">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@* | node()"/>
        </xsl:element>
    </xsl:template>

    <!-- Scope color mode CSS class -->
    <xsl:template match="*:div[@id = 'fr-view']">
        <xsl:copy>
            <xsl:attribute name="class" select="concat(@class, ' fr-pdf-color-mode-', $color-mode)"/>
            <xsl:apply-templates select="(@* except @class) | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Make a copy of useful information so it can be moved, via CSS, to headers and footers -->
    <!-- Since #5576, this is no longer the "correct" way of doing things -->
    <xsl:template match="*:body">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@*"/>
            <div class="fr-header-title xforms-hidden"><div><xsl:value-of select="$title"/></div></div>
            <div class="fr-footer-title xforms-hidden"><div><xsl:value-of select="$title"/></div></div>
            <xsl:apply-templates select="node()"/>
        </xsl:element>
    </xsl:template>

</xsl:transform>