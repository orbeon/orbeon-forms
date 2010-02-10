<?xml version="1.0" encoding="UTF-8"?>
<!--
    Copyright (C) 2009-2010 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!DOCTYPE xsl:transform [
<!ENTITY neutralElementsInHeaderOrBody "self::xxforms:variable|self::xforms:action|self::xforms:setvalue|self::xforms:insert|self::xforms:delete|self::xforms:setindex|self::xforms:toggle|self::xforms:setfocus|self::xforms:dispatch|self::xforms:rebuild|self::xforms:recalculate|self::xforms:revalidate|self::xforms:refresh|self::xforms:reset|self::xforms:load|self::xforms:send">
]>
<xsl:transform xmlns:xforms="http://www.w3.org/2002/xforms" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms" xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:xxbl="http://orbeon.org/oxf/xml/xbl" xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:exf="http://www.exforms.org/exf/1-0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">

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
            <xsl:text>', $fr-dt-datatable-instance)</xsl:text>
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
    <xsl:variable name="scrollH" select="$pass4/fr:datatable/@scrollable = ('horizontal', 'both') and $pass4/fr:datatable/@width"/>
    <xsl:variable name="scrollV" select="$pass4/fr:datatable/@scrollable = ('vertical', 'both') and $pass4/fr:datatable/@height"/>
    <xsl:variable name="scrollable" select="$scrollH or $scrollV"/>
    <xsl:variable name="height" select="if ($scrollV) then concat('height: ', $pass4/fr:datatable/@height, ';') else ''"/>
    <xsl:variable name="width" select="if ($pass4/fr:datatable/@width) then concat('width: ', $pass4/fr:datatable/@width, ';') else ''"/>
    <xsl:variable name="id">
        <xsl:choose>
            <xsl:when test="$pass4/fr:datatable/@id">
                <id xxbl:scope="outer">
                    <xsl:value-of select="$pass4/fr:datatable/@id"/>
                </id>
            </xsl:when>
            <xsl:otherwise>
                <id xxbl:scope="inner">
                    <xsl:value-of select="generate-id($pass4/fr:datatable)"/>
                </id>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:variable>
    <xsl:variable name="paginated" select="$pass4/fr:datatable/@paginated = 'true'"/>
    <xsl:variable name="rowsPerPage"
        select="if ($pass4/fr:datatable/@rowsPerPage castable as xs:integer) then $pass4/fr:datatable/@rowsPerPage cast as xs:integer else 10"/>
    <xsl:variable name="maxNbPagesToDisplay"
        select="if ($pass4/fr:datatable/@maxNbPagesToDisplay castable as xs:integer) then $pass4/fr:datatable/@maxNbPagesToDisplay cast as xs:integer else -1"/>
    <xsl:variable name="sortAndPaginationMode" select="$pass4/fr:datatable/@sortAndPaginationMode"/>
    <xsl:variable name="isExternallySortedAndPaginated" select="$sortAndPaginationMode = 'external'"/>
    <xsl:variable name="isInternallySortedAndPaginated" select="not($isExternallySortedAndPaginated)"/>
    <xsl:variable name="innerTableWidth"
        select="if ($pass4/fr:datatable/@innerTableWidth) then concat(&quot;'&quot;, $pass4/fr:datatable/@innerTableWidth, &quot;'&quot;) else 'null'"/>
    <xsl:variable name="hasLoadingFeature" select="count($pass4/fr:datatable/@loading) = 1"/>
    <xsl:variable name="debug" select="$pass4/fr:datatable/@debug = 'true'"/>

    <!-- And some more -->

    <xsl:variable name="repeatNodeset" select="$pass4/fr:datatable/xhtml:tbody/xforms:repeat/@nodeset"/>

    <xsl:template match="@*|node()" mode="#all" priority="-100" name="identity">
        <!-- Default template == identity -->
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <!-- Helper functions to filter out row elements that are significant for the datatable geometry -->

    <xsl:function name="fr:significantPrecedingsInRow" as="item()*">
        <xsl:param name="node"/>
        <xsl:sequence select="$node/preceding-sibling::*[not(&neutralElementsInHeaderOrBody;)]"/>
    </xsl:function>

    <xsl:function name="fr:significantPositionInRow" as="xs:integer">
        <xsl:param name="node"/>
        <xsl:sequence select="count(fr:significantPrecedingsInRow($node)) + 1"/>
    </xsl:function>

    <xsl:function name="fr:significantElementsInRow" as="item()*">
        <xsl:param name="row" as="node()"/>
        <xsl:sequence select="$row/*[not(&neutralElementsInHeaderOrBody;)]"/>
    </xsl:function>

    <xsl:function name="fr:significantElementInRowAtPosition" as="node()">
        <xsl:param name="row" as="node()"/>
        <xsl:param name="index" as="xs:integer"/>
        <xsl:sequence select="fr:significantElementsInRow($row)[$index]"/>
    </xsl:function>

    <xsl:template match="/">

        <xsl:apply-templates select="$pass4/fr:datatable" mode="dynamic"/>

    </xsl:template>

    <xsl:template name="fr-goto-page">
        <xsl:param name="fr-new-page"/>
        <xsl:choose>
            <xsl:when test="$isExternallySortedAndPaginated">
                <xforms:dispatch ev:event="DOMActivate" name="fr-goto-page" target="fr.datatable">
                    <xxforms:context name="fr-new-page" select="{$fr-new-page}"/>
                </xforms:dispatch>
            </xsl:when>
            <xsl:otherwise>
                <xforms:setvalue ev:event="DOMActivate" model="datatable-model" ref="instance('page')" value="{$fr-new-page}"/>
            </xsl:otherwise>
        </xsl:choose>
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

    <xsl:template match="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/td[@repeat-nodeset]|/fr:datatable/xhtml:thead/xhtml:tr/th[@repeat-nodeset]"
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
                        <xsl:apply-templates select="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/*" mode="pass4-header"/>
                    </xhtml:tr>
                </xhtml:thead>
            </xsl:if>
            <xsl:apply-templates select="xhtml:tbody" mode="pass4"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template
        match="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td/xforms:output[xforms:label][1]/xforms:label
        |/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xforms:repeat/xhtml:td/xforms:output[xforms:label][1]/xforms:label"
        mode="pass4"/>

    <!-- 
        
        Pass 4-header : populate the a header element if missing
        (called by pass4)
        
    -->

    <xsl:template match="*" mode="pass4-header"/>

    <xsl:template match="/fr:datatable/xhtml:tbody/xforms:repeat/xhtml:tr/xforms:repeat" mode="pass4-header">
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
            <xsl:message terminate="yes">Datatable components should include a thead element.</xsl:message>
        </xsl:if>
        <xsl:if test="not(xhtml:tbody)">
            <xsl:message terminate="yes">Datatable components should include a tbody element.</xsl:message>
        </xsl:if>

        <xsl:variable name="columns">
            <xsl:apply-templates select="xhtml:thead/xhtml:tr[1]/*" mode="dyn-columns"/>
        </xsl:variable>

        <xforms:group xbl:attr="model context ref bind" xxbl:scope="outer" id="{$id}-container">
            <xsl:copy-of select="namespace::*"/>

            <xforms:model id="datatable-model" xxbl:scope="inner">
                <xforms:instance id="datatable-instance">
                    <columns xmlns="">
                        <xsl:if test="$isInternallySortedAndPaginated">
                            <xsl:attribute name="currentSortColumn">-1</xsl:attribute>
                            <xsl:attribute name="default">true</xsl:attribute>
                        </xsl:if>
                        <xsl:for-each select="$columns/*[self::column|self::columnSet]">
                            <xsl:copy>
                                <xsl:attribute name="nbColumns"/>
                                <xsl:attribute name="index"/>
                                <xsl:if test="$isInternallySortedAndPaginated">
                                    <xsl:attribute name="currentSortOrder"/>
                                    <xsl:attribute name="nextSortOrder"/>
                                    <xsl:attribute name="type"/>
                                    <xsl:attribute name="pathToFirstNode"/>
                                </xsl:if>
                                <xsl:copy-of select="@*"/>
                            </xsl:copy>
                        </xsl:for-each>
                    </columns>
                </xforms:instance>
                <xforms:bind nodeset="column/@nbColumns" calculate="1"/>
                <xforms:bind nodeset="columnSet/@nbColumns" calculate="count(../column)"/>
                <xforms:bind nodeset="//@index" calculate="count(../preceding::column) + 1"/>
                <xforms:bind nodeset="//column/@currentSortOrder"
                    calculate="if (/*/@default='true' and ../@fr:sorted) then ../@fr:sorted else if (../@index = /*/@currentSortColumn) then . else 'none'"/>
                <xforms:bind nodeset="//column/@nextSortOrder" calculate="if (../@currentSortOrder = 'ascending') then 'descending' else 'ascending'"/>
                <xxforms:variable name="repeatNodeset">
                    <xsl:value-of select="$repeatNodeset"/>
                </xxforms:variable>
                <xforms:bind nodeset="//column/@pathToFirstNode"
                    calculate="concat('xxforms:component-context()/(', $repeatNodeset, ')[1]/(', ../@sortKey, ')')"/>
                <xforms:bind nodeset="//column[@fr:sortType]/@type" calculate="../@fr:sortType"/>
                <!--<xforms:bind nodeset="//column[not(@fr:sortType)]/@type"
                    calculate="for $value in xxforms:evaluate(../@pathToFirstNode)
                        return if ($value instance of node())
                        then if (xxforms:type($value) = ({$numberTypesEnumeration}))
                            then 'number'
                            else 'text'
                        else if ($value instance of xs:decimal)
                            then 'number'
                            else 'text'"/>
