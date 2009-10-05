<?xml version="1.0" encoding="UTF-8"?>
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
<xsl:transform xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:exf="http://www.exforms.org/exf/1-0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

    <xsl:variable name="parameters">
        <!-- These optional attributes are used as parameters -->
        <parameter>appearance</parameter>
        <parameter>scrollable</parameter>
        <parameter>width</parameter>
        <parameter>height</parameter>
        <parameter>paginated</parameter>
        <parameter>rowsPerPage</parameter>
        <parameter>sortAndPaginationMode</parameter>
        <parameter>nbPages</parameter>
        <parameter>maxNbPagesToDisplay</parameter>
        <parameter>page</parameter>
        <parameter>innerTableWidth</parameter>
        <parameter>loading</parameter>
        <parameter>dynamic</parameter>
        <parameter>debug</parameter>
    </xsl:variable>


    <xsl:variable name="numberTypes">
        <type>xs:decimal</type>
        <type>xs:integer</type>
        <type>xs:nonPositiveInteger</type>
        <type>xs:negativeInteger</type>
        <type>xs:long</type>
        <type>xs:int</type>
        <type>xs:short</type>
        <type>xs:byte</type>
        <type>xs:nonNegativeInteger</type>
        <type>xs:unsignedLong</type>
        <type>xs:unsignedInt</type>
        <type>xs:unsignedShort</type>
        <type>xs:unsignedByte</type>
        <type>xs:positiveInteger</type>
    </xsl:variable>
    <xsl:variable name="numberTypesEnumeration">
        <xsl:for-each select="$numberTypes/*">
            <xsl:if test="position() >1">,</xsl:if>
            <xsl:text>resolve-QName('</xsl:text>
            <xsl:value-of select="."/>
            <xsl:text>',..)</xsl:text>
        </xsl:for-each>
    </xsl:variable>

    <!-- Perform pass 1 to 4 to support simplified syntaxes -->
    <xsl:variable name="pass1">
        <xsl:apply-templates select="/" mode="pass1"/>
    </xsl:variable>

    <xsl:variable name="pass2">
        <xsl:apply-templates select="$pass1" mode="pass2"/>
    </xsl:variable>

    <xsl:variable name="pass3">
        <xsl:apply-templates select="$pass2" mode="pass3"/>
    </xsl:variable>

    <xsl:variable name="pass4">
        <xsl:apply-templates select="$pass3" mode="pass4"/>
    </xsl:variable>

    <!-- Set some variables that will dictate the geometry of the widget -->
    <xsl:variable name="scrollH"
        select="$pass4/fr:datatable/@scrollable = ('horizontal', 'both') and $pass4/fr:datatable/@width"/>
    <xsl:variable name="scrollV"
        select="$pass4/fr:datatable/@scrollable = ('vertical', 'both') and $pass4/fr:datatable/@height"/>
    <xsl:variable name="height"
        select="if ($scrollV) then concat('height: ', $pass4/fr:datatable/@height, ';') else ''"/>
    <xsl:variable name="width"
        select="if ($pass4/fr:datatable/@width) then concat('width: ', $pass4/fr:datatable/@width, ';') else ''"/>
    <xsl:variable name="id"
        select="if ($pass4/fr:datatable/@id) then $pass4/fr:datatable/@id else generate-id($pass4/fr:datatable)"/>
    <xsl:variable name="paginated" select="$pass4/fr:datatable/@paginated = 'true'"/>
    <xsl:variable name="rowsPerPage"
        select="if ($pass4/fr:datatable/@rowsPerPage castable as xs:integer) then $pass4/fr:datatable/@rowsPerPage cast as xs:integer else 10"/>
    <xsl:variable name="maxNbPagesToDisplay"
        select="if ($pass4/fr:datatable/@maxNbPagesToDisplay castable as xs:integer) then $pass4/fr:datatable/@maxNbPagesToDisplay cast as xs:integer else -1"/>
    <xsl:variable name="sortAndPaginationMode" select="$pass4/fr:datatable/@sortAndPaginationMode"/>
    <xsl:variable name="innerTableWidth"
        select="if ($pass4/fr:datatable/@innerTableWidth) then concat(&quot;'&quot;, $pass4/fr:datatable/@innerTableWidth, &quot;'&quot;) else 'null'"/>
    <xsl:variable name="hasLoadingFeature" select="count($pass4/fr:datatable/@loading) = 1"/>
    <xsl:variable name="dynamic"
        select="$pass4/fr:datatable/@dynamic = 'true' or $pass4/fr:datatable/xhtml:thead/xhtml:tr/xforms:repeat"/>
    <xsl:variable name="debug" select="$pass4/fr:datatable/@debug = 'true'"/>

    <!--
        Pagination...
        <div id="yui-dt0-paginator1" class="yui-dt-paginator yui-pg-container" style="">
        <span id="yui-pg0-1-first-span" class="yui-pg-first"><< first</span>
        <span id="yui-pg0-1-prev-span" class="yui-pg-previous">< prev</span>
            <span id="yui-pg0-1-pages" class="yui-pg-pages">
                <span class="yui-pg-current-page yui-pg-page">1</span>
                <a class="yui-pg-page" page="2" href="#">2</a>
                <a class="yui-pg-page" page="3" href="#">3</a>
                <a class="yui-pg-page" page="4" href="#">4</a>
                <a class="yui-pg-page" page="5" href="#">5</a>
                <a class="yui-pg-page" page="6" href="#">6</a>
                <a class="yui-pg-page" page="7" href="#">7</a>
                <a class="yui-pg-page" page="8" href="#">8</a>
                <a class="yui-pg-page" page="9" href="#">9</a>
            </span>
            <a id="yui-pg0-1-next-link" class="yui-pg-next" href="#">next ></a>
            <a id="yui-pg0-1-last-link" class="yui-pg-last" href="#">last >></a>
    </div>-->


    <xsl:template match="@*|node()" mode="#all">
        <!-- Default template == identity -->
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/">
        <xsl:choose>
            <xsl:when test="$dynamic">
                <xsl:apply-templates select="$pass4/fr:datatable" mode="dynamic"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates select="$pass4/fr:datatable"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="fr:datatable">
        <!-- Matches the bound element -->


        <xhtml:div id="{$id}-container">
            <xsl:copy-of select="namespace::*"/>



            <xsl:variable name="pass5">

                <!--
                This pass generates the XHTML structure .
                and uses the default mode.

                -->

                <xhtml:table id="{$id}-table"
                    class="datatable datatable-{$id} yui-dt-table {@class} {if ($scrollV) then 'fr-scrollV' else ''}  {if ($scrollH) then 'fr-scrollH' else ''} "
                    style="{$height} {$width}">
                    <!-- Copy attributes that are not parameters! -->
                    <xsl:apply-templates select="@*[not(name() = ($parameters/*, 'id', 'class' ))]"/>
                    <xsl:if test="not(xhtml:colgroup)">
                        <!-- If there is no colgroup element, add one -->
                        <xhtml:colgroup>
                            <xsl:for-each
                                select="((xhtml:tbody|self::*)/(xforms:repeat|self::*)/xhtml:tr)[1]/xhtml:td">
                                <xhtml:col/>
                            </xsl:for-each>
                        </xhtml:colgroup>
                    </xsl:if>
                    <xsl:if test="not(xhtml:thead)">
                        <!-- If there is no thead element, add one -->
                        <xhtml:thead>
                            <xhtml:tr>
                                <xsl:for-each
                                    select="((xhtml:tbody|self::*)/(xforms:repeat|self::*)/xhtml:tr)[1]/xhtml:td">
                                    <xhtml:th>
                                        <xsl:copy-of select="@fr:*"/>
                                        <xsl:value-of select="xforms:*/xforms:label"/>
                                    </xhtml:th>
                                </xsl:for-each>
                            </xhtml:tr>
                        </xhtml:thead>
                    </xsl:if>
                    <xsl:choose>
                        <xsl:when test="xhtml:tbody">
                            <xsl:apply-templates select="node()"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <!-- If there is no tbody, add one -->
                            <xsl:apply-templates select="xhtml:thead"/>
                            <xhtml:tbody>
                                <xsl:apply-templates select="*[not(self::xhtml:thead)]"/>
                            </xhtml:tbody>
                        </xsl:otherwise>
                    </xsl:choose>
                </xhtml:table>
            </xsl:variable>

            <!--

            Now it's time to assemble all that stuff

            -->

            <xforms:model id="datatable">
                <xsl:variable name="sort-instance">
                    <xforms:instance id="sort">
                        <xsl:variable name="sorted"
                            select="$pass5/xhtml:table/xhtml:thead/xhtml:tr/xhtml:th[@fr:sorted = ('descending', 'ascending')][1]/@fr:sorted"/>
                        <sort
                            currentId="{if ($sorted) then count($sorted/../preceding-sibling::xhtml:th) + 1 else -1}"
                            currentOrder="{$sorted}" xmlns="">
                            <xsl:for-each select="$pass5/xhtml:table/xhtml:thead/xhtml:tr/xhtml:th">
                                <key
                                    type="{if (@fr:sortType) then if (@fr:sortType = 'number') then 'number' else 'text' else ''}">
                                    <xsl:variable name="position"
                                        select="count(preceding-sibling::xhtml:th) + 1"/>
                                    <xsl:if test="@fr:sortable='true'">
                                        <xsl:variable name="sortKeys">
                                            <xsl:apply-templates
                                                select="$pass5/xhtml:table/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td[position() = $position]"
                                                mode="sortKey"/>
                                        </xsl:variable>
                                        <xsl:choose>
                                            <xsl:when test="count($sortKeys/*) > 1">
                                                <xsl:text>concat(</xsl:text>
                                                <xsl:value-of select="string-join($sortKeys/*, ',')"/>
                                                <xsl:text>)</xsl:text>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:value-of select="$sortKeys/*"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:if>
                                </key>
                            </xsl:for-each>
                        </sort>
                    </xforms:instance>
                </xsl:variable>
                <xsl:copy-of select="$sort-instance"/>
                <xsl:variable name="repeat-nodeset" select="$pass5//xforms:repeat[1]/@nodeset"/>
                <xxforms:variable name="nodeset"
                    select="xxforms:component-context()/{$pass5//xforms:repeat/@nodeset}"/>
                <xsl:for-each
                    select="$pass5/xhtml:table/xhtml:thead/xhtml:tr/xhtml:th[@fr:sortable='true' and not(@fr:sortType)]">
                    <xsl:variable name="position" select="count(preceding-sibling::xhtml:th) + 1"/>
                    <xxforms:variable name="node{$position}"
                        select="$nodeset[1]/{$sort-instance/xforms:instance/sort/key[position() = $position]}"/>
                    <!--Note: the following expression filters out values (for which . instance of node() is false) since xxforms:type doesn't work for them -->
                    <xxforms:variable name="isNode{$position}"
                        select="$node{$position} instance of node()"/>
                    <xxforms:variable name="type{$position}"
                        select="if ($isNode{$position}) then xxforms:type($node{$position}) else ()"/>

                    <xforms:bind nodeset="key[{$position}]/@type"
                        calculate="if (($isNode{$position} and $type{$position} = ({$numberTypesEnumeration})) or $node{$position} instance of xs:decimal) then 'number' else 'text'"
                    />
                </xsl:for-each>
                <xsl:if test="$paginated">
                    <xforms:instance id="page">
                        <page xmlns="">1</page>
                    </xforms:instance>
                </xsl:if>
                <xforms:bind nodeset="@currentId" type="xs:integer"/>

            </xforms:model>

            <xsl:choose>
                <xsl:when test="$paginated and not($sortAndPaginationMode='external')">
                    <xxforms:variable name="page" model="datatable" select="instance('page')"/>
                    <xxforms:variable name="nbRows"
                        select="count({$pass5//xforms:repeat[1]/@nodeset})"/>
                    <xxforms:variable name="nbPages"
                        select="ceiling($nbRows div {$rowsPerPage}) cast as xs:integer"/>
                </xsl:when>

                <xsl:when test="$paginated and $sortAndPaginationMode='external'">
                    <xxforms:variable name="page" xbl:attr="select=page"/>
                    <xxforms:variable name="nbPages" xbl:attr="select=nbPages"/>
                </xsl:when>

            </xsl:choose>


            <xsl:choose>
                <xsl:when test="$paginated and $maxNbPagesToDisplay &lt; 0">
                    <xxforms:variable name="pages"
                        select="for $p in 1 to $nbPages cast as xs:integer return xxforms:element('page', $p)"
                    />
                </xsl:when>
                <xsl:when test="$paginated">
                    <xxforms:variable name="maxNbPagesToDisplay"
                        select="{$maxNbPagesToDisplay} cast as xs:integer"/>
                    <xxforms:variable name="radix"
                        select="floor(($maxNbPagesToDisplay - 2) div 2) cast as xs:integer"/>
                    <xxforms:variable name="minPage"
                        select="
                        (if ($page > $radix)
                            then if ($nbPages >= $page + $radix)
                                then ($page - $radix)
                                else max((1, $nbPages - $maxNbPagesToDisplay + 1))
                            else 1) cast as xs:integer"/>
                    <xxforms:variable name="pages"
                        select="for $p in 1 to $nbPages cast as xs:integer return xxforms:element('page', $p)"/>
                    <!--<xxforms:variable name="pages"
                        select="for $p in $minPage to min(($nbPages, $minPage + $maxNbPagesToDisplay - 1)) cast as xs:integer return xxforms:element('page', $p)"
                    />-->
                </xsl:when>
            </xsl:choose>

            <xsl:variable name="pagination">
                <xsl:if test="$paginated">
                    <xhtml:div class="yui-dt-paginator yui-pg-container" style="">

                        <xforms:group ref=".[$page = 1]">
                            <xhtml:span class="yui-pg-first">&lt;&lt; first</xhtml:span>
                        </xforms:group>
                        <xforms:group ref=".[$page != 1]">
                            <xforms:trigger class="yui-pg-first" appearance="minimal">
                                <xforms:label>&lt;&lt; first </xforms:label>
                                <xsl:call-template name="fr-goto-page">
                                    <xsl:with-param name="fr-new-page">1</xsl:with-param>
                                </xsl:call-template>
                            </xforms:trigger>
                        </xforms:group>

                        <xforms:group ref=".[$page = 1]">
                            <xhtml:span class="yui-pg-previous">&lt; prev</xhtml:span>
                        </xforms:group>
                        <xforms:group ref=".[$page != 1]">
                            <xforms:trigger class="yui-pg-previous" appearance="minimal">
                                <xforms:label>&lt; prev</xforms:label>
                                <xsl:call-template name="fr-goto-page">
                                    <xsl:with-param name="fr-new-page">$page - 1</xsl:with-param>
                                </xsl:call-template>
                            </xforms:trigger>
                        </xforms:group>

                        <xhtml:span class="yui-pg-pages">
                            <xforms:repeat nodeset="$pages">
                                <xsl:choose>
                                    <xsl:when test="$maxNbPagesToDisplay &lt; 0">
                                        <xxforms:variable name="display">page</xxforms:variable>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xxforms:variable name="display"
                                            select="
                                                    if ($page &lt; $maxNbPagesToDisplay -2)
                                                    then if (. &lt;= $maxNbPagesToDisplay - 2 or . = $nbPages)
                                                        then 'page'
                                                        else if (. = $nbPages - 1)
                                                            then 'ellipses'
                                                            else 'none'
                                                    else if ($page > $nbPages - $maxNbPagesToDisplay + 3)
                                                        then if (. >= $nbPages - $maxNbPagesToDisplay + 3 or . = 1)
                                                            then 'page'
                                                            else if (. = 2)
                                                                then 'ellipses'
                                                                else 'none'
                                                        else if (. = 1 or . = $nbPages or (. > $page - $radix and . &lt; $page + $radix))
                                                            then 'page'
                                                            else if (. = 2 or . = $nbPages -1)
                                                                then 'ellipses'
                                                                else 'none'
                                                 "
                                        />
                                    </xsl:otherwise>
                                </xsl:choose>
                                <xforms:group ref=".[. = $page and $display = 'page']">
                                    <xforms:output class="yui-pg-page" value="$page">
                                        <xforms:hint>Current page (edit to move to another
                                            page)</xforms:hint>
                                    </xforms:output>
                                </xforms:group>
                                <xforms:group ref=".[. != $page and $display = 'page']">
                                    <xxforms:variable name="targetPage" select="."/>
                                    <xforms:trigger class="yui-pg-page" appearance="minimal">
                                        <xforms:label>
                                            <xforms:output value="."/>
                                        </xforms:label>
                                        <xsl:call-template name="fr-goto-page">
                                            <xsl:with-param name="fr-new-page"
                                                >$targetPage</xsl:with-param>
                                        </xsl:call-template>
                                    </xforms:trigger>
                                </xforms:group>
                                <xforms:group ref=".[ $display = 'ellipses']">
                                    <xhtml:span class="yui-pg-page">...</xhtml:span>
                                </xforms:group>
                            </xforms:repeat>
                        </xhtml:span>

                        <xforms:group ref=".[$page = $nbPages or $nbPages = 0]">
                            <xhtml:span class="yui-pg-next">next ></xhtml:span>
                        </xforms:group>
                        <xforms:group ref=".[$page != $nbPages and $nbPages != 0]">
                            <xforms:trigger class="yui-pg-next" appearance="minimal">
                                <xforms:label>next ></xforms:label>
                                <xsl:call-template name="fr-goto-page">
                                    <xsl:with-param name="fr-new-page">$page + 1</xsl:with-param>
                                </xsl:call-template>
                            </xforms:trigger>
                        </xforms:group>

                        <xforms:group ref=".[$page = $nbPages or $nbPages = 0]">
                            <xhtml:span class="yui-pg-last">last >></xhtml:span>
                        </xforms:group>
                        <xforms:group ref=".[$page != $nbPages and $nbPages != 0]">
                            <xforms:trigger class="yui-pg-last" appearance="minimal">
                                <xforms:label>last >></xforms:label>
                                <xsl:call-template name="fr-goto-page">
                                    <xsl:with-param name="fr-new-page">$nbPages</xsl:with-param>
                                </xsl:call-template>
                            </xforms:trigger>
                        </xforms:group>
                    </xhtml:div>

                </xsl:if>
            </xsl:variable>

            <xsl:copy-of select="$pagination"/>


            <xsl:if test="$hasLoadingFeature">
                <xxforms:variable name="loading" xbl:attr="select=loading"/>
            </xsl:if>

            <xforms:group id="fr-datatable-group">
                <xsl:attribute name="ref">
                    <xsl:text>xxforms:component-context()</xsl:text>
                    <xsl:if test="$hasLoadingFeature">[not($loading = true())]</xsl:if>
                </xsl:attribute>

                <xforms:action ev:event="xforms-enabled" ev:target="fr-datatable-group">
                    <xxforms:script> YAHOO.log("Enabling datatable id <xsl:value-of select="$id"
                        />","info"); ORBEON.widgets.datatable.init(this, <xsl:value-of
                            select="$innerTableWidth"/>); </xxforms:script>
                </xforms:action>

                <xsl:apply-templates select="$pass5" mode="YUI"/>

            </xforms:group>

            <xsl:if test="$hasLoadingFeature">
                <xforms:group ref="xxforms:component-context()[$loading = true()]">
                    <xhtml:span class="yui-dt yui-dt-scrollable" style="display: table; ">
                        <xhtml:span class="yui-dt-hd"
                            style="border: 1px solid rgb(127, 127, 127); display: table-cell;">
                            <xhtml:table class="datatable  yui-dt-table" style="{$height} {$width}">
                                <xhtml:thead>
                                    <xhtml:tr class="yui-dt-first yui-dt-last">
                                        <xsl:apply-templates
                                            select="$pass5/xhtml:table/xhtml:thead/xhtml:tr/xhtml:th"
                                            mode="YUI"/>
                                    </xhtml:tr>
                                </xhtml:thead>
                                <xhtml:tbody>
                                    <xhtml:tr>
                                        <xhtml:td
                                            colspan="{count($pass5/xhtml:table/xhtml:thead/xhtml:tr/xhtml:th)}"
                                            class="fr-datatable-is-loading"/>
                                    </xhtml:tr>
                                </xhtml:tbody>
                            </xhtml:table>
                        </xhtml:span>
                    </xhtml:span>
                </xforms:group>
            </xsl:if>


            <xsl:copy-of select="$pagination"/>



        </xhtml:div>

        <!-- End of template on the bound element -->
    </xsl:template>

    <xsl:template name="fr-goto-page">
        <xsl:param name="fr-new-page"/>
        <xsl:choose>
            <xsl:when test="$sortAndPaginationMode='external'">
                <xforms:dispatch ev:event="DOMActivate" name="fr-goto-page" target="fr.datatable">
                    <xxforms:context name="fr-new-page" select="{$fr-new-page}"/>
                </xforms:dispatch>
            </xsl:when>
            <xsl:otherwise>
                <xforms:setvalue ev:event="DOMActivate" model="datatable" ref="instance('page')"
                    value="{$fr-new-page}"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>


    <!--

        YUI mode (YUI decorations)

    -->

    <xsl:template match="xhtml:thead/xhtml:tr" mode="YUI">
        <xhtml:tr class="yui-dt-first yui-dt-last {@class}" id="{$id}-thead-tr">
            <xsl:apply-templates select="@*[not(name() = ('class', 'id') )]|node()" mode="YUI"/>
        </xhtml:tr>
    </xsl:template>

    <xsl:template match="xhtml:thead" mode="YUI">
        <xhtml:thead id="{$id}-thead">
            <xsl:apply-templates select="@*[not(name() = ('id') )]|node()" mode="YUI"/>
        </xhtml:thead>
    </xsl:template>

    <xsl:template name="yui-dt-liner">
        <xsl:param name="position"/>
        <xhtml:div class="yui-dt-liner datatable-cell-content">
            <xhtml:span class="yui-dt-label">
                <xsl:choose>
                    <xsl:when test="@fr:sortable = 'true' and $sortAndPaginationMode='external'">
                        <xforms:trigger appearance="minimal">
                            <xforms:label>
                                <xsl:apply-templates select="node()" mode="YUI"/>
                            </xforms:label>
                            <xforms:hint>
                                <xforms:output value="{@fr:sortMessage}"/>
                            </xforms:hint>
                            <xforms:dispatch ev:event="DOMActivate" name="fr-update-sort"
                                target="fr.datatable">
                                <xxforms:context name="fr-column"
                                    select="{count(preceding-sibling::xhtml:th) + 1}"/>
                            </xforms:dispatch>
                        </xforms:trigger>
                    </xsl:when>
                    <xsl:when
                        test="@fr:sortable = 'true' and not($sortAndPaginationMode='external')">
                        <xforms:group model="datatable" instance="sort">
                            <xforms:group ref=".[$nextSortOrder = 'ascending']">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xsl:apply-templates select="node()" mode="YUI"/>
                                    </xforms:label>
                                    <xforms:hint>Click to sort <xforms:output value="$nextSortOrder"
                                        /></xforms:hint>
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:setvalue ref="@currentOrder" value="$nextSortOrder"/>
                                        <xforms:setvalue ref="@currentId">
                                            <xsl:value-of select="$position"/>
                                        </xforms:setvalue>
                                        <xforms:setvalue ref="instance('page')" value="1"/>
                                    </xforms:action>
                                </xforms:trigger>
                            </xforms:group>
                            <xforms:group ref=".[$nextSortOrder != 'ascending']">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xsl:apply-templates select="node()" mode="YUI"/>
                                    </xforms:label>
                                    <xforms:hint>Click to sort <xforms:output value="$nextSortOrder"
                                        /></xforms:hint>
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:setvalue ref="@currentOrder" value="$nextSortOrder"/>
                                        <xforms:setvalue ref="@currentId">
                                            <xsl:value-of select="$position"/>
                                        </xforms:setvalue>
                                        <xforms:setvalue ref="instance('page')" value="1"/>
                                    </xforms:action>
                                </xforms:trigger>
                            </xforms:group>
                        </xforms:group>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="node()" mode="YUI"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xhtml:span>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="xhtml:thead/xhtml:tr/xhtml:th" mode="YUI">
        <xsl:variable name="position" select="count(preceding-sibling::xhtml:th) + 1"/>
        <xxforms:variable name="nextSortOrder" model="datatable"
            select=" if (@currentId = {$position} and @currentOrder = 'ascending') then 'descending' else 'ascending' "/>
        <xxforms:variable name="currentId" model="datatable" select="@currentId"/>
        <xxforms:variable name="currentOrder" model="datatable" select="@currentOrder"/>
        <xhtml:th
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''}
            {if (@fr:resizeable = 'true') then 'yui-dt-resizeable' else ''}
            {{if ({$position} = $currentId)
            then  if($currentOrder = 'descending') then 'yui-dt-desc' else 'yui-dt-asc'
            else ''}}
            {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="YUI"/>
            <!-- <xsl:choose>
                <xsl:when test="@fr:resizeable = 'true'">
                    <xhtml:div class="yui-dt-resizerliner">
                        <xsl:call-template name="yui-dt-liner">
                            <xsl:with-param name="position" select="$position"/>table
                        </xsl:call-template>
                        <xhtml:div id="{generate-id()}" class="yui-dt-resizer" style=" left: auto; right: 0pt; top: auto; bottom: 0pt; height: 100%;"
                        />
                    </xhtml:div>
                </xsl:when>
                <xsl:otherwise>-->
            <xsl:call-template name="yui-dt-liner">
                <xsl:with-param name="position" select="$position"/>
            </xsl:call-template>
            <!--               </xsl:otherwise>
     </xsl:choose>-->
        </xhtml:th>
    </xsl:template>

    <xsl:template match="xhtml:tbody" mode="YUI">
        <xhtml:tbody class="yui-dt-data {@class}" id="{$id}-tbody">
            <xsl:apply-templates select="@*[not(name() = ('class', 'id'))]|node()" mode="YUI"/>
        </xhtml:tbody>
    </xsl:template>

    <xsl:template match="xforms:repeat" mode="YUI">
        <xxforms:variable name="sort" model="datatable" select="."/>
        <xxforms:variable name="key" model="datatable" select="key[position() = $sort/@currentId]"/>
        <xxforms:variable name="nodeset">
            <xsl:attribute name="select">
                <xsl:choose>
                    <xsl:when test="$sortAndPaginationMode='external'">
                        <xsl:value-of select="@nodeset"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:if test="$paginated">(</xsl:if>
                        <xsl:text>if ($sort/@currentId = -1 or $sort/@currentOrder = '') then </xsl:text>
                        <xsl:value-of select="@nodeset"/>
                        <xsl:text> else exf:sort(</xsl:text>
                        <xsl:value-of select="@nodeset"/>
                        <xsl:text>, $key , $key/@type, $sort/@currentOrder)</xsl:text>
                        <xsl:if test="$paginated">)[position() >= ($page - 1) * <xsl:value-of
                                select="$rowsPerPage"/> + 1 and position() &lt;= $page *
                                <xsl:value-of select="$rowsPerPage"/>]</xsl:if>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
        </xxforms:variable>
        <xxforms:script ev:event="xxforms-nodeset-changed" ev:target="fr-datatable-repeat">
            ORBEON.widgets.datatable.update(this); </xxforms:script>
        <xforms:repeat id="fr-datatable-repeat" nodeset="$nodeset">
            <xsl:apply-templates select="@*[not(name()=('nodeset', 'id'))]|node()" mode="YUI"/>
        </xforms:repeat>
        <xforms:group ref=".[not($nodeset)]">
            <xhtml:tr>
                <xhtml:td>&#xa0;</xhtml:td>
            </xhtml:tr>
        </xforms:group>
    </xsl:template>

    <xsl:template match="xhtml:tr" mode="YUI">
        <xhtml:tr
            class="
            {{if (position() = 1) then 'yui-dt-first' else '' }}
            {{if (position() = last()) then 'yui-dt-last' else '' }}
            {{if (position() mod 2 = 0) then 'yui-dt-odd' else 'yui-dt-even' }}
            {{if (xxforms:index() = position()) then 'yui-dt-selected' else ''}}
            {@class}"
            style="height: auto;">
            <xsl:apply-templates select="@*[name() != 'class']|node()" mode="YUI"/>
        </xhtml:tr>
    </xsl:template>

    <xsl:template match="xforms:repeat/xhtml:tr/xhtml:td" mode="YUI">
        <xsl:variable name="position" select="count(preceding-sibling::xhtml:td) + 1"/>
        <xxforms:variable name="currentId" model="datatable" select="@currentId"/>
        <xxforms:variable name="currentOrder" model="datatable" select="@currentOrder"/>
        <xhtml:td
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''}
            {{if ({$position} = $currentId)
                then  if($currentOrder = 'descending') then 'yui-dt-desc' else 'yui-dt-asc'
                else ''}}
            {@class}
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="YUI"/>
            <xhtml:div class="yui-dt-liner datatable-cell-content">
                <xsl:apply-templates select="node()" mode="YUI"/>
            </xhtml:div>
        </xhtml:td>
    </xsl:template>

    <xsl:template match="@fr:*" mode="YUI"/>

    <!--

        sortKey mode builds a list of sort keys from a cell content

        Note that we don't bother to take text nodes into account, assuming that
        they are constant and should not influence the sort order...

    -->

    <xsl:template match="*" mode="sortKey">
        <xsl:apply-templates select="*" mode="sortKey"/>
    </xsl:template>

    <xsl:template match="xforms:output" mode="sortKey">
        <xpath>
            <xsl:value-of select="@ref|@value"/>
        </xpath>
    </xsl:template>

    <!-- 
    
    Pass 1 : create a body element if missing
    
    Note (common to pass 1, 2, 3, 4): replace xsl:copy-of by xsl:apply-templates if needed! 
    
    -->

    <xsl:template match="/fr:datatable" mode="pass1">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="xhtml:colgroup|xhtml:thead|xhtml:tbody"/>
            <xsl:if test="not(xhtml:tbody)">
                <xhtml:tbody>
                    <xsl:copy-of select="*[not(self::tcolgroup|self::thead)]"/>
                </xhtml:tbody>
            </xsl:if>
        </xsl:copy>
    </xsl:template>

    <!-- 
        
        Pass 2 : expand /fr:datatable/xhtml:tbody/xhtml:tr[@repeat-nodeset]
        
    -->

    <xsl:template match="/fr:datatable/xhtml:tbody/xhtml:tr[@repeat-nodeset]" mode="pass2">
        <xforms:repeat nodeset="{@repeat-nodeset}">
            <xsl:copy>
                <xsl:copy-of select="@*[name() != 'repeat-nodeset']|node()"/>
            </xsl:copy>
        </xforms:repeat>
    </xsl:template>

    <!-- 
        
        Pass 3 : expand /fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/td[@repeat-nodeset]
        and /fr:datatable/xhtml:thead/xhtml:tr/th[@repeat-nodeset]
        
        Note: do not merge with pass 2 unless you update these XPath expressions to work with 
        xhtml:tr[@repeat-nodeset]
        
    -->

    <xsl:template
        match="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/td[@repeat-nodeset]|/fr:datatable/xhtml:thead/xhtml:tr/th[@repeat-nodeset]"
        mode="pass3">
        <xforms:repeat nodeset="{@repeat-nodeset}">
            <xsl:copy>
                <xsl:copy-of select="@*[name() != 'repeat-nodeset']|node()"/>
            </xsl:copy>
        </xforms:repeat>
    </xsl:template>

    <!-- 
        
        Pass 4 : create a header element if missing
        
    -->

    <xsl:template match="/fr:datatable" mode="pass4">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="xhtml:colgroup|xhtml:thead"/>
            <xsl:if test="not(xhtml:thead)">
                <xhtml:thead>
                    <xhtml:tr>
                        <xsl:apply-templates
                            select="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/*"
                            mode="pass4-header"/>
                    </xhtml:tr>
                </xhtml:thead>
            </xsl:if>
            <xsl:copy-of select="xhtml:tbody"/>
        </xsl:copy>
    </xsl:template>

    <!-- 
        
        Pass 4-header : populate the a header element if missing
        (called by pass4)
        
    -->

    <xsl:template match="*" mode="pass4-header"/>

    <xsl:template match="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xforms:repeat"
        mode="pass4-header">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="pass4-header"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template
        match="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td|/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xforms:repeat/xhtml:td"
        mode="pass4-header">
        <xhtml:th>
            <xsl:apply-templates select="@*" mode="pass4-header"/>
            <xsl:apply-templates select="xforms:output[xforms:label][1]" mode="pass4-header"/>
        </xhtml:th>
    </xsl:template>

    <xsl:template
        match="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td/xforms:output[@ref]|/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xforms:repeat/xhtml:td/xforms:output[@ref]"
        mode="pass4-header">
        <xforms:group>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="xforms:label/*"/>
        </xforms:group>
    </xsl:template>

    <xsl:template
        match="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td/xforms:output|/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xforms:repeat/xhtml:td/xforms:output"
        mode="pass4-header">
        <xsl:value-of select="xforms:label"/>
    </xsl:template>

    <!-- 
        ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        
        Below this point, the templates belong to the new implementation that supports dynamic columns
    
        ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    -->

    <xsl:template match="/fr:datatable" mode="dynamic">
        <!-- Matches the bound element -->

        <xsl:if test="not(xhtml:thead)">
            <xsl:message terminate="yes">Datatable components should include a thead
                element.</xsl:message>
        </xsl:if>
        <xsl:if test="not(xhtml:tbody)">
            <xsl:message terminate="yes">Datatable components should include a tbody
                element.</xsl:message>
        </xsl:if>

        <xsl:variable name="columns">
            <xsl:apply-templates select="xhtml:thead/xhtml:tr[1]/*" mode="dyn-columns"/>
        </xsl:variable>

        <xhtml:div id="{$id}-container">
            <xsl:copy-of select="namespace::*"/>

            <xforms:model id="datatable-model">
                <xforms:instance id="datatable-instance">
                    <columns xmlns="" currentSortColumn="-1">
                        <xsl:for-each select="$columns/*">
                            <xsl:copy>
                                <xsl:attribute name="nbColumns"/>
                                <xsl:attribute name="index"/>
                                <xsl:attribute name="currentSortOrder"/>
                                <xsl:attribute name="nextSortOrder"/>
                                <xsl:attribute name="type"/>
                                <xsl:attribute name="pathToFirstNode"/>
                                <xsl:copy-of select="@*"/>
                            </xsl:copy>
                        </xsl:for-each>
                    </columns>
                </xforms:instance>
                <xforms:bind nodeset="column/@nbColumns" calculate="1"/>
                <xforms:bind nodeset="columnSet/@nbColumns" calculate="count(../column)"/>
                <xforms:bind nodeset="//@index" calculate="count(../preceding::column) + 1"/>
                <xforms:bind nodeset="//column/@currentSortOrder"
                    calculate="if (../@index = /*/@currentSortColumn) then . else 'none'"/>
                <xforms:bind nodeset="//column/@nextSortOrder"
                    calculate="if (../@index = /*/@currentSortColumn) then if (../@currentSortOrder = 'ascending') then 'descending' else 'ascending' else 'ascending'"/>
                <xforms:bind nodeset="//column/@pathToFirstNode"
                    calculate="concat('xxforms:component-context()/({/*/xhtml:tbody/xforms:repeat/@nodeset})[1]/(', ../@sortKey, ')')"/>
                <xforms:bind nodeset="//column[@fr:sortType]/@type" calculate="../@fr:sortType"/>
                <xforms:bind nodeset="//column[not(@fr:sortType)]/@type"
                    calculate="for $value in xxforms:evaluate(../@pathToFirstNode)
                        return if ($value instance of node())
                        then if (xxforms:type($value) = ({$numberTypesEnumeration}))
                            then 'number'
                            else 'text'
                        else if ($value instance of xs:decimal)
                            then 'number'
                            else 'text'"
                />
            </xforms:model>

            <xxforms:variable name="currentSortOrder" model="datatable-model"
                select="instance('datatable-instance')/@currentSortOrder"/>
            <xxforms:variable name="currentSortColumn" model="datatable-model"
                select="instance('datatable-instance')/@currentSortColumn"/>

            <xsl:if test="$debug">
                <xhtml:div style="border:thin solid black" id="debug">
                    <xhtml:h3>Local instance:</xhtml:h3>
                    <xforms:group model="datatable-model" instance="datatable-instance">
                        <xhtml:div id="debug-columns">
                            <xhtml:p>
                                <xforms:output value="name()"/>
                            </xhtml:p>
                            <xhtml:ul>
                                <xforms:repeat nodeset="@*">
                                    <xhtml:li id="debug-top-level-attribute">
                                        <xforms:output ref=".">
                                            <xforms:label>
                                                <xforms:output value="concat(name(), ': ')"/>
                                            </xforms:label>
                                        </xforms:output>
                                    </xhtml:li>
                                </xforms:repeat>
                            </xhtml:ul>
                        </xhtml:div>
                        <xforms:repeat nodeset="*|//column">
                            <xhtml:div id="debug-column">
                                <xhtml:p>
                                    <xforms:output value="name()"/>
                                </xhtml:p>
                                <xhtml:ul>
                                    <xforms:repeat nodeset="@*">
                                        <xhtml:li>
                                            <xforms:output ref="." id="debug-column-attribute">
                                                <xforms:label>
                                                  <xforms:output value="concat(name(), ': ')"/>
                                                </xforms:label>
                                            </xforms:output>
                                        </xhtml:li>
                                    </xforms:repeat>
                                </xhtml:ul>
                            </xhtml:div>
                        </xforms:repeat>
                    </xforms:group>
                </xhtml:div>
            </xsl:if>

            <xsl:if test="$hasLoadingFeature">
                <xxforms:variable name="loading" xbl:attr="select=loading"/>
            </xsl:if>

            <xforms:group>
                <xsl:attribute name="ref">
                    <xsl:text>xxforms:component-context()</xsl:text>
                    <xsl:if test="$hasLoadingFeature">[not($loading = true())]</xsl:if>
                </xsl:attribute>

                <xforms:action ev:event="xforms-enabled">
                    <xxforms:script> YAHOO.log("Enabling datatable id <xsl:value-of select="$id"
                        />","info"); ORBEON.widgets.datatable.init(this, <xsl:value-of
                            select="$innerTableWidth"/>); </xxforms:script>
                </xforms:action>

                <xhtml:table id="{$id}-table"
                    class="datatable datatable-{$id} yui-dt-table {if ($scrollV) then 'fr-scrollV' else ''}  {if ($scrollH) then 'fr-scrollH' else ''} "
                    style="{$height} {$width}">
                    <!-- Copy attributes that are not parameters! -->
                    <xsl:apply-templates select="@*[not(name() = ($parameters/*, 'id' ))]"
                        mode="dynamic"/>
                    <xhtml:thead id="{$id}-thead">
                        <xhtml:tr class="yui-dt-first yui-dt-last {@class}" id="{$id}-thead-tr">
                            <xsl:apply-templates select="$columns/*" mode="dynamic"/>
                        </xhtml:tr>
                    </xhtml:thead>
                    <xsl:apply-templates select="xhtml:tbody" mode="dynamic"/>
                </xhtml:table>

            </xforms:group>

            <xsl:if test="$hasLoadingFeature">
                <xforms:group ref="xxforms:component-context()[$loading = true()]">
                    <xhtml:span class="yui-dt yui-dt-scrollable" style="display: table; ">
                        <xhtml:span class="yui-dt-hd"
                            style="border: 1px solid rgb(127, 127, 127); display: table-cell;">
                            <xhtml:table class="datatable  yui-dt-table" style="{$height} {$width}">
                                <xhtml:thead>
                                    <xhtml:tr class="yui-dt-first yui-dt-last">
                                        <xsl:apply-templates select="$columns/*"
                                            mode="dynamic-loadingIndicator"/>
                                    </xhtml:tr>
                                </xhtml:thead>
                                <xhtml:tbody>
                                    <xhtml:tr>
                                        <xhtml:td colspan="{count($columns/*)}"
                                            class="fr-datatable-is-loading"/>
                                    </xhtml:tr>
                                </xhtml:tbody>
                            </xhtml:table>
                        </xhtml:span>
                    </xhtml:span>
                </xforms:group>
            </xsl:if>


        </xhtml:div>
        <!-- End of template on the bound element -->
    </xsl:template>



    <xsl:template name="header-cell">

        <!-- XXForms variable "columnDesc" is the current column description when we enter here -->

        <!-- <xforms:output value="$columnDesc/@index"/>-->

        <xhtml:div class="yui-dt-liner">
            <xhtml:span class="yui-dt-label">
                <xsl:choose>
                    <xsl:when test="@fr:sortable = 'true'">
                        <xforms:trigger appearance="minimal">
                            <xforms:label>
                                <xsl:apply-templates select="node()" mode="dynamic"/>
                            </xforms:label>
                            <xforms:hint>Click to sort <xforms:output
                                    value="$columnDesc/@nextSortOrder"/></xforms:hint>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:setvalue ref="$columnDesc/@currentSortOrder"
                                    value="$columnDesc/@nextSortOrder"/>
                                <xforms:setvalue ref="$currentSortColumn" value="$columnDesc/@index"
                                />
                            </xforms:action>
                        </xforms:trigger>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="node()" mode="dynamic"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xhtml:span>
        </xhtml:div>

    </xsl:template>

    <xsl:template match="column|columnSet" priority="1" mode="dynamic">
        <xsl:apply-templates select="header" mode="dynamic"/>
    </xsl:template>

    <xsl:template match="header" mode="dynamic">
        <xsl:apply-templates select="*" mode="dynamic"/>
    </xsl:template>

    <xsl:template match="header/xhtml:th" mode="dynamic">
        <xxforms:variable name="index" select="{count(../../preceding-sibling::*) + 1}"/>
        <xxforms:variable name="columnDesc" model="datatable-model"
            select="instance('datatable-instance')/*[position() = $index]"/>
        <xhtml:th
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {if (@fr:resizeable = 'true') then 'yui-dt-resizeable' else ''} 
            {{if ($columnDesc/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($columnDesc/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            
             {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xsl:call-template name="header-cell"/>

        </xhtml:th>
    </xsl:template>

    <xsl:template match="header/xforms:repeat/xhtml:th" mode="dynamic">
        <xxforms:variable name="position" select="position()"/>
        <xxforms:variable name="index" select="{count(../../../preceding-sibling::*) + 1}"/>
        <xxforms:variable name="columnSet" model="datatable-model"
            select="instance('datatable-instance')/*[position() = $index]"/>
        <xxforms:variable name="columnIndex" model="datatable-model"
            select="$columnSet/@index + $position - 1"/>
        <xxforms:variable name="column" model="datatable-model"
            select="$columnSet/column[@index = $columnIndex]"/>
        <xhtml:th
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {if (@fr:resizeable = 'true') then 'yui-dt-resizeable' else ''} 
            {{if ($column/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($column/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xforms:group ref=".">
                <xforms:action ev:event="xforms-enabled">
                    <!--<xforms:delete nodeset="$columnSet/column[@position = $position]"/>-->
                    <xforms:insert context="$columnSet" nodeset="column"
                        origin="xxforms:element('column', (
                                xxforms:attribute('position', $position),
                                xxforms:attribute('nbColumns', 1),
                                xxforms:attribute('index', $columnIndex),
                                xxforms:attribute('sortKey', concat( '(',  $columnSet/@nodeset, ')[', $position , ']/', $columnSet/@sortKey)),
                                xxforms:attribute('currentSortOrder', ''),
                                xxforms:attribute('nextSortOrder', ''),
                                xxforms:attribute('type', ''),
                                xxforms:attribute('pathToFirstNode', ''),
                                $columnSet/@fr:sortable,
                                $columnSet/@fr:resizeable,
                                $columnSet/@fr:sortType
                                ))"
                        if="not($columnSet/column[@position = $position])
                           "
                    />
                </xforms:action>
            </xforms:group>

            <xxforms:variable name="columnDesc" select="$columnSet/column[@position = $position]"/>

            <xsl:call-template name="header-cell"/>

        </xhtml:th>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody" mode="dynamic">
        <xhtml:tbody class="yui-dt-data {@class}" id="{$id}-tbody">
            <xsl:apply-templates select="@*[not(name() = ('class', 'id'))]|node()" mode="dynamic"/>
        </xhtml:tbody>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody/xforms:repeat" mode="dynamic">
        <xxforms:variable name="currentSortColumnIndex" model="datatable-model"
            select="@currentSortColumn"/>
        <xxforms:variable name="currentSortColumn" model="datatable-model"
            select="(//column)[@index=$currentSortColumnIndex]"/>
        <xforms:repeat>
            <xsl:attribute name="nodeset">
                <xsl:if test="$paginated">(</xsl:if>
                <xsl:text>if (not($currentSortColumn) or $currentSortColumn/@currentSortOrder = 'none') then </xsl:text>
                <xsl:value-of select="@nodeset"/>
                <xsl:text> else exf:sort(</xsl:text>
                <xsl:value-of select="@nodeset"/>
                <xsl:text>, $currentSortColumn/@sortKey , $currentSortColumn/@type, $currentSortColumn/@currentSortOrder)</xsl:text>
                <xsl:if test="$paginated">)[position() >= ($page - 1) * <xsl:value-of
                        select="$rowsPerPage"/> + 1 and position() &lt;= $page * <xsl:value-of
                        select="$rowsPerPage"/>]</xsl:if>
            </xsl:attribute>
            <xsl:apply-templates select="@*[not(name()='nodeset')]|node()" mode="dynamic"/>
        </xforms:repeat>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody/xforms:repeat/xhtml:tr" mode="dynamic">
        <xhtml:tr
            class="
            {{if (position() = 1) then 'yui-dt-first' else '' }}
            {{if (position() = last()) then 'yui-dt-last' else '' }}
            {{if (position() mod 2 = 0) then 'yui-dt-odd' else 'yui-dt-even' }}
            {{if (xxforms:index() = position()) then 'yui-dt-selected' else ''}}
            {@class}"
            style="height: auto;">
            <xsl:apply-templates select="@*[name() != 'class']|node()" mode="dynamic"/>
        </xhtml:tr>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td" mode="dynamic">
        <xxforms:variable name="index" select="{count(preceding-sibling::*) + 1}"/>
        <xxforms:variable name="columnDesc" model="datatable-model"
            select="instance('datatable-instance')/*[position() = $index]"/>

        <xhtml:td
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {{if ($columnDesc/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($columnDesc/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}            
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xhtml:div class="yui-dt-liner">
                <xsl:apply-templates select="node()" mode="dynamic"/>
            </xhtml:div>
        </xhtml:td>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody/xforms:repeat/xhtml:tr/xforms:repeat/xhtml:td"
        mode="dynamic">
        <xxforms:variable name="position" select="position()"/>
        <xxforms:variable name="index" select="{count(../preceding-sibling::*) + 1}"/>
        <xxforms:variable name="columnSet" model="datatable-model"
            select="instance('datatable-instance')/*[position() = $index]"/>
        <xxforms:variable name="columnIndex" model="datatable-model"
            select="$columnSet/@index + $position - 1"/>
        <xxforms:variable name="column" model="datatable-model"
            select="$columnSet/column[@index = $columnIndex]"/>
        <xhtml:td
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {{if ($column/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($column/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}            
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xhtml:div class="yui-dt-liner">
                <xsl:apply-templates select="node()" mode="dynamic"/>
            </xhtml:div>
        </xhtml:td>
    </xsl:template>

    <xsl:template match="@fr:*" mode="dynamic"/>

    <!-- 
        
        sortKey mode builds a list of sort keys from a cell content 
        
        Note that we don't bother to take text nodes into account, assuming that
        they are constant and should not influence the sort order...
        
    -->

    <xsl:template match="*" mode="dyn-sortKey" priority="-0.25">
        <xsl:apply-templates select="*" mode="dyn-sortKey"/>
    </xsl:template>

    <xsl:template match="xforms:output" mode="dyn-sortKey">
        <xpath>
            <xsl:value-of select="@ref|@value"/>
        </xpath>
    </xsl:template>


    <!-- 

        Column mode is used to consolidate information about columns
        from theader and tbody

    -->

    <xsl:template match="/*/xhtml:thead/xhtml:tr/*" mode="dyn-columns">
        <xsl:message terminate="yes">Unxepected element (<xsl:value-of select="name()"/> found in a
            datatable header (expecting either xhtml:th or xforms:repeat).</xsl:message>
    </xsl:template>

    <xsl:template match="/*/xhtml:thead/xhtml:tr/xhtml:th" mode="dyn-columns" priority="1">
        <xsl:variable name="position" select="count(preceding-sibling::*) + 1"/>
        <xsl:variable name="body"
            select="/*/xhtml:tbody/xforms:repeat/xhtml:tr/*[position() = $position]"/>
        <xsl:if test="not($body/self::xhtml:td)">
            <xsl:message terminate="yes">Datatable: mismatch, element position <xsl:value-of
                    select="$position"/> is a <xsl:value-of select="name()"/> in the header and a
                    <xsl:value-of select="name($body)"/> in the body.</xsl:message>repeat </xsl:if>
        <xsl:variable name="sortKey">
            <xsl:apply-templates select="$body" mode="dyn-sortKey"/>
        </xsl:variable>
        <column sortKey="{$sortKey}" type="" xmlns="">
            <xsl:copy-of select="@*"/>
            <header>
                <xsl:copy-of select="."/>
            </header>
            <body>
                <xsl:copy-of select="$body"/>
            </body>
        </column>
    </xsl:template>

    <xsl:template match="/*/xhtml:thead/xhtml:tr/xforms:repeat" mode="dyn-columns" priority="1">
        <xsl:variable name="position" select="count(preceding-sibling::*) + 1"/>
        <xsl:variable name="body"
            select="/*/xhtml:tbody/xforms:repeat/xhtml:tr/*[position() = $position]"/>
        <xsl:if test="not($body/self::xforms:repeat)">
            <xsl:message terminate="yes">Datatable: mismatch, element position <xsl:value-of
                    select="$position"/> is a <xsl:value-of select="name()"/> in the header and a
                    <xsl:value-of select="name($body)"/> in the body.</xsl:message>
        </xsl:if>
        <xsl:variable name="sortKey">
            <xsl:apply-templates select="$body" mode="dyn-sortKey"/>
        </xsl:variable>
        <columnSet sortKey="{$sortKey}" xmlns="">
            <xsl:copy-of select="$body/@nodeset|xhtml:th/@*"/>
            <header>
                <xsl:copy-of select="."/>
            </header>
            <body>
                <xsl:copy-of select="$body"/>
            </body>
        </columnSet>
    </xsl:template>

    <xsl:template match="column" mode="dyn-loadingIndicator">
        <xsl:apply-templates select="header/xhtml:th" mode="dynamic"/>
    </xsl:template>

    <xsl:variable name="fakeColumn">
        <header xmlns="">
            <xhtml:th class="fr-datatable-columnset-loading-indicator"
                >&#160;...&#160;</xhtml:th>
        </header>
    </xsl:variable>

    <xsl:template match="columnSet" mode="dyn-loadingIndicator">
        <xsl:apply-templates select="$fakeColumn/header/xhtml:th"/>
    </xsl:template>



</xsl:transform>
