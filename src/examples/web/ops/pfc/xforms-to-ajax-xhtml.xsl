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
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    exclude-result-prefixes="xforms xxforms xs saxon xhtml f">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:output name="xml" method="xml"/>

    <xsl:variable name="request" as="element()" 
        select="doc('input:request')/xxforms:event-request"/>
    <xsl:variable name="response" as="element()" 
        select="doc('input:response')/xxforms:event-response"/>

    <xsl:variable name="repeat-indexes" as="element()*"
        select="$response/xxforms:action/xxforms:repeats/xxforms:repeat-index" />

    <xsl:variable name="repeat-iterations" as="element()*"
        select="$response/xxforms:action/xxforms:repeats/xxforms:repeat-iteration" />

    <xsl:variable name="itemsets" as="element()*"
        select="$response/xxforms:action/xxforms:itemsets/xxforms:itemset"/>

    <!-- - - - - - - Form with hidden divs - - - - - - -->
    
    <xsl:template match="xhtml:body">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xhtml:form id="xforms-form" class="xforms-form" action="/xforms-server-submit" method="POST">
                <!-- Store private information used by the client-side JavaScript -->
                <xhtml:input type="hidden" name="$static-state" value="{$request/xxforms:static-state}"/>
                <xhtml:input type="hidden" name="$dynamic-state" value="{$response/xxforms:dynamic-state}"/>
                <xhtml:input type="hidden" name="$temp-dynamic-state" value=""/>
                <!-- Store information about nested repeats hierarchy -->
                <xsl:variable name="repeat-tree" as="xs:string*">
                    <xsl:for-each select="//xforms:repeat">
                        <xsl:value-of select="if (position() > 1) then ',' else ''"/>
                        <xsl:variable name="parent-repeat" as="element()?" select="ancestor::xforms:repeat[1]"/>
                        <xsl:value-of select="@id"/>
                        <xsl:value-of select="if (exists($parent-repeat)) then concat(' ', $parent-repeat/@id) else ''"/>
                    </xsl:for-each>
                </xsl:variable>
                <xhtml:span id="xforms-repeat-tree"><xsl:value-of select="string-join($repeat-tree, '')"/></xhtml:span>
                <!-- Store information about the initial index of each repeat -->
                <xsl:variable name="repeat-indexes-value" as="xs:string*">
                    <xsl:for-each select="$repeat-indexes">
                        <xsl:value-of select="if (position() > 1) then ',' else ''"/>
                        <xsl:value-of select="concat(@id, ' ', @index)"/>
                    </xsl:for-each>
                </xsl:variable>
                <xhtml:span id="xforms-repeat-indexes"><xsl:value-of select="string-join($repeat-indexes-value, '')"/></xhtml:span>
                <xhtml:span class="xforms-loading-loading"/>
                <xhtml:span class="xforms-loading-error"/>
                <xhtml:span class="xforms-loading-none"/>
                <xsl:apply-templates/>
            </xhtml:form>
        </xsl:copy>
    </xsl:template>
    
    <!-- - - - - - - XForms controls - - - - - - -->

    <!-- Add classes to control elements reflecting model item properties -->
    <xsl:template match="xforms:output | xforms:input
            | xforms:secret | xforms:textarea | xforms:select | xforms:select1
            | xforms:range | xforms:trigger | xforms:submit" priority="3">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xsl:choose>
            <xsl:when test="$generate-template">
                <xsl:next-match/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
                <xsl:variable name="control-rendered" as="element()+">
                    <xsl:next-match/>
                </xsl:variable>
                <xsl:for-each select="$control-rendered">
                    <xsl:copy>
                        <xsl:copy-of select="@* except @class"/>
                        <xsl:variable name="classes" as="xs:string*" select="(if (@class) then @class else (),
                            if (xxforms:control($id)/@relevant = 'false') then  'xforms-disabled' else ())"/>
                        <xsl:attribute name="class" select="string-join($classes , ' ')"/>
                        <xsl:copy-of select="node()"/>
                    </xsl:copy>
                </xsl:for-each>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="xforms:output | xforms:input
            | xforms:secret | xforms:textarea | xforms:select | xforms:select1 
            | xforms:range" priority="2">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xsl:apply-templates select="xforms:label"/>
        <xsl:next-match/>
        <xsl:apply-templates select="xforms:help"/>
        <xsl:if test="local-name() != 'output'">
            <xhtml:label for="{$id}">
                <xsl:copy-of select="xxforms:copy-attributes(xforms:alert,
                    if ($generate-template) then 'xforms-alert-inactive' else
                    concat('xforms-alert-', if (xxforms:control($id)/@valid = 'false') then 'active' else 'inactive'), ())"/>
                <xsl:if test="not($generate-template)">
                    <xsl:value-of select="xxforms:control($id)/@alert"/>
                </xsl:if>
            </xhtml:label>
        </xsl:if>
        <xsl:apply-templates select="xforms:hint"/>
    </xsl:template>
    
    <xsl:template match="xforms:output">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., ('xforms-control', 'xforms-output'), $id)"/>
            <xsl:choose>
                <xsl:when test="$generate-template">
                    <xsl:value-of select="'$xforms-output-value$'"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="xxforms:control($id)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xhtml:span>
    </xsl:template>

    <xsl:template match="xforms:trigger">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:button type="button" class="trigger">
            <xsl:copy-of select="xxforms:copy-attributes(., ('xforms-control', 'xforms-trigger'), $id)"/>
            <xsl:choose>
                <xsl:when test="$generate-template">
                    <xsl:value-of select="'$xforms-label-value$'"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="xxforms:control($id)/@label"/>
                </xsl:otherwise>
            </xsl:choose>
        </xhtml:button>
    </xsl:template>

    <xsl:template match="xforms:submit">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:button type="button" class="trigger">
            <xsl:copy-of select="xxforms:copy-attributes(., ('xforms-control', 'xforms-trigger'), $id)"/>

            <xsl:choose>
                <xsl:when test="$generate-template">
                    <xsl:value-of select="'$xforms-label-value$'"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="xxforms:control($id)/@label"/>
                </xsl:otherwise>
            </xsl:choose>
        </xhtml:button>
    </xsl:template>

    <xsl:template match="xforms:input">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xsl:choose>
            <xsl:when test="$generate-template">
                <xhtml:input type="text" name="{$id}">
                    <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
                </xhtml:input>
            </xsl:when>
            <xsl:otherwise>
                <xhtml:input type="text" name="{$id}" value="{xxforms:control($id)}">
                    <xsl:if test="xxforms:control($id)/@readonly = 'true'">
                        <xsl:attribute name="disabled">disabled</xsl:attribute>
                    </xsl:if>
                    <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
                </xhtml:input>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="xforms:secret">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:input type="password" name="{$id}" value="{xxforms:control($id)}">
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
        </xhtml:input>
    </xsl:template>

    <xsl:template match="xforms:textarea">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:textarea name="{$id}">
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
            <xsl:value-of select="xxforms:control($id)"/>
        </xhtml:textarea>
    </xsl:template>
    
    <!-- Display as list of checkboxes / radio buttons -->
    <xsl:template match="xforms:select[@appearance = 'full'] | xforms:select1[@appearance = 'full']">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xsl:variable name="many" as="xs:boolean" select="local-name() = 'select'"/>
        <xsl:variable name="type" as="xs:string"
            select="if ($many) then 'checkbox' else 'radio'"/>
        <xsl:variable name="class" as="xs:string"
            select="if ($many) then 'xforms-select-full' else 'xforms-select1-full'"/>

        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., ('xforms-control', $class), $id)"/>
            <xsl:choose>
                <xsl:when test="xforms:itemset">
                    <!-- There is an itemset -->
                    <xsl:variable name="itemset-element" select="xforms:itemset" as="element()"/>

                    <!-- Obtain dynamic item labels and values -->
                    <xsl:for-each select="$itemsets[@id = $id]/xxforms:item">
                        <xsl:call-template name="select-full-item">
                            <xsl:with-param name="type" select="$type"/>
                            <xsl:with-param name="id" select="$id"/>
                            <xsl:with-param name="attributes-element" select="$itemset-element"/>
                            <xsl:with-param name="label" select="@label"/>
                            <xsl:with-param name="value" select="@value"/>
                            <xsl:with-param name="generate-template" select="$generate-template"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Static list of items -->
                    <xsl:for-each select="xforms:item">
                        <xsl:call-template name="select-full-item">
                            <xsl:with-param name="type" select="$type"/>
                            <xsl:with-param name="id" select="$id"/>
                            <xsl:with-param name="attributes-element" select="."/>
                            <xsl:with-param name="label" select="xforms:label"/>
                            <xsl:with-param name="value" select="xforms:value"/>
                            <xsl:with-param name="generate-template" select="$generate-template"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:otherwise>
            </xsl:choose>

        </xhtml:span>
        <!-- Produce template -->
        <xsl:if test="xforms:itemset">
            <xhtml:span id="xforms-select-template-{$id}" class="xforms-select-template">
                <xsl:call-template name="select-full-item">
                    <xsl:with-param name="type" select="$type"/>
                    <xsl:with-param name="id" select="$id"/>
                    <xsl:with-param name="attributes-element" select="xforms:itemset"/>
                    <xsl:with-param name="label" select="'$xforms-template-label$'"/>
                    <xsl:with-param name="value" select="'$xforms-template-value$'"/>
                </xsl:call-template>
            </xhtml:span>
        </xsl:if>
    </xsl:template>
    
    <xsl:template name="select-full-item">
        <xsl:param name="type" as="xs:string"/>
        <xsl:param name="id" as="xs:string"/>
        <xsl:param name="attributes-element" as="element()"/>
        <xsl:param name="label" as="xs:string"/>
        <xsl:param name="value" as="xs:string"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xhtml:span>
            <xhtml:input type="{$type}" name="{$id}" value="{$value}">
                <xsl:copy-of select="xxforms:copy-attributes($attributes-element, (), ())"/>
                <xsl:if test="not($generate-template)">
                    <xsl:if test="$value = tokenize(xxforms:control($id), ' ')">
                        <xsl:attribute name="checked">checked</xsl:attribute>
                    </xsl:if>
                </xsl:if>
            </xhtml:input>
            <xsl:value-of select="$label"/>
        </xhtml:span>
    </xsl:template>

    <!-- Display as list / combobox -->
    <xsl:template match="xforms:select1[@appearance = ('minimal', 'compact')] 
            | xforms:select[@appearance = 'compact']">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xsl:variable name="many" as="xs:boolean" select="local-name() = 'select'"/>
        <xsl:variable name="class" as="xs:string"
            select="if (local-name() = 'select1' and @appearance = 'minimal') then 'xforms-select1-minimal' 
                else if (local-name() = 'select1' and @appearance = 'compact') then 'xforms-select1-compact'
                else if (local-name() = 'select' and @appearance = 'compact') then 'xforms-select-compact'
                else ()"/>
        
        <xhtml:select name="{$id}">
            <xsl:copy-of select="xxforms:copy-attributes(., ('xforms-control', $class), $id)"/>
            <xsl:if test="@appearance = 'compact'">
                <xsl:attribute name="multiple">multiple</xsl:attribute>
            </xsl:if>

            <xsl:choose>
                <xsl:when test="xforms:itemset">
                    <!-- There is an itemset -->
                    <xsl:variable name="itemset-element" select="xforms:itemset" as="element()"/>

                    <!-- Obtain dynamic item labels and values -->
                    <xsl:for-each select="$itemsets[@id = $id]/xxforms:item">
                        <xsl:call-template name="select-minimal-compact-item">
                            <xsl:with-param name="many" select="$many"/>
                            <xsl:with-param name="id" select="$id"/>
                            <xsl:with-param name="attributes-element" select="$itemset-element"/>
                            <xsl:with-param name="label" select="@label"/>
                            <xsl:with-param name="value" select="@value"/>
                            <xsl:with-param name="generate-template" select="$generate-template"/>
                        </xsl:call-template>
                    </xsl:for-each>

                </xsl:when>
                <xsl:otherwise>
                    <!-- Static list of items -->
                    <xsl:for-each select="xforms:item">
                        <xsl:call-template name="select-minimal-compact-item">
                            <xsl:with-param name="many" select="$many"/>
                            <xsl:with-param name="id" select="$id"/>
                            <xsl:with-param name="attributes-element" select="."/>
                            <xsl:with-param name="label" select="xforms:label"/>
                            <xsl:with-param name="value" select="xforms:value"/>
                            <xsl:with-param name="generate-template" select="$generate-template"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:otherwise>
            </xsl:choose>
        </xhtml:select>
    </xsl:template>

    <xsl:template name="select-minimal-compact-item">
        <xsl:param name="many" as="xs:boolean"/>
        <xsl:param name="id" as="xs:string"/>
        <xsl:param name="attributes-element" as="element()"/>
        <xsl:param name="label" as="xs:string"/>
        <xsl:param name="value" as="xs:string"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xhtml:option value="{$value}">
            <xsl:copy-of select="xxforms:copy-attributes($attributes-element, (), ())"/>
            <xsl:if test="$generate-template">
                <xsl:if test="$value = (if ($many) then tokenize(xxforms:control($id), ' ') else xxforms:control($id))">
                    <xsl:attribute name="selected">selected</xsl:attribute>
                </xsl:if>
            </xsl:if>
            <xsl:value-of select="$label"/>
        </xhtml:option>
    </xsl:template>

    <xsl:template match="xforms:label | xforms:hint | xforms:help">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="generate-template" select="false()" tunnel="yes"/>

        <xsl:variable name="parent-id" as="xs:string" select="concat(../@id, $id-postfix)"/>
        <xsl:variable name="current-id" as="xs:string" select="concat(@id, $id-postfix)"/>
        <xhtml:label for="{$parent-id}">
            <xsl:copy-of select="xxforms:copy-attributes(., concat('xforms-', local-name()), ())"/>
            <xsl:if test="not($generate-template)">
                <xsl:variable name="value-element" as="element()" select="xxforms:control($parent-id)"/>
                <xsl:value-of select="if (local-name() = 'label') then $value-element/@label
                    else if (local-name() = 'hint') then $value-element/@hint
                    else $value-element/@help"/>
            </xsl:if>
        </xhtml:label>
    </xsl:template>
    
    <xsl:template match="xforms:range">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:div>
            <xsl:copy-of select="xxforms:copy-attributes(., ('xforms-control', 'xforms-range-casing'), $id)"/>
            <xhtml:div class="xforms-range-track"/>
            <xhtml:div class="xforms-range-slider"/>
        </xhtml:div>
    </xsl:template>

    <!-- - - - - - - XForms repeat - - - - - - -->

    <xsl:template match="xforms:repeat">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:param name="top-level-repeat" select="true()" as="xs:boolean" tunnel="yes"/>
        <xsl:param name="generate-template" select="true()" as="xs:boolean" tunnel="yes"/>
        <xsl:param name="repeat-selected" select="true()" as="xs:boolean" tunnel="yes"/>

        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xsl:variable name="xforms-repeat" select="." as="element()"/>
        <xsl:variable name="current-repeat-index" select="$repeat-indexes[@id = $xforms-repeat/@id]" as="element()"/>
        <xsl:variable name="current-repeat-iteration" select="$repeat-iterations[@id = $id]" as="element()?"/>

        <xsl:variable name="delimiter-local-name" as="xs:string" select="local-name(*[1])"/>
        <xsl:variable name="delimiter-namespace-uri" as="xs:string" select="namespace-uri(*[1])"/>

        <!-- Delimiter: begin repeat -->
        <xsl:copy-of select="xxforms:repeat-delimiter($delimiter-namespace-uri,
            $delimiter-local-name, concat('repeat-begin-', $id))"/>
        <xsl:copy-of select="xxforms:repeat-delimiter($delimiter-namespace-uri, $delimiter-local-name, ())"/>

        <xsl:if test="$top-level-repeat or not($generate-template)">
            <!-- Repeat content for the number of occurrences the XForms Server gives us -->
            <xsl:for-each select="(1 to $current-repeat-iteration/@occurs)">
                <!-- Delimiter: between repeat entries -->
                <xsl:if test="current() > 1">
                    <xsl:copy-of select="xxforms:repeat-delimiter($delimiter-namespace-uri, $delimiter-local-name, ())"/>
                </xsl:if>
                <!-- Is the current iteration selected? -->
                <xsl:variable name="current-repeat-selected" as="xs:boolean" select="$repeat-selected and current() = $current-repeat-index/@index"/>
                <!-- Is the current iteration relevant? -->
                <xsl:variable name="action-repeat-iteration" as="element()"
                    select="$response/xxforms:action/xxforms:control-values/xxforms:repeat-iteration[@id = $id and @iteration = current()]"/>
                <xsl:variable name="current-repeat-relevant" as="xs:boolean" select="$action-repeat-iteration/@relevant = 'true'"/>
                <!-- Classes we add on the element in the current repeat iteration -->
                <xsl:variable name="number-parent-repeat" as="xs:integer" select="count(tokenize($id-postfix, '-'))"/>
                <xsl:variable name="added-classes" as="xs:string*"
                    select="(if ($current-repeat-selected) then
                        concat('xforms-repeat-selected-item-', if ($number-parent-repeat mod 2 = 1) then '1' else '2') else (),
                        if ($current-repeat-relevant) then () else 'xforms-disabled')"/>
                <!-- Get children of current repeat iteration adding a span element around text nodes -->
                <xsl:variable name="current-repeat-children-nodes" as="node()*">
                    <xsl:apply-templates select="$xforms-repeat/node()">
                        <xsl:with-param name="id-postfix" select="concat($id-postfix, '-', current())" tunnel="yes"/>
                        <xsl:with-param name="top-level-repeat" select="false()" tunnel="yes"/>
                        <xsl:with-param name="generate-template" select="false()" tunnel="yes"/>
                        <xsl:with-param name="repeat-selected" select="$current-repeat-selected" tunnel="yes"/>
                    </xsl:apply-templates>
                </xsl:variable>
                <xsl:variable name="current-repeat-children-with-span" as="node()*">
                    <xsl:for-each select="$current-repeat-children-nodes">
                        <xsl:choose>
                            <xsl:when test=". instance of element()">
                                <xsl:copy-of select="."/>
                            </xsl:when>
                            <xsl:when test="normalize-space() != ''">
                                <xhtml:span>
                                    <xsl:value-of select="."/>
                                </xhtml:span>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="."/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                </xsl:variable>
                <!-- Copy elements in current repeat iteration adding class if necessary -->
                <xsl:for-each select="$current-repeat-children-with-span">
                    <xsl:choose>
                        <xsl:when test=". instance of element()">
                            <xsl:copy>
                                <xsl:variable name="classes-on-element" as="xs:string*" select="(@class, $added-classes)"/>
                                <xsl:if test="count($classes-on-element) > 0">
                                    <xsl:attribute name="class" select="string-join($classes-on-element, ' ')"/>
                                </xsl:if>
                                <xsl:copy-of select="@* except @class"/>
                                <xsl:copy-of select="node()"/>
                            </xsl:copy>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="."/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:if>

        <xsl:if test="$generate-template">
            <!-- Produce templates -->
            <xsl:if test="$current-repeat-iteration/@occurs > 0">
                <xsl:copy-of select="xxforms:repeat-delimiter($delimiter-namespace-uri, $delimiter-local-name, ())"/>
            </xsl:if>
            <xsl:for-each select="$xforms-repeat/node()">
                <xsl:choose>
                    <xsl:when test=". instance of element()">
                        <xsl:copy>
                            <xsl:copy-of select="@* except (@xhtml:class, @class)"/>
                            <xsl:choose>
                                <xsl:when test="$top-level-repeat">
                                    <xsl:attribute name="class" select="string-join
                                        ((@xhtml:class, @class, 'xforms-repeat-template'), ' ')"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:copy-of select="@class"/>
                                </xsl:otherwise>
                            </xsl:choose>
                            <xsl:apply-templates select="node()">
                                <xsl:with-param name="id-postfix" select="$id-postfix" tunnel="yes"/>
                                <xsl:with-param name="top-level-repeat" select="false()" tunnel="yes"/>
                                <xsl:with-param name="generate-template" select="true()" tunnel="yes"/>
                                <xsl:with-param name="selected" select="false()" tunnel="yes"/>
                            </xsl:apply-templates>
                        </xsl:copy>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:copy-of select="."/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:for-each>
        </xsl:if>

        <!-- Delimiter: end repeat -->
        <xsl:copy-of select="xxforms:repeat-delimiter($delimiter-namespace-uri,
            $delimiter-local-name, concat('repeat-end-', $id))"/>

    </xsl:template>

    <xsl:function name="xxforms:repeat-delimiter">
        <xsl:param name="delimiter-namespace-uri" as="xs:string"/>
        <xsl:param name="delimiter-local-name" as="xs:string"/>
        <xsl:param name="id" as="xs:string?"/>
        <xsl:element name="{$delimiter-local-name}" namespace="{$delimiter-namespace-uri}">
            <xsl:choose>
                <xsl:when test="$id">
                    <xsl:attribute name="id" select="$id"/>
                    <xsl:attribute name="class">xforms-repeat-begin-end</xsl:attribute>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:attribute name="class">xforms-repeat-delimiter</xsl:attribute>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:function>

    <!-- - - - - - - XForms containers - - - - - - -->

    <xsl:template match="xforms:group|xforms:switch">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., concat('xforms-', local-name()), $id)"/>
            <xsl:apply-templates/>
        </xhtml:span>
    </xsl:template>
    
    <xsl:template match="xforms:case">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-case', $id)"/>
            <xsl:variable name="div-information" as="element()"
                select="$response/xxforms:action/xxforms:divs/xxforms:div[@id = $id]"/>
            <xsl:attribute name="style" select="concat('display: ', 
                if ($div-information/@visibility = 'visible') then 'block' else 'none')"/>
            <xsl:apply-templates/>
        </xhtml:span>
    </xsl:template>
    
    <!-- - - - - - - Utility templates and functions - - - - - - -->

    <xsl:template match="xforms:*"/>
    
    <xsl:function name="xxforms:copy-attributes">
        <xsl:param name="element" as="element()?"/>
        <xsl:param name="classes" as="xs:string*"/>
        <xsl:param name="id" as="xs:string?"/>
        <!-- Handle id attribute -->
        <xsl:if test="$id">
            <xsl:attribute name="id" select="$id"/>
        </xsl:if>
        <!-- Copy attributes with no namespaces -->
        <xsl:copy-of select="$element/@accesskey | $element/@tabindex | $element/@style"/>
        <!-- Convert navindex to tabindex -->
        <xsl:if test="$element/@navindex">
            <xsl:attribute name="tabindex" select="$element/@navindex"/>
        </xsl:if>
        <!-- Copy class attribute, both in xhtml namespace and no namespace -->
        <xsl:variable name="class" as="xs:string" select="string-join
            (($element/@xhtml:class, $element/@class, $classes, 
            if ($element/@incremental = 'true') then 'xforms-incremental' else ()), ' ')"/>
        <xsl:if test="string-length($class) > 0">
            <xsl:attribute name="class" select="$class"/>
        </xsl:if>
        <!-- Copy attributes in the xhtml namespace to no namespace -->
        <xsl:for-each select="$element/@xhtml:* except $element/@xhtml:class">
            <xsl:attribute name="{local-name()}" select="."/>
        </xsl:for-each>
    </xsl:function>
    
    <xsl:function name="xxforms:control" as="element()">
        <xsl:param name="id" as="xs:string"/>
        <xsl:variable name="control"
            select="$response/xxforms:action/xxforms:control-values/xxforms:control[@id = $id]"/>
        <xsl:if test="not($control)">
            <xsl:message terminate="yes">Can't find control with id = '<xsl:value-of select="$id"/>'</xsl:message>
        </xsl:if>
        <xsl:sequence select="$control"/>
    </xsl:function>
    
</xsl:stylesheet>