-->

                <xsl:if test="$paginated">
                    <xforms:instance id="page">
                        <page nbPages="" xmlns="">1</page>
                    </xforms:instance>


                    <!-- 
                       Uncomment when https://forge.ow2.org/tracker/index.php?func=detail&aid=314437&group_id=168&atid=350207 will be fixed
                       <xsl:if test="not($sortAndPaginationMode='external')">
                        <xxforms:variable name="nbRows">
                            <xxforms:sequence select="count({$repeatNodeset})" xxbl:scope="outer"/>
                        </xxforms:variable>
                        <xxforms:variable name="nbPages"
                            select="ceiling($nbRows div {$rowsPerPage}) cast as xs:integer"/>
                        <xforms:bind nodeset="instance('page')">
                            <xforms:bind nodeset="@nbPages" calculate="$nbPages"/>
                            <!-\-<xforms:bind nodeset="."
                               calculate="if (. cast as xs:integer > $nbPages) then $nbPages else ."
                               />-\->
                        </xforms:bind>
                    </xsl:if>-->

                </xsl:if>

            </xforms:model>

            <xsl:choose>
                <xsl:when test="$paginated and $isInternallySortedAndPaginated">
                    <xxforms:variable name="page" model="datatable-model" select="instance('page')" xxbl:scope="inner"/>
                    <xxforms:variable name="nbRows" xxbl:scope="inner">
                        <xxforms:sequence select="count({$repeatNodeset})" xxbl:scope="outer"/>
                    </xxforms:variable>
                    <!-- Workaround for a limitation where an expression refering to non relevant contexts returns an empty sequence -->
                    <xxforms:variable name="nbPages" select="if ($nbRows) then ceiling($nbRows div {$rowsPerPage}) cast as xs:integer else 0"
                        xxbl:scope="inner"/>
                </xsl:when>

                <xsl:when test="$paginated and $isExternallySortedAndPaginated">
                    <xxforms:variable name="page" xxbl:scope="inner">
                        <xxforms:sequence xbl:attr="select=page" xxbl:scope="outer"/>
                    </xxforms:variable>
                    <xxforms:variable name="nbPages" xxbl:scope="inner">
                        <xxforms:sequence xbl:attr="select=nbPages" xxbl:scope="outer"/>
                    </xxforms:variable>
                </xsl:when>

            </xsl:choose>


            <xsl:choose>
                <xsl:when test="$paginated and $maxNbPagesToDisplay &lt; 0">
                    <xxforms:variable name="pages" select="1 to xs:integer($nbPages)" xxbl:scope="inner"/>
                </xsl:when>
                <xsl:when test="$paginated">
                    <xxforms:variable name="maxNbPagesToDisplay" select="{$maxNbPagesToDisplay} cast as xs:integer" xxbl:scope="inner"/>
                    <xxforms:variable name="radix" select="floor(($maxNbPagesToDisplay - 2) div 2) cast as xs:integer" xxbl:scope="inner"/>
                    <xxforms:variable name="minPage"
                        select="
                        (if ($page > $radix)
                        then if ($nbPages >= $page + $radix)
                        then ($page - $radix)
                        else max((1, $nbPages - $maxNbPagesToDisplay + 1))
                        else 1) cast as xs:integer"
                        xxbl:scope="inner"/>
                    <xxforms:variable name="pages"
                        select="if ($nbPages castable as xs:integer) 
                                then 1 to xs:integer($nbPages)
                                else ()"
                        xxbl:scope="inner"/>
                </xsl:when>
            </xsl:choose>

            <xsl:variable name="pagination">
                <!-- TODO: fix scopes -->
                <xsl:if test="$paginated">
                    <xhtml:div class="yui-dt-paginator yui-pg-container" style="">

                        <xforms:group appearance="xxforms:internal" xxbl:scope="inner">

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
                                            <!-- <xforms:hint>Current page (edit to move to another
                                            page)</xforms:hint>-->
                                        </xforms:output>
                                    </xforms:group>
                                    <xforms:group ref=".[. != $page and $display = 'page']">
                                        <xxforms:variable name="targetPage" select="."/>
                                        <xforms:trigger class="yui-pg-page" appearance="minimal">
                                            <xforms:label>
                                                <xforms:output value="."/>
                                            </xforms:label>
                                            <xsl:call-template name="fr-goto-page">
                                                <xsl:with-param name="fr-new-page">$targetPage</xsl:with-param>
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

                        </xforms:group>
                    </xhtml:div>

                </xsl:if>
            </xsl:variable>

            <xxforms:variable name="currentSortOrder" model="datatable-model" select="instance('datatable-instance')/@currentSortOrder"
                xxbl:scope="inner"/>
            <xxforms:variable name="currentSortColumn" model="datatable-model" select="instance('datatable-instance')/@currentSortColumn"
                xxbl:scope="inner"/>

            <xsl:if test="$debug">
                <xhtml:div style="border:thin solid black" class="fr-dt-debug fr-dt-debug-{id}">
                    <xhtml:h3>Local instance:</xhtml:h3>
                    <xforms:group model="datatable-model" instance="datatable-instance" xxbl:scope="inner">
                        <xhtml:div class="fr-dt-debug-columns" id="debug-columns">
                            <xhtml:p>
                                <xforms:output value="name()"/>
                            </xhtml:p>
                            <xhtml:ul>
                                <xforms:repeat nodeset="@*">
                                    <xhtml:li>
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
                                            <xforms:output ref=".">
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
                <xxforms:variable name="fr-dt-loading" xbl:attr="select=loading"/>
            </xsl:if>

            <xsl:copy-of select="$pagination"/>

            <xxforms:variable name="group-ref" xxbl:scope="inner">
                <xxforms:sequence select=".{if ($hasLoadingFeature) then '[not($fr-dt-loading = true())]' else ''}" xxbl:scope="outer"/>
            </xxforms:variable>

            <xxforms:script ev:event="xforms-enabled" ev:target="fr-dt-group" xxbl:scope="inner"> YAHOO.log("Enabling datatable id <xsl:value-of
                    select="$id"/>","info"); ORBEON.widgets.datatable.init(this, <xsl:value-of select="$innerTableWidth"/>); </xxforms:script>

            <xforms:group ref="$group-ref" id="fr-dt-group" xxbl:scope="inner">

                <!--  <xforms:group appearance="xxforms:internal" xxbl:scope="outer"> would be better but doesn't work! -->
                <xforms:group xxbl:scope="outer">
                    <xhtml:div class="yui-dt yui-dt-scrollable">
                        <xhtml:div class="yui-dt-hd">
                            <xhtml:table id="{$id}-table"
                                class="{@class} datatable datatable-{$id} yui-dt-table {if ($scrollV) then 'fr-scrollV' else ''}  {if ($scrollH) then 'fr-scrollH' else ''} "
                                style="{$height} {$width}">
                                <!-- Copy attributes that are not parameters! -->
                                <xsl:apply-templates select="@*[not(name() = ($parameters/*, 'id', 'class'))]" mode="dynamic"/>
                                <xhtml:thead id="{$id}-thead">
                                    <xhtml:tr class="yui-dt-first yui-dt-last {@class}" id="{$id}-thead-tr">
                                        <xsl:apply-templates select="$columns/*" mode="dynamic"/>
                                    </xhtml:tr>
                                </xhtml:thead>
                                <xsl:apply-templates select="xhtml:tbody" mode="dynamic"/>
                            </xhtml:table>
                        </xhtml:div>
                    </xhtml:div>
                </xforms:group>

            </xforms:group>

            <xsl:if test="$hasLoadingFeature">
                <!-- The trick with the spans is working fine for simple case where we don't need to specify the height or width.
                    In other cases, the elements "gain layout" in IE world and the width of the div that contains the 
                    scrollbar takes all the page in IE 6 if not explicitely set...-->
                <xforms:group ref="xxforms:component-context()[$fr-dt-loading = true()]">
                    <xforms:action ev:event="xforms-enabled">
                        <xxforms:script> ORBEON.widgets.datatable.initLoadingIndicator(this, <xsl:value-of select="$scrollV"/>, <xsl:value-of
                                select="$scrollH"/>); </xxforms:script>
                    </xforms:action>
                    <xsl:variable name="tableContent">
                        <xhtml:thead>
                            <xhtml:tr class="yui-dt-first yui-dt-last">
                                <xsl:apply-templates select="$columns/*" mode="dyn-loadingIndicator"/>
                            </xhtml:tr>
                        </xhtml:thead>
                        <xhtml:tbody>
                            <xhtml:tr>
                                <xhtml:td colspan="{count($columns/*)}">
                                    <xhtml:div class="fr-datatable-is-loading" style="{if ($scrollable) then concat( $height, ' ', $width) else ''}"/>
                                </xhtml:td>
                            </xhtml:tr>
                        </xhtml:tbody>
                    </xsl:variable>
                    <xsl:choose>
                        <xsl:when test="$scrollable">
                            <xhtml:div class="yui-dt yui-dt-scrollable" style="{if ($scrollV) then $height else 'height: 95px;'} {$width}">
                                <xhtml:div style="overflow: auto; {if ($scrollV) then $height else 'height: 95px;'} {$width}" class="yui-dt-hd">
                                    <xhtml:table style="" class="datatable datatable-table-scrollV yui-dt-table fr-scrollV">
                                        <xsl:copy-of select="$tableContent"/>
                                    </xhtml:table>
                                </xhtml:div>
                            </xhtml:div>
                        </xsl:when>
                        <xsl:otherwise>
                            <xhtml:span class="yui-dt yui-dt-scrollable" style="display: table; ">
                                <xhtml:span class="yui-dt-hd" style="border: 1px solid rgb(127, 127, 127); display: table-cell;">
                                    <xhtml:table class="datatable  yui-dt-table" style="{$height} {$width}">
                                        <xsl:copy-of select="$tableContent"/>
                                    </xhtml:table>
                                </xhtml:span>
                            </xhtml:span>
                        </xsl:otherwise>
                    </xsl:choose>
                </xforms:group>
            </xsl:if>

            <xsl:copy-of select="$pagination"/>

            <xsl:if test="$paginated and not($sortAndPaginationMode='external')">
                <xforms:group model="datatable-model" ref="instance('page')" appearance="xxforms:internal" xxbl:scope="inner">
                    <xforms:input ref="@nbPages" style="display:none;">
                        <!-- Workaround, see https://forge.ow2.org/tracker/index.php?func=detail&aid=314429&group_id=168&atid=350207 -->
                        <xforms:setvalue ref=".." ev:event="xforms-value-changed"
                            value="if (. cast as xs:integer &gt; @nbPages and @nbPages > 0) then @nbPages else ."/>
                    </xforms:input>
                </xforms:group>
            </xsl:if>

        </xforms:group>
        <!-- End of template on the bound element -->
    </xsl:template>



    <xsl:template name="header-cell">

        <!-- XXForms variable "columnDesc" is the current column description when we enter here -->

        <!-- <xforms:output value="$columnDesc/@index"/>-->

        <xhtml:div class="yui-dt-liner datatable-cell-content">
            <xhtml:span class="yui-dt-label">
                <xsl:choose>
                    <xsl:when test="@fr:sortable = 'true'">
                        <xforms:trigger appearance="minimal">
                            <xforms:label>
                                <xsl:apply-templates select="node()" mode="dynamic"/>
                            </xforms:label>
                            <xsl:choose>
                                <xsl:when test="$isExternallySortedAndPaginated">
                                    <xforms:hint>
                                        <xforms:output value="{@fr:sortMessage}"/>
                                    </xforms:hint>
                                    <xforms:dispatch ev:event="DOMActivate" name="fr-update-sort" target="fr.datatable" xxbl:scope="inner">
                                        <xxforms:context name="fr-column" select="xs:integer($columnDesc/@index)"/>
                                    </xforms:dispatch>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xforms:hint xxbl:scope="inner">Click to sort <xforms:output value="$columnDesc/@nextSortOrder"/></xforms:hint>
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:setvalue ref="$columnDesc/ancestor::columns/@default" xxbl:scope="inner">false</xforms:setvalue>
                                        <xforms:setvalue ref="$columnDesc/@currentSortOrder" value="$columnDesc/@nextSortOrder" xxbl:scope="inner"/>
                                        <xforms:setvalue ref="$currentSortColumn" value="$columnDesc/@index" xxbl:scope="inner"/>
                                    </xforms:action>
                                </xsl:otherwise>
                            </xsl:choose>
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
        <xsl:if test="$sortAndPaginationMode = 'external' and @fr:sortable and not(@fr:sortMessage)">
            <xsl:message terminate="yes">In datatables with sortAndPaginationMode set to "external", sortable columns must have fr:sortMessage
                attributes.</xsl:message>
        </xsl:if>
        <xxforms:variable name="index" select="{count(../../preceding-sibling::*) + 1}" xxbl:scope="inner"/>
        <xxforms:variable name="columnDesc" model="datatable-model" select="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        <xxforms:variable name="fr-dt-columnDesc">
            <xxforms:sequence select="$columnDesc" xxbl:scope="inner"/>
        </xxforms:variable>
        <xhtml:th
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {if (@fr:resizeable = 'true') then 'yui-dt-resizeable' else ''} 
            {{if ($fr-dt-columnDesc/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-columnDesc/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            
             {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xsl:call-template name="header-cell"/>

        </xhtml:th>
    </xsl:template>

    <xsl:template match="header/xforms:repeat/xhtml:th" mode="dynamic">
        <xsl:if test="$sortAndPaginationMode = 'external' and @fr:sortable and not(@fr:sortMessage)">
            <xsl:message terminate="yes">In datatables with sortAndPaginationMode set to "external", sortable columns must have fr:sortMessage
                attributes.</xsl:message>
        </xsl:if>

        <xxforms:variable name="position" xxbl:scope="inner">
            <xxforms:sequence select="position()" xxbl:scope="outer"/>
        </xxforms:variable>
        <xxforms:variable name="index" select="{ fr:significantPositionInRow(../../..) }" xxbl:scope="inner"/>
        <xxforms:variable name="columnSet" select="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        <xxforms:variable name="columnIndex" select="$columnSet/@index + $position - 1" xxbl:scope="inner"/>
        <xxforms:variable name="column" select="$columnSet/column[@index = $columnIndex]" xxbl:scope="inner"/>
        <xxforms:variable name="fr-dt-column">
            <xxforms:sequence select="$column" xxbl:scope="inner"/>
        </xxforms:variable>
        <xhtml:th
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {if (@fr:resizeable = 'true') then 'yui-dt-resizeable' else ''} 
            {{if ($fr-dt-column/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-column/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xforms:group ref=".">
                <xforms:action ev:event="xforms-enabled" xxbl:scope="inner">
                    <!--<xforms:delete nodeset="$columnSet/column[@position = $position]"/>-->
                    <xsl:choose>
                        <xsl:when test="$isInternallySortedAndPaginated">
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
                            "/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xforms:insert context="$columnSet" nodeset="column"
                                origin="xxforms:element('column', (
                                xxforms:attribute('position', $position),
                                xxforms:attribute('nbColumns', 1),
                                xxforms:attribute('index', $columnIndex),
                                $columnSet/@fr:sortable,
                                $columnSet/@fr:resizeable,
                                $columnSet/@fr:sortType
                                ))"
                                if="not($columnSet/column[@position = $position])
                                "/>
                        </xsl:otherwise>
                    </xsl:choose>

                </xforms:action>
            </xforms:group>

            <xxforms:variable name="columnDesc" select="$columnSet/column[@position = $position]" xxbl:scope="inner"/>

            <xsl:call-template name="header-cell"/>

        </xhtml:th>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody" mode="dynamic">
        <xhtml:tbody class="yui-dt-data {@class}" id="{$id}-tbody">
            <xsl:apply-templates select="@*[not(name() = ('class', 'id'))]|node()" mode="dynamic"/>
        </xhtml:tbody>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody/xforms:repeat" mode="dynamic">

        <xxforms:variable name="fr-dt-nodeset" xxbl:scope="outer" select="{$repeatNodeset}"/>

        <xsl:choose>

            <xsl:when test="$isExternallySortedAndPaginated">
                <xxforms:variable name="fr-dt-rewrittenNodeset" select="$fr-dt-nodeset"/>
            </xsl:when>

            <xsl:otherwise>
                <xxforms:variable name="fr-dt-datatable-instance" xxbl:scope="outer">
                    <xxforms:sequence select="instance('datatable-instance')" xxbl:scope="inner"/>
                </xxforms:variable>
                <xxforms:variable name="currentSortColumnIndex" select="instance('datatable-instance')/@currentSortColumn" xxbl:scope="inner"/>

                <xxforms:variable name="fr-dt-currentSortColumn" xxbl:scope="outer">
                    <xxforms:sequence select="(instance('datatable-instance')//column)[@index=$currentSortColumnIndex]" xxbl:scope="inner"/>
                </xxforms:variable>

                <xxforms:variable name="fr-dt-isDefault" xxbl:scope="outer">
                    <xxforms:sequence select="instance('datatable-instance')/@default = 'true'" xxbl:scope="inner"/>
                </xxforms:variable>

                <xxforms:variable name="fr-dt-isSorted" select="$fr-dt-isDefault or $fr-dt-currentSortColumn[@currentSortOrder = @fr:sorted]"
                    xxbl:scope="outer"/>

                <xxforms:variable name="fr-dt-currentSortColumnType" xxbl:scope="outer"
                    select="
            
            if ($fr-dt-currentSortColumn)
                then if ($fr-dt-currentSortColumn/@type != '')
                    then $fr-dt-currentSortColumn/@type
                    else for $value in xxforms:evaluate($fr-dt-currentSortColumn/@pathToFirstNode)
                        return if ($value instance of node())
                            then if (xxforms:type($value) = ({$numberTypesEnumeration}))
                                then 'number'
                                else 'text'
                            else if ($value instance of xs:decimal)
                                then 'number'
                                else 'text'
                else ''
            
            "/>


                <xsl:if test="$paginated">
                    <xxforms:variable name="fr-dt-page" xxbl:scope="outer">
                        <xxforms:sequence select="$page" xxbl:scope="inner"/>
                    </xxforms:variable>
                </xsl:if>

                <xxforms:variable name="fr-dt-rewrittenNodeset"
                    select="
                
                {if ($paginated) then '(' else ''}
                
                if (not($fr-dt-currentSortColumn) or $fr-dt-currentSortColumn/@currentSortOrder = 'none' or $fr-dt-isSorted) 
                    then $fr-dt-nodeset
                    else exf:sort($fr-dt-nodeset,  $fr-dt-currentSortColumn/@sortKey , $fr-dt-currentSortColumnType, $fr-dt-currentSortColumn/@currentSortOrder)
                
                {if ($paginated) 
                    then concat(
                        ')[position() >= ($fr-dt-page - 1) * '
                        , $rowsPerPage 
                        , ' + 1 and position() &lt;= $fr-dt-page *'
                        , $rowsPerPage
                        ,']') 
                    else ''}
                
                "
                />
            </xsl:otherwise>
        </xsl:choose>


        <xforms:repeat nodeset="$fr-dt-rewrittenNodeset">
            <xsl:apply-templates select="@*[not(name()='nodeset')]" mode="dynamic"/>

            <xforms:action ev:event="xxforms-nodeset-changed">
                <xxforms:script> ORBEON.widgets.datatable.update(this); </xxforms:script>
                <xsl:if test="$paginated and not($sortAndPaginationMode='external')">
                    <!-- Workaround, see https://forge.ow2.org/tracker/index.php?func=detail&aid=314429&group_id=168&atid=350207 -->
                    <xforms:setvalue model="datatable-model" ref="instance('page')/@nbPages" value="$nbPages" xxbl:scope="inner"/>
                    <!-- <xforms:setvalue model="datatable-model" ref="instance('page')"
                                         value="if (. cast as xs:integer > $nbPages) then $nbPages else ."/>-->
                </xsl:if>
            </xforms:action>

            <!-- 
                Send  fr-selection-changed events when needed: 
                    - xforms-enabled is triggered at init time
                    - xxforms-index-changed is fired when the index is changed (by the user or using xforms:setindex)
                    - xxforms-nodeset-changed is fired when the nodeset changed (happens also when the user changes the sort order or page)
            -->
            <xforms:action ev:event="xforms-enabled xxforms-index-changed xxforms-nodeset-changed" ev:target="#observer">

                <xxforms:variable name="context" xxbl:scope="inner">
                    <xxforms:sequence select="xxforms:repeat-nodeset()[xxforms:index()]" xxbl:scope="outer"/>
                </xxforms:variable>
                <xxforms:variable name="index" xxbl:scope="inner">
                    <xxforms:sequence select="xxforms:index()" xxbl:scope="outer"/>
                </xxforms:variable>

                <xforms:dispatch name="fr-selection-changed" target="fr.datatable" xxbl:scope="inner">
                    <xxforms:context name="index" select="$index"/>
                    <xxforms:context name="selected" select="$context"/>
                </xforms:dispatch>

            </xforms:action>

            <xsl:apply-templates select="node()" mode="dynamic"/>
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
            style="height: auto;" xxbl:scope="outer">
            <xsl:apply-templates select="@*[name() != 'class']|node()" mode="dynamic"/>
        </xhtml:tr>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td" mode="dynamic">
        <xxforms:variable name="index" select="{count(preceding-sibling::*) + 1}" xxbl:scope="inner"/>
        <xxforms:variable name="fr-dt-columnDesc" model="datatable-model" xxbl:scope="outer">
            <xxforms:sequence select="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        </xxforms:variable>

        <xhtml:td
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {{if ($fr-dt-columnDesc/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-columnDesc/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}            
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xhtml:div class="yui-dt-liner datatable-cell-content">
                <xsl:apply-templates select="node()" mode="dynamic"/>
            </xhtml:div>
        </xhtml:td>
    </xsl:template>

    <xsl:template match="/*/xhtml:tbody/xforms:repeat/xhtml:tr/xforms:repeat/xhtml:td" mode="dynamic">
        <xxforms:variable name="position" select="position()" xxbl:scope="inner">
            <xxforms:sequence select="position()" xxbl:scope="outer"/>
        </xxforms:variable>
        <xxforms:variable name="index" select="{count(../preceding-sibling::*) + 1}" xxbl:scope="inner"/>
        <xxforms:variable name="columnSet" model="datatable-model" select="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        <xxforms:variable name="columnIndex" model="datatable-model" select="$columnSet/@index + $position - 1" xxbl:scope="inner"/>
        <xxforms:variable name="fr-dt-column" model="datatable-model" xxbl:scope="outer">
            <xxforms:sequence select="$columnSet/column[@index = $columnIndex]" xxbl:scope="inner"/>
        </xxforms:variable>
        <xhtml:td
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {{if ($fr-dt-column/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-column/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}            
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xhtml:div class="yui-dt-liner datatable-cell-content">
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
        <xsl:message terminate="yes">Unxepected element (<xsl:value-of select="name()"/> found in a datatable header (expecting either xhtml:th or
            xforms:repeat).</xsl:message>
    </xsl:template>

    <xsl:template match="/*/xhtml:thead/xhtml:tr/*[&neutralElementsInHeaderOrBody;]" mode="dyn-columns" priority="1">
        <xsl:call-template name="identity"/>
    </xsl:template>

    <xsl:template match="/*/xhtml:thead/xhtml:tr/xhtml:th" mode="dyn-columns" priority="1">
        <xsl:variable name="position" select="fr:significantPositionInRow(.)"/>
        <xsl:variable name="body" select="fr:significantElementInRowAtPosition(/*/xhtml:tbody/xforms:repeat/xhtml:tr, $position)"/>
        <xsl:if test="not($body/self::xhtml:td)">
            <xsl:message terminate="yes">Datatable: mismatch, significant element position <xsl:value-of select="$position"/> is a <xsl:value-of
                    select="name()"/> in the header and a <xsl:value-of select="name($body)"/> in the body.</xsl:message>repeat </xsl:if>
        <column xmlns="">
            <xsl:if test="$isInternallySortedAndPaginated">
                <xsl:attribute name="type"/>
                <xsl:attribute name="sortKey">
                    <xsl:choose>
                        <xsl:when test="@fr:sortKey">
                            <xsl:value-of select="@fr:sortKey"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:apply-templates select="$body" mode="dyn-sortKey"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:attribute>
            </xsl:if>
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
        <xsl:variable name="position" select="fr:significantPositionInRow(.)"/>
        <xsl:variable name="body" select="fr:significantElementInRowAtPosition(/*/xhtml:tbody/xforms:repeat/xhtml:tr, $position)"/>
        <xsl:if test="not($body/self::xforms:repeat)">
            <xsl:message terminate="yes">Datatable: mismatch, significant element position <xsl:value-of select="$position"/> is a <xsl:value-of
                    select="name()"/> in the header and a <xsl:value-of select="name($body)"/> in the body.</xsl:message>
        </xsl:if>
        <columnSet xmlns="">
            <xsl:if test="$isInternallySortedAndPaginated">
                <xsl:attribute name="sortKey">
                    <xsl:apply-templates select="$body" mode="dyn-sortKey"/>
                </xsl:attribute>
            </xsl:if>
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
            <xhtml:th class="fr-datatable-columnset-loading-indicator">&#160;...&#160;</xhtml:th>
        </header>
    </xsl:variable>

    <xsl:template match="columnSet" mode="dyn-loadingIndicator">
        <xsl:apply-templates select="$fakeColumn/header/xhtml:th"/>
    </xsl:template>



</xsl:transform>
