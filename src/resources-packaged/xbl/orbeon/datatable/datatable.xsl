<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<!DOCTYPE xsl:transform [
<!ENTITY neutralElementsInHeaderOrBody "self::xxf:variable|self::xf:var|self::xf:action|self::xf:setvalue|self::xf:insert|self::xf:delete|self::xf:setindex|self::xf:toggle|self::xf:setfocus|self::xf:dispatch|self::xf:rebuild|self::xf:recalculate|self::xf:revalidate|self::xf:refresh|self::xf:reset|self::xf:load|self::xf:send">
]>
<xsl:transform xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms" xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:xxbl="http://orbeon.org/oxf/xml/xbl" xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:exf="http://www.exforms.org/exf/1-0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">

    <!-- These optional attributes are used as parameters and are not copied on the generate xh:table -->
    <xsl:variable name="parameters">
        <parameter>appearance</parameter>
        <parameter>scrollable</parameter>
        <parameter>width</parameter>
        <parameter>height</parameter>
        <parameter>paginated</parameter>
        <parameter>rowsPerPage</parameter>
        <parameter>sortAndPaginationMode</parameter>
        <parameter>sort-mode</parameter>
        <parameter>pagination-mode</parameter>
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

    <xsl:variable name="pass5">
        <xsl:apply-templates select="$pass4" mode="pass5"/>
    </xsl:variable>

    <!-- Set some variables that will dictate the geometry of the widget -->
    <xsl:variable name="scrollH" select="$pass5/fr:datatable/@scrollable = ('horizontal', 'both') and $pass5/fr:datatable/@width"/>
    <xsl:variable name="scrollV" select="$pass5/fr:datatable/@scrollable = ('vertical', 'both') and $pass5/fr:datatable/@height"/>
    <xsl:variable name="scrollable" select="$scrollH or $scrollV"/>
    <xsl:variable name="height" select="if ($scrollV) then concat('height: ', $pass5/fr:datatable/@height, ';') else ''"/>
    <xsl:variable name="width" select="if ($pass5/fr:datatable/@width) then concat('width: ', $pass5/fr:datatable/@width, ';') else ''"/>
    <xsl:variable name="id">
        <xsl:choose>
            <xsl:when test="$pass5/fr:datatable/@id">
                <id xxbl:scope="outer">
                    <xsl:value-of select="$pass5/fr:datatable/@id"/>
                </id>
            </xsl:when>
            <xsl:otherwise>
                <id xxbl:scope="inner">
                    <xsl:value-of select="generate-id($pass5/fr:datatable)"/>
                </id>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:variable>
    <xsl:variable name="paginated" select="$pass5/fr:datatable/@paginated = 'true'"/>
    <xsl:variable name="rowsPerPage"
        select="if ($pass5/fr:datatable/@rowsPerPage castable as xs:integer) then $pass5/fr:datatable/@rowsPerPage cast as xs:integer else 10"/>
    <xsl:variable name="maxNbPagesToDisplay"
        select="if ($pass5/fr:datatable/@maxNbPagesToDisplay castable as xs:integer)
            then if ($pass5/fr:datatable/@maxNbPagesToDisplay cast as xs:integer mod 2 = 0 )
                then $pass5/fr:datatable/@maxNbPagesToDisplay cast as xs:integer + 1
                else $pass5/fr:datatable/@maxNbPagesToDisplay cast as xs:integer
            else -1"/>

    <!-- Variable dealing with sorting and pagination mode -->
    <xsl:variable name="is-externally-sorted-and-paginated" as="xs:boolean" select="$pass5/fr:datatable/@sortAndPaginationMode = 'external'"/>
    <xsl:variable name="sort-mode" select="$pass5/fr:datatable/@sort-mode"/>
    <xsl:variable name="is-externally-sorted" as="xs:boolean" select="$sort-mode = 'external' or $is-externally-sorted-and-paginated"/>
    <xsl:variable name="is-internally-sorted" as="xs:boolean" select="not($is-externally-sorted)"/>
    <xsl:variable name="pagination-mode" select="$pass5/fr:datatable/@pagination-mode"/>
    <xsl:variable name="is-externally-paginated" as="xs:boolean" select="$pagination-mode = 'external' or $is-externally-sorted-and-paginated"/>
    <xsl:variable name="is-internally-paginated" as="xs:boolean" select="not($is-externally-paginated)"/>

    <xsl:variable name="innerTableWidth" select="$pass5/fr:datatable/@innerTableWidth"/>
    <xsl:variable name="innerTableWidthJS" select="if ($innerTableWidth) then concat(&quot;'&quot;, $innerTableWidth, &quot;'&quot;) else 'null'"/>
    <xsl:variable name="innerTableWidthCSS" select="if ($innerTableWidth) then concat('width: ', $innerTableWidth, ';') else ''"/>
    <xsl:variable name="hasLoadingFeature" select="count($pass5/fr:datatable/@loading) = 1"/>
    <xsl:variable name="is-modal" select="$pass5/fr:datatable/@modal = 'true'"/>

    <xsl:variable name="debug" select="$pass5/fr:datatable/@debug = 'true'"/>

    <!-- And some more -->

    <xsl:variable name="repeatNodeset" select="$pass5/fr:datatable/xh:tbody/xf:repeat/(@ref, @nodeset)[1]"/>

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

        <xsl:if test="$pass5/fr:datatable/@sortAndPaginationMode and ($pass5/fr:datatable/@sort-mode or $pass5/fr:datatable/@pagination-mode)">
            <xsl:message terminate="yes">You can't use the sortAndPaginationMode attribute, if you have a sort-mode or pagination-mode attribute</xsl:message>
        </xsl:if>
        <xsl:apply-templates select="$pass5/fr:datatable" mode="dynamic"/>

    </xsl:template>

    <xsl:template name="fr-goto-page">
        <xsl:param name="fr-new-page"/>
        <xf:action ev:event="DOMActivate">
            <!-- Always dispatch the fr-goto-page; with external pagination, it used by the user of the datatable to load
                 the corresponding data, but it can also be useful with internal paging -->
            <xf:dispatch name="fr-goto-page" targetid="fr.datatable">
                <xf:property name="fr-new-page" value="{$fr-new-page}"/>
            </xf:dispatch>
        </xf:action>
    </xsl:template>

    <!--

    Pass 1 : create a body element if missing

    Note (common to pass 1, 2, 3, 4): replace xsl:copy-of by xsl:apply-templates if needed!

    -->

    <xsl:template match="/fr:datatable" mode="pass1">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="xh:colgroup|xh:thead|xh:tbody"/>
            <xsl:if test="not(xh:tbody)">
                <xh:tbody>
                    <xsl:copy-of select="*[not(self::tcolgroup|self::thead)]"/>
                </xh:tbody>
            </xsl:if>
        </xsl:copy>
    </xsl:template>

    <!--

        Pass 2 : expand /fr:datatable/xh:tbody/xh:tr[@repeat-ref | @repeat-nodeset]

    -->

    <xsl:template match="/fr:datatable/xh:tbody/xh:tr[@repeat-ref | @repeat-nodeset]" mode="pass2">
        <xf:repeat ref="{(@repeat-ref, @repeat-nodeset)[1]}">
            <xsl:copy-of select="@id"/>
            <xsl:copy>
                <xsl:copy-of select="@*[not(name() = ('repeat-ref', 'repeat-nodeset'))]|node()"/>
            </xsl:copy>
        </xf:repeat>
    </xsl:template>

    <!--

        Pass 3 : expand /fr:datatable/xh:tbody/xf:repeat/xh:tr/td[@repeat-ref | @repeat-nodeset]
        and /fr:datatable/xh:thead/xh:tr/th[@repeat-ref | @repeat-nodeset]

        Note: do not merge with pass 2 unless you update these XPath expressions to work with
        xh:tr[@repeat-ref | @repeat-nodeset]

    -->

    <xsl:template match="/fr:datatable/xh:tbody/xf:repeat/xh:tr/td[@repeat-ref | @repeat-nodeset]|/fr:datatable/xh:thead/xh:tr/th[@repeat-ref | @repeat-nodeset]"
        mode="pass3">
        <xf:repeat ref="{(@repeat-ref, @repeat-nodeset)[1]}">
            <xsl:copy>
                <xsl:copy-of select="@*[name() != 'repeat-nodeset']|node()"/>
            </xsl:copy>
        </xf:repeat>
    </xsl:template>

    <!--

        Pass 4 : create a header element if missing

    -->

    <xsl:template match="/fr:datatable" mode="pass4">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="xh:colgroup|xh:thead"/>
            <xsl:if test="not(xh:thead)">
                <xh:thead>
                    <xh:tr>
                        <xsl:apply-templates select="/fr:datatable/xh:tbody/xf:repeat/xh:tr/*" mode="pass4-header"/>
                    </xh:tr>
                </xh:thead>
            </xsl:if>
            <xsl:apply-templates select="xh:tbody" mode="pass4"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template
        match="/fr:datatable/xh:tbody/xf:repeat/xh:tr/xh:td/xf:output[xf:label][1]/xf:label
        |/fr:datatable/xh:tbody/xf:repeat/xh:tr/xf:repeat/xh:td/xf:output[xf:label][1]/xf:label"
        mode="pass4"/>

    <!--

        Pass 4-header : populate the a header element if missing
        (called by pass4)

    -->

    <xsl:template match="*" mode="pass4-header"/>

    <xsl:template match="/fr:datatable/xh:tbody/xf:repeat/xh:tr/xf:repeat" mode="pass4-header">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="pass4-header"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template
        match="/fr:datatable/xh:tbody/xf:repeat/xh:tr/xh:td|/fr:datatable/xh:tbody/xf:repeat/xh:tr/xf:repeat/xh:td"
        mode="pass4-header">
        <xh:th>
            <xsl:apply-templates select="@*" mode="pass4-header"/>
            <xsl:apply-templates select="xf:output[xf:label][1]" mode="pass4-header"/>
        </xh:th>
    </xsl:template>

    <xsl:template
        match="/fr:datatable/xh:tbody/xf:repeat/xh:tr/xh:td/xf:output[@ref]|/fr:datatable/xh:tbody/xf:repeat/xh:tr/xf:repeat/xh:td/xf:output[@ref]"
        mode="pass4-header">
        <xf:group>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="xf:label/*"/>
        </xf:group>
    </xsl:template>

    <xsl:template
        match="/fr:datatable/xh:tbody/xf:repeat/xh:tr/xh:td/xf:output|/fr:datatable/xh:tbody/xf:repeat/xh:tr/xf:repeat/xh:td/xf:output"
        mode="pass4-header">
        <xsl:value-of select="xf:label"/>
    </xsl:template>

    <!--

        Pass 5: add a @fr:master="true" attribute to the last header row if there is none elsewhere

    -->

    <xsl:template match="/fr:datatable/xh:thead[not(xh:tr/@fr:master='true')]/xh:tr[last()]" mode="pass5">
        <xsl:copy>
            <xsl:attribute name="fr:master">true</xsl:attribute>
            <xsl:apply-templates select="@*|node()" mode="pass5"/>
        </xsl:copy>
    </xsl:template>


    <!-- Now do the "real" work! -->

    <xsl:template match="/fr:datatable" mode="dynamic">
        <!-- Matches the bound element -->

        <xsl:if test="not(xh:thead)">
            <xsl:message terminate="yes">Datatable components should include a thead element.</xsl:message>
        </xsl:if>
        <xsl:if test="not(xh:tbody)">
            <xsl:message terminate="yes">Datatable components should include a tbody element.</xsl:message>
        </xsl:if>

        <xsl:variable name="columns">
            <xsl:apply-templates select="xh:thead/xh:tr[@fr:master = 'true']/*" mode="dyn-columns"/>
        </xsl:variable>

        <xf:group xbl:attr="model context ref bind" xxbl:scope="outer" id="{$id}-container">
            <xsl:copy-of select="namespace::*"/>

            <xf:model id="datatable-model" xxbl:scope="inner">
                <xf:instance id="datatable-instance">
                    <columns xmlns="">
                        <xsl:if test="$is-internally-sorted">
                            <xsl:attribute name="currentSortColumn">-1</xsl:attribute>
                            <xsl:attribute name="default">true</xsl:attribute>
                        </xsl:if>
                        <xsl:for-each select="$columns/*[self::column|self::columnSet]">
                            <xsl:copy>
                                <xsl:attribute name="nbColumns"/>
                                <xsl:attribute name="index"/>
                                <xsl:if test="$is-internally-sorted">
                                    <xsl:attribute name="currentSortOrder"/>
                                    <xsl:attribute name="nextSortOrder"/>
                                    <xsl:attribute name="type"/>
                                    <xsl:attribute name="pathToFirstNode"/>
                                </xsl:if>
                                <xsl:copy-of select="@*"/>
                            </xsl:copy>
                        </xsl:for-each>
                    </columns>
                </xf:instance>
                <xf:bind ref="column/@nbColumns" calculate="1"/>
                <xf:bind ref="columnSet/@nbColumns" calculate="count(../column)"/>
                <xf:bind ref="//@index" calculate="count(../preceding::column) + 1"/>
                <xf:bind ref="//column/@currentSortOrder"
                    calculate="if (/*/@default='true' and ../@fr:sorted) then ../@fr:sorted else if (../@index = /*/@currentSortColumn) then . else 'none'"/>
                <xf:bind ref="//column/@nextSortOrder" calculate="if (../@currentSortOrder = 'ascending') then 'descending' else 'ascending'"/>
                <xf:var name="repeatNodeset">
                    <xsl:value-of select="$repeatNodeset"/>
                </xf:var>
                <xf:bind ref="//column/@pathToFirstNode"
                    calculate="concat('xxf:component-context()/(', $repeatNodeset, ')[1]/(', ../@sortKey, ')')"/>
                <xf:bind ref="//column[@fr:sortType]/@type" calculate="../@fr:sortType"/>
                <!--<xf:bind ref="//column[not(@fr:sortType)]/@type"
                    calculate="for $value in xxf:evaluate(../@pathToFirstNode)
                        return if ($value instance of node())
                        then if (xxf:type($value) = ({$numberTypesEnumeration}))
                            then 'number'
                            else 'text'
                        else if ($value instance of xs:decimal)
                            then 'number'
                            else 'text'"/>
-->

                <xsl:if test="$paginated">
                    <xf:instance id="page">
                        <page nbPages="" xmlns="">1</page>
                    </xf:instance>


                    <!--
                       Uncomment when https://forge.ow2.org/tracker/index.php?func=detail&aid=314437&group_id=168&atid=350207 will be fixed
                       <xsl:if test="not($sortAndPaginationMode='external')">
                        <xf:var name="nbRows">
                            <xxf:sequence value="count({$repeatNodeset})" xxbl:scope="outer"/>
                        </xf:var>
                        <xf:var name="nbPages"
                            value="ceiling($nbRows div {$rowsPerPage}) cast as xs:integer"/>
                        <xf:bind ref="instance('page')">
                            <xf:bind ref="@nbPages" calculate="$nbPages"/>
                            <!-\-<xf:bind ref="."
                               calculate="if (. cast as xs:integer > $nbPages) then $nbPages else ."
                               />-\->
                        </xf:bind>
                    </xsl:if>-->

                </xsl:if>

            </xf:model>

            <xsl:choose>
                <xsl:when test="$paginated and $is-internally-paginated">
                    <xf:var name="page" model="datatable-model" value="instance('page')" xxbl:scope="inner"/>
                    <xf:var name="nbRows" xxbl:scope="inner">
                        <xxf:sequence value="count({$repeatNodeset})" xxbl:scope="outer"/>
                    </xf:var>
                    <!-- Workaround for a limitation where an expression refering to non relevant contexts returns an empty sequence -->
                    <xf:var name="nbPages" value="if ($nbRows) then ceiling($nbRows div {$rowsPerPage}) cast as xs:integer else 0"
                        xxbl:scope="inner"/>
                </xsl:when>

                <xsl:when test="$paginated and $is-externally-paginated">
                    <xf:var name="page" xxbl:scope="inner">
                        <xxf:sequence xbl:attr="value=page" xxbl:scope="outer"/>
                    </xf:var>
                    <xf:var name="nbPages" xxbl:scope="inner">
                        <xxf:sequence xbl:attr="value=nbPages" xxbl:scope="outer"/>
                    </xf:var>
                </xsl:when>

            </xsl:choose>


            <xsl:choose>
                <xsl:when test="$paginated and $maxNbPagesToDisplay &lt; 0">
                    <xf:var name="pages" value="1 to xs:integer($nbPages)" xxbl:scope="inner"/>
                </xsl:when>
                <xsl:when test="$paginated">
                    <xf:var name="maxNbPagesToDisplay" value="{$maxNbPagesToDisplay} cast as xs:integer" xxbl:scope="inner"/>
                    <xf:var name="radix" value="floor(($maxNbPagesToDisplay - 2) div 2) cast as xs:integer" xxbl:scope="inner"/>
                    <xf:var name="minPage"
                        value="
                        (if ($page > $radix)
                        then if ($nbPages >= $page + $radix)
                        then ($page - $radix)
                        else max((1, $nbPages - $maxNbPagesToDisplay + 1))
                        else 1) cast as xs:integer"
                        xxbl:scope="inner"/>
                    <xf:var name="pages"
                        value="if ($nbPages castable as xs:integer)
                                then 1 to xs:integer($nbPages)
                                else ()"
                        xxbl:scope="inner"/>
                </xsl:when>
            </xsl:choose>

            <xf:var name="currentSortOrder" model="datatable-model" value="instance('datatable-instance')/@currentSortOrder"
                xxbl:scope="inner"/>
            <xf:var name="currentSortColumn" model="datatable-model" value="instance('datatable-instance')/@currentSortColumn"
                xxbl:scope="inner"/>

            <xsl:if test="$debug">
                <xh:div style="border:thin solid black" class="fr-dt-debug fr-dt-debug-{id}">
                    <xh:h3>Local instance:</xh:h3>
                    <xf:group model="datatable-model" instance="datatable-instance" xxbl:scope="inner">
                        <xh:div class="fr-dt-debug-columns" id="debug-columns">
                            <xh:p>
                                <xf:output value="name()"/>
                            </xh:p>
                            <xh:ul>
                                <xf:repeat ref="@*">
                                    <xh:li>
                                        <xf:output ref=".">
                                            <xf:label>
                                                <xf:output value="concat(name(), ': ')"/>
                                            </xf:label>
                                        </xf:output>
                                    </xh:li>
                                </xf:repeat>
                            </xh:ul>
                        </xh:div>
                        <xf:repeat ref="*|//column">
                            <xh:div id="debug-column">
                                <xh:p>
                                    <xf:output value="name()"/>
                                </xh:p>
                                <xh:ul>
                                    <xf:repeat ref="@*">
                                        <xh:li>
                                            <xf:output ref=".">
                                                <xf:label>
                                                    <xf:output value="concat(name(), ': ')"/>
                                                </xf:label>
                                            </xf:output>
                                        </xh:li>
                                    </xf:repeat>
                                </xh:ul>
                            </xh:div>
                        </xf:repeat>
                    </xf:group>
                </xh:div>
            </xsl:if>

            <xsl:if test="$hasLoadingFeature">
                <xf:var name="fr-dt-loading" xbl:attr="value=loading"/>
            </xsl:if>
            <xsl:call-template name="pagination">
                <xsl:with-param name="prefix">top</xsl:with-param>
            </xsl:call-template>

            <xf:var name="group-ref" xxbl:scope="inner">
                <xxf:sequence value=".{if ($hasLoadingFeature) then '[not($fr-dt-loading = true())]' else ''}" xxbl:scope="outer"/>
            </xf:var>

            <xf:group ref="$group-ref" id="fr-dt-group" xxbl:scope="inner">

                <xxf:script ev:event="xforms-enabled" ev:target="#observer">
                    YAHOO.log("Enabling datatable id <xsl:value-of select="$id"/>","info");
                    <xsl:choose>
                        <xsl:when test="$scrollH or $scrollV">
                            var target = this;
                            _.defer(function() { YAHOO.xbl.fr.Datatable.instance(target).init(); });
                        </xsl:when>
                        <xsl:otherwise>YAHOO.xbl.fr.Datatable.instance(this).init();</xsl:otherwise>
                    </xsl:choose>
                </xxf:script>

                <xh:span class="fr-dt-inner-table-width" style="display: none">
                    <xsl:value-of select="$innerTableWidth"/>
                </xh:span>

                <xf:group appearance="xxf:internal" xxbl:scope="outer">
                    <xh:div class="yui-dt yui-dt-scrollable {if ($scrollV) then 'fr-scrollV' else ''}  {if ($scrollH) then 'fr-scrollH' else ''}"
                        style="{$height} {$width}">
                        <xsl:variable name="table">
                            <xh:table id="{$id}-table" class="{@class} datatable datatable-{$id} yui-dt-table "
                                style="{if ($scrollV) then $height else ''} {if ($scrollH) then $innerTableWidthCSS else $width}">
                                <!-- Copy attributes that are not parameters! -->
                                <xsl:apply-templates select="@*[not(name() = ($parameters/*, 'id', 'class'))]" mode="dynamic"/>
                                <xh:thead id="{$id}-thead">
                                    <xsl:apply-templates select="xh:thead/xh:tr" mode="dynamic">
                                        <xsl:with-param name="columns" select="$columns"/>
                                    </xsl:apply-templates>
                                </xh:thead>
                                <xsl:apply-templates select="xh:tbody" mode="dynamic"/>
                            </xh:table>
                        </xsl:variable>
                        <xsl:choose>
                            <xsl:when test="$scrollH or $scrollV">
                                <!-- The table needs to be split -->
                                <xh:div class="yui-dt-hd fr-datatable-hidden" style="{$width}">
                                    <xsl:variable name="tHead">
                                        <xsl:apply-templates select="$table" mode="headerIsolation"/>
                                    </xsl:variable>
                                    <xsl:choose>
                                        <xsl:when test="$scrollV">
                                            <!-- Add an intermediary div to the header to compensate the scroll bar width when needed -->
                                            <xh:div>
                                                <xsl:apply-templates select="$tHead" mode="addListener"/>
                                            </xh:div>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <!-- Add the header without an intermediary div -->
                                            <xsl:apply-templates select="$tHead" mode="addListener"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xh:div>
                                <xh:div class="yui-dt-hd" style="overflow: auto; {$width} {$height}">
                                    <xsl:apply-templates select="$table" mode="bodyFiltering"/>
                                </xh:div>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- The table can be left alone -->
                                <xh:div class="yui-dt-hd">
                                    <xsl:apply-templates select="$table" mode="addListener"/>
                                </xh:div>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xh:div>
                </xf:group>

            </xf:group>

            <xsl:if test="$hasLoadingFeature">
                <!-- The trick with the spans is working fine for simple case where we don't need to specify the height or width.
                    In other cases, the elements "gain layout" in IE world and the width of the div that contains the
                    scrollbar takes all the page in IE 6 if not explicitly set...-->
                <xf:group ref="xxf:component-context()[$fr-dt-loading = true()]">
                    <xf:action ev:event="xforms-enabled">
                        <xxf:script> YAHOO.xbl.fr.Datatable.instance(this).initLoadingIndicator(this, <xsl:value-of select="$scrollV"/>,
                                <xsl:value-of select="$scrollH"/>); </xxf:script>
                    </xf:action>
                    <xsl:variable name="tableContent">
                        <xh:thead>
                            <xh:tr class="yui-dt-first yui-dt-last">
                                <xsl:apply-templates select="$columns/*" mode="dyn-loadingIndicator"/>
                            </xh:tr>
                        </xh:thead>
                        <xh:tbody>
                            <xh:tr>
                                <xh:td colspan="{count($columns/*)}">
                                    <xh:div class="fr-datatable-is-loading" style="{if ($scrollable) then concat( $height, ' ', $width) else ''}"/>
                                </xh:td>
                            </xh:tr>
                        </xh:tbody>
                    </xsl:variable>
                    <xsl:choose>
                        <xsl:when test="$scrollable">
                            <xh:div class="yui-dt yui-dt-scrollable" style="{if ($scrollV) then $height else 'height: 95px;'} {$width}">
                                <xh:div style="overflow: auto; {if ($scrollV) then $height else 'height: 95px;'} {$width}" class="yui-dt-hd">
                                    <xh:table style="" class="datatable datatable-table-scrollV yui-dt-table fr-scrollV">
                                        <xsl:copy-of select="$tableContent"/>
                                    </xh:table>
                                </xh:div>
                            </xh:div>
                        </xsl:when>
                        <xsl:otherwise>
                            <xh:span class="yui-dt yui-dt-scrollable" style="display: table; ">
                                <xh:span class="yui-dt-hd" style="border: 1px solid rgb(127, 127, 127); display: table-cell;">
                                    <xh:table class="datatable  yui-dt-table" style="{$height} {$width}">
                                        <xsl:copy-of select="$tableContent"/>
                                    </xh:table>
                                </xh:span>
                            </xh:span>
                        </xsl:otherwise>
                    </xsl:choose>
                </xf:group>
            </xsl:if>

            <xsl:call-template name="pagination">
                <xsl:with-param name="prefix">bottom</xsl:with-param>
            </xsl:call-template>

            <xsl:if test="$paginated and $is-internally-paginated">
                <xf:group model="datatable-model" ref="instance('page')" appearance="xxf:internal" xxbl:scope="inner">
                    <xf:input ref="@nbPages" style="display:none;">
                        <!-- Workaround, see https://forge.ow2.org/tracker/index.php?func=detail&aid=314429&group_id=168&atid=350207 -->
                        <xf:setvalue ref=".." ev:event="xforms-value-changed"
                            value="if (. cast as xs:integer &gt; @nbPages and @nbPages > 0) then @nbPages else ."/>
                    </xf:input>
                </xf:group>
            </xsl:if>

        </xf:group>
        <!-- End of template on the bound element -->
    </xsl:template>


    <xsl:template match="/fr:datatable/xh:thead/xh:tr[@fr:master='true']" mode="dynamic">
        <xsl:param name="columns"/>
        <xh:tr class="{@class} fr-dt-master-row">
            <xsl:apply-templates
                select="@*[name() != 'class' and (local-name() != 'master' or namespace-uri() != 'http://orbeon.org/oxf/xml/form-runner')]"
                mode="dynamic"/>
            <xsl:apply-templates select="$columns" mode="dynamic"/>
        </xh:tr>
    </xsl:template>

    <xsl:template match="/fr:datatable/xh:thead/xh:tr[not(@fr:master='true')]//xh:th" mode="dynamic">
        <xsl:copy>
            <xsl:apply-templates select="@*" mode="#current"/>
            <xh:div class="yui-dt-liner  datatable-cell-content">
                <xh:span class="yui-dt-label">
                    <xsl:apply-templates select="node()" mode="#current"/>
                </xh:span>
            </xh:div>
        </xsl:copy>
    </xsl:template>

    <xsl:template name="header-cell">
        <xsl:param name="index"/>

        <!-- XXForms variable "columnDesc" is the current column description when we enter here -->

        <!-- <xf:output value="$columnDesc/@index"/>-->

        <xsl:variable name="liner">
            <xh:div class="yui-dt-liner  datatable-cell-content">
                <xh:span class="yui-dt-label">
                    <xsl:choose>
                        <xsl:when test="@fr:sortable = 'true'">
                            <xf:trigger appearance="minimal">
                                <xsl:if test="$is-modal"><xsl:attribute name="xxf:modal">true</xsl:attribute></xsl:if>
                                <xf:label>
                                    <xsl:apply-templates select="node()" mode="dynamic"/>
                                </xf:label>
                                <xsl:choose>
                                    <xsl:when test="$is-externally-sorted">
                                        <xf:hint>
                                            <xf:output value="{@fr:sortMessage}"/>
                                        </xf:hint>
                                        <xf:dispatch ev:event="DOMActivate" name="fr-update-sort" targetid="fr.datatable" xxbl:scope="inner">
                                            <xf:property name="fr-column" value="xs:integer($columnDesc/@index)"/>
                                        </xf:dispatch>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xf:hint xxbl:scope="inner">Click to sort <xf:output value="$columnDesc/@nextSortOrder"
                                            /></xf:hint>
                                        <xf:action ev:event="DOMActivate">
                                            <xf:setvalue ref="$columnDesc/ancestor::columns/@default" xxbl:scope="inner">false</xf:setvalue>
                                            <xf:setvalue ref="$columnDesc/@currentSortOrder" value="$columnDesc/@nextSortOrder" xxbl:scope="inner"/>
                                            <xf:setvalue ref="$currentSortColumn" value="$columnDesc/@index" xxbl:scope="inner"/>
                                        </xf:action>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xf:trigger>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:apply-templates select="node()" mode="dynamic"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xh:span>
            </xh:div>
        </xsl:variable>

        <xsl:choose>
            <xsl:when test="@fr:resizeable = 'true'">
                <xh:div class="yui-dt-resizerliner">
                    <xsl:copy-of select="$liner"/>
                    <xh:div class="yui-dt-resizer"/>
                </xh:div>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy-of select="$liner"/>
            </xsl:otherwise>
        </xsl:choose>


    </xsl:template>

    <xsl:template match="column|columnSet" priority="1" mode="dynamic">
        <xsl:apply-templates select="header" mode="dynamic"/>
    </xsl:template>

    <xsl:template match="header" mode="dynamic">
        <xsl:apply-templates select="*" mode="dynamic"/>
    </xsl:template>

    <xsl:template match="header/xh:th" mode="dynamic">
        <xsl:if test="$is-externally-sorted and @fr:sortable and not(@fr:sortMessage)">
            <xsl:message terminate="yes">In datatables with external sorting, sortable columns must have fr:sortMessage attributes.</xsl:message>
        </xsl:if>
        <xsl:variable name="index" select="count(../../preceding-sibling::*) + 1" xxbl:scope="inner"/>
        <xf:var name="index" value="{$index}" xxbl:scope="inner"/>
        <xf:var name="columnDesc" model="datatable-model" value="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        <xf:var name="fr-dt-columnDesc">
            <xxf:sequence value="$columnDesc" xxbl:scope="inner"/>
        </xf:var>
        <xh:th
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''}
            {if (@fr:resizeable = 'true') then 'yui-dt-resizeable' else ''}
            {{if ($fr-dt-columnDesc/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-columnDesc/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}

             {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xsl:call-template name="header-cell">
                <xsl:with-param name="index" select="$index"/>
            </xsl:call-template>

        </xh:th>
    </xsl:template>

    <xsl:template match="/xh:table/xh:thead/xh:tr/xf:repeat" mode="addListener">
        <xsl:copy>
            <xsl:apply-templates select="@*" mode="dynamic"/>
            <xf:action ev:event="xxforms-nodeset-changed xforms-enabled xforms-disabled" ev:target="#observer">
                <xxf:script> YAHOO.xbl.fr.Datatable.instance(this).updateColumns(); </xxf:script>
            </xf:action>
            <xsl:apply-templates select="node()" mode="dynamic"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/xh:table/xh:thead/xh:tr/xf:group" mode="addListener">
        <xsl:copy>
            <xsl:apply-templates select="@*" mode="dynamic"/>
            <xf:action ev:event="xforms-enabled xforms-disabled">
                <xxf:script> YAHOO.xbl.fr.Datatable.instance(this).updateColumns(); </xxf:script>
            </xf:action>
            <xsl:apply-templates select="node()" mode="dynamic"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="header/xf:repeat/xh:th" mode="dynamic">
        <xsl:if test="$is-externally-sorted and @fr:sortable and not(@fr:sortMessage)">
            <xsl:message terminate="yes">In datatables with external sorting, sortable columns must have fr:sortMessage attributes.</xsl:message>
        </xsl:if>

        <xf:var name="position" xxbl:scope="inner">
            <xxf:sequence value="position()" xxbl:scope="outer"/>
        </xf:var>
        <xf:var name="index" value="{ fr:significantPositionInRow(../../..) }" xxbl:scope="inner"/>
        <xf:var name="columnSet" value="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        <xf:var name="columnIndex" value="$columnSet/@index + $position - 1" xxbl:scope="inner"/>
        <xf:var name="column" value="$columnSet/column[@index = $columnIndex]" xxbl:scope="inner"/>
        <xf:var name="fr-dt-column">
            <xxf:sequence value="$column" xxbl:scope="inner"/>
        </xf:var>
        <xh:th
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''}
            {if (@fr:resizeable = 'true') then 'yui-dt-resizeable' else ''}
            {{if ($fr-dt-column/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-column/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xf:group ref=".">
                <xf:action ev:event="xforms-enabled" xxbl:scope="inner">
                    <xsl:choose>
                        <xsl:when test="$is-internally-sorted">
                            <xf:insert context="$columnSet" ref="column"
                                origin="xxf:element('column', (
                            xxf:attribute('position', $position),
                            xxf:attribute('nbColumns', 1),
                            xxf:attribute('index', $columnIndex),
                            xxf:attribute('sortKey', concat( '(',  $columnSet/(@ref, @nodeset)[1], ')[', $position , ']/', $columnSet/@sortKey)),
                            xxf:attribute('currentSortOrder', ''),
                            xxf:attribute('nextSortOrder', ''),
                            xxf:attribute('type', ''),
                            xxf:attribute('pathToFirstNode', ''),
                            $columnSet/@fr:sortable,
                            $columnSet/@fr:resizeable,
                            $columnSet/@fr:sortType
                            ))"
                                if="not($columnSet/column[@position = $position])
                            "/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xf:insert context="$columnSet" ref="column"
                                origin="xxf:element('column', (
                                xxf:attribute('position', $position),
                                xxf:attribute('nbColumns', 1),
                                xxf:attribute('index', $columnIndex),
                                $columnSet/@fr:sortable,
                                $columnSet/@fr:resizeable,
                                $columnSet/@fr:sortType
                                ))"
                                if="not($columnSet/column[@position = $position])
                                "/>
                        </xsl:otherwise>
                    </xsl:choose>

                </xf:action>
            </xf:group>

            <xf:var name="columnDesc" value="$columnSet/column[@position = $position]" xxbl:scope="inner"/>

            <xsl:call-template name="header-cell"/>

        </xh:th>
    </xsl:template>

    <xsl:template match="header/xf:group/xh:th" mode="dynamic">
        <xsl:if test="$is-externally-sorted and @fr:sortable and not(@fr:sortMessage)">
            <xsl:message terminate="yes">In datatables with external sorting, sortable columns must have fr:sortMessage attributes.</xsl:message>
        </xsl:if>

        <xf:var name="position" xxbl:scope="inner">
            <xxf:sequence value="position()" xxbl:scope="outer"/>
        </xf:var>
        <xf:var name="index" value="{ fr:significantPositionInRow(../../..) }" xxbl:scope="inner"/>
        <xf:var name="columnSet" value="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        <xf:var name="columnIndex" value="$columnSet/@index + $position - 1" xxbl:scope="inner"/>
        <xf:var name="column" value="$columnSet/column[@index = $columnIndex]" xxbl:scope="inner"/>
        <xf:var name="fr-dt-column">
            <xxf:sequence value="$column" xxbl:scope="inner"/>
        </xf:var>
        <xh:th
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''}
            {if (@fr:resizeable = 'true') then 'yui-dt-resizeable' else ''}
            {{if ($fr-dt-column/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-column/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xf:var name="value" value="string()">
                <xf:action ev:event="xforms-enabled xforms-value-changed">
                    <xsl:choose>
                        <xsl:when test="$is-internally-sorted">
                            <xf:insert
                                xxbl:scope="inner"
                                context="$columnSet" ref="column"
                                origin="xxf:element('column', (
                                    xxf:attribute('position', $position),
                                    xxf:attribute('nbColumns', 1),
                                    xxf:attribute('index', $columnIndex),
                                    xxf:attribute('sortKey', concat( '(',  $columnSet/(@ref, @nodeset)[1], ')[', $position , ']/', $columnSet/@sortKey)),
                                    xxf:attribute('currentSortOrder', ''),
                                    xxf:attribute('nextSortOrder', ''),
                                    xxf:attribute('type', ''),
                                    xxf:attribute('pathToFirstNode', ''),
                                    $columnSet/@fr:sortable,
                                    $columnSet/@fr:resizeable,
                                    $columnSet/@fr:sortType
                                    ))"
                                if="not($columnSet/column[@position = $position])
                                "/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xf:insert
                                xxbl:scope="inner"
                                context="$columnSet"
                                ref="column"
                                origin="xxf:element('column', (
                                    xxf:attribute('position', $position),
                                    xxf:attribute('nbColumns', 1),
                                    xxf:attribute('index', $columnIndex),
                                    $columnSet/@fr:sortable,
                                    $columnSet/@fr:resizeable,
                                    $columnSet/@fr:sortType
                                ))"
                                if="not($columnSet/column[@position = $position])
                                "/>
                        </xsl:otherwise>
                    </xsl:choose>

                </xf:action>
            </xf:var>

            <xf:var name="columnDesc" value="$columnSet/column[@position = $position]" xxbl:scope="inner"/>

            <xsl:call-template name="header-cell"/>

        </xh:th>
    </xsl:template>

    <xsl:template match="/*/xh:tbody" mode="dynamic">
        <xh:tbody class="yui-dt-data {@class}" id="{$id}-tbody">
            <xsl:apply-templates select="@*[not(name() = ('class', 'id'))]|node()" mode="dynamic"/>
        </xh:tbody>
    </xsl:template>

    <xsl:template match="/*/xh:tbody/xf:repeat" mode="dynamic">

        <xf:var name="fr-dt-nodeset" xxbl:scope="outer" value="{$repeatNodeset}"/>

        <!-- Handling internal sorting -->
        <xsl:choose>
            <xsl:when test="$is-externally-sorted">
                <xf:var name="fr-dt-rewritten-sorted-nodeset" value="$fr-dt-nodeset"/>
            </xsl:when>
            <xsl:otherwise>

                <xf:var name="fr-dt-datatable-instance" xxbl:scope="outer">
                    <xxf:sequence value="instance('datatable-instance')" xxbl:scope="inner"/>
                </xf:var>
                <xf:var name="currentSortColumnIndex" value="instance('datatable-instance')/@currentSortColumn" xxbl:scope="inner"/>

                <xf:var name="fr-dt-currentSortColumn" xxbl:scope="outer">
                    <xxf:sequence value="(instance('datatable-instance')//column)[@index=$currentSortColumnIndex]" xxbl:scope="inner"/>
                </xf:var>

                <xf:var name="fr-dt-isDefault" xxbl:scope="outer">
                    <xxf:sequence value="instance('datatable-instance')/@default = 'true'" xxbl:scope="inner"/>
                </xf:var>

                <xf:var name="fr-dt-isSorted" value="$fr-dt-isDefault or $fr-dt-currentSortColumn[@currentSortOrder = @fr:sorted]" xxbl:scope="outer"/>

                <xf:var name="fr-dt-currentSortColumnType" xxbl:scope="outer"
                    value="
                        if ($fr-dt-currentSortColumn)
                            then if ($fr-dt-currentSortColumn/@type != '')
                                then $fr-dt-currentSortColumn/@type
                                else for $value in xxf:evaluate($fr-dt-currentSortColumn/@pathToFirstNode)
                                    return if ($value instance of node())
                                        then if (xxf:type($value) = ({$numberTypesEnumeration}))
                                            then 'number'
                                            else 'text'
                                        else if ($value instance of xs:decimal)
                                            then 'number'
                                            else 'text'
                            else ''"/>

                <xf:var name="fr-dt-rewritten-sorted-nodeset"
                    value="
                        if (not($fr-dt-currentSortColumn) or $fr-dt-currentSortColumn/@currentSortOrder = 'none' or $fr-dt-isSorted)
                            then $fr-dt-nodeset
                            else exf:sort($fr-dt-nodeset,  $fr-dt-currentSortColumn/@sortKey , $fr-dt-currentSortColumnType, $fr-dt-currentSortColumn/@currentSortOrder)
                    "/>
            </xsl:otherwise>
        </xsl:choose>

        <!-- Handling internal paging -->
        <xsl:choose>
            <xsl:when test="not($paginated) or $is-externally-paginated">
                <xf:var name="fr-dt-rewritten-paginated-nodeset" value="$fr-dt-rewritten-sorted-nodeset"/>
            </xsl:when>
            <xsl:otherwise>

                <xf:var name="fr-dt-page" xxbl:scope="outer">
                    <xxf:sequence value="$page" xxbl:scope="inner"/>
                </xf:var>

                <xf:var name="fr-dt-rewritten-paginated-nodeset"
                    value="
                        $fr-dt-rewritten-sorted-nodeset
                        {concat(
                                '[position() >= ($fr-dt-page - 1) * '
                                , $rowsPerPage
                                , ' + 1 and position() &lt;= $fr-dt-page *'
                                , $rowsPerPage
                                ,']')
                         }"/>
            </xsl:otherwise>
        </xsl:choose>

        <xf:repeat ref="$fr-dt-rewritten-paginated-nodeset">
            <xsl:apply-templates select="@*[not(name()='nodeset')]" mode="dynamic"/>
            <xsl:apply-templates select="node()" mode="dynamic"/>
        </xf:repeat>

        <!--
            Send fr-selection-changed events when needed:
                - xforms-enabled is triggered when the repeat becomes relevant
                - xforms-disabled is triggered when the repeat becomes non-relevant (including when it has no iterations)
                - xxforms-index-changed is fired when the index is changed (by the user or using xf:setindex)
                - xxforms-nodeset-changed is fired when the nodeset changed (happens also when the user changes the sort order or page)

            We must leave the listener outside so that xforms-disabled works, otherwise nested listeners won't run.
        -->
        <xf:action
                ev:event="xforms-enabled xforms-disabled xxforms-index-changed xxforms-nodeset-changed"
                ev:observer="#preceding-sibling"
                ev:target="#observer">

            <xf:action if="event('xxf:type') = ('xxforms-nodeset-changed', 'xforms-enabled', 'xforms-disabled')">
                <xxf:script>YAHOO.xbl.fr.Datatable.instance(this).updateRows();</xxf:script>
                <xsl:if test="$paginated and $is-internally-paginated">
                    <!-- Workaround, see https://forge.ow2.org/tracker/index.php?func=detail&aid=314429&group_id=168&atid=350207 -->
                    <xf:setvalue model="datatable-model" ref="instance('page')/@nbPages" value="$nbPages" xxbl:scope="inner"/>
                    <!-- <xf:setvalue model="datatable-model" ref="instance('page')"
                                         value="if (. cast as xs:integer > $nbPages) then $nbPages else ."/>-->
                </xsl:if>
            </xf:action>

            <xf:var name="context" xxbl:scope="inner">
                <xxf:sequence value="xxf:binding(event('xxf:targetid'))[index(event('xxf:targetid'))]" xxbl:scope="outer"/>
            </xf:var>
            <xf:var name="index" xxbl:scope="inner">
                <xxf:sequence value="index(event('xxf:targetid'))" xxbl:scope="outer"/>
            </xf:var>

            <xf:dispatch name="fr-selection-changed" targetid="fr.datatable" xxbl:scope="inner">
                <xf:property name="index" value="$index"/>
                <xf:property name="selected" value="$context"/>
            </xf:dispatch>

        </xf:action>

    </xsl:template>

    <xsl:template match="/*/xh:tbody/xf:repeat/xh:tr" mode="dynamic">
        <xh:tr
            class="
            {{if (position() = 1) then 'yui-dt-first' else '' }}
            {{if (position() = last()) then 'yui-dt-last' else '' }}
            {{if (position() mod 2 = 0) then 'yui-dt-odd' else 'yui-dt-even' }}
            {{if (xxf:index() = position()) then 'yui-dt-selected' else ''}}
            {@class}"
            style="height: auto;" xxbl:scope="outer">
            <xsl:apply-templates select="@*[name() != 'class']|node()" mode="dynamic"/>
        </xh:tr>
    </xsl:template>

    <xsl:template match="/*/xh:tbody/xf:repeat/xh:tr/xh:td" mode="dynamic">
        <xsl:variable name="index" select="count(preceding-sibling::*) + 1" xxbl:scope="inner"/>
        <xf:var name="index" value="{$index}" xxbl:scope="inner"/>
        <xf:var name="fr-dt-columnDesc" xxbl:scope="outer">
            <xxf:sequence model="datatable-model" value="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        </xf:var>

        <xh:td
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''}
            {{if ($fr-dt-columnDesc/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-columnDesc/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xh:div class="yui-dt-liner datatable-cell-content">
                <xsl:apply-templates select="node()" mode="dynamic"/>
            </xh:div>
        </xh:td>
    </xsl:template>

    <xsl:template match="/*/xh:tbody/xf:repeat/xh:tr/xf:repeat/xh:td" mode="dynamic">
        <xf:var name="position" value="position()" xxbl:scope="inner">
            <xxf:sequence value="position()" xxbl:scope="outer"/>
        </xf:var>
        <xf:var name="index" value="{count(../preceding-sibling::*) + 1}" xxbl:scope="inner"/>
        <xf:var name="columnSet" model="datatable-model" value="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        <xf:var name="columnIndex" model="datatable-model" value="$columnSet/@index + $position - 1" xxbl:scope="inner"/>
        <xf:var name="fr-dt-column" xxbl:scope="outer">
            <xxf:sequence model="datatable-model" value="$columnSet/column[@index = $columnIndex]" xxbl:scope="inner"/>
        </xf:var>
        <xh:td
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''}
            {{if ($fr-dt-column/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-column/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xh:div class="yui-dt-liner datatable-cell-content">
                <xsl:apply-templates select="node()" mode="dynamic"/>
            </xh:div>
        </xh:td>
    </xsl:template>

    <xsl:template match="/*/xh:tbody/xf:repeat/xh:tr/xf:group/xh:td" mode="dynamic">
        <xf:var name="position" value="position()" xxbl:scope="inner">
            <xxf:sequence value="position()" xxbl:scope="outer"/>
        </xf:var>
        <xf:var name="index" value="{count(../preceding-sibling::*) + 1}" xxbl:scope="inner"/>
        <xf:var name="columnSet" model="datatable-model" value="instance('datatable-instance')/*[position() = $index]" xxbl:scope="inner"/>
        <xf:var name="columnIndex" model="datatable-model" value="$columnSet/@index + $position - 1" xxbl:scope="inner"/>
        <xf:var name="fr-dt-column" xxbl:scope="outer">
            <xxf:sequence model="datatable-model" value="$columnSet/column[@index = $columnIndex]" xxbl:scope="inner"/>
        </xf:var>
        <xh:td
            class="
            {if (@fr:sortable = 'true') then 'yui-dt-sortable' else ''}
            {{if ($fr-dt-column/@currentSortOrder = 'ascending') then 'yui-dt-asc'
            else if ($fr-dt-column/@currentSortOrder = 'descending') then 'yui-dt-desc' else '' }}
            {@class}
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="dynamic"/>
            <xh:div class="yui-dt-liner datatable-cell-content">
                <xsl:apply-templates select="node()" mode="dynamic"/>
            </xh:div>
        </xh:td>
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

    <xsl:template match="xf:output" mode="dyn-sortKey">
        <xpath>
            <xsl:value-of select="@ref|@value"/>
        </xpath>
    </xsl:template>


    <!--

        Column mode is used to consolidate information about columns
        from theader and tbody

    -->

    <xsl:template match="/*/xh:thead/xh:tr/*" mode="dyn-columns">
        <xsl:message terminate="yes">Unxepected element (<xsl:value-of select="name()"/> found in a datatable header (expecting either xh:th or
            xf:repeat).</xsl:message>
    </xsl:template>

    <xsl:template match="/*/xh:thead/xh:tr/*[&neutralElementsInHeaderOrBody;]" mode="dyn-columns" priority="1">
        <xsl:call-template name="identity"/>
    </xsl:template>

    <xsl:template match="/*/xh:thead/xh:tr/xh:th" mode="dyn-columns" priority="1">
        <xsl:variable name="position" select="fr:significantPositionInRow(.)"/>
        <xsl:variable name="body" select="fr:significantElementInRowAtPosition(/*/xh:tbody/xf:repeat/xh:tr, $position)"/>
        <xsl:if test="not($body/self::xh:td)">
            <xsl:message terminate="yes">Datatable: mismatch, significant element position <xsl:value-of select="$position"/> is a <xsl:value-of
                    select="name()"/> in the header and a <xsl:value-of select="name($body)"/> in the body.</xsl:message>repeat </xsl:if>
        <column xmlns="">
            <xsl:if test="$is-internally-sorted">
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

    <xsl:template match="/*/xh:thead/xh:tr/xf:repeat" mode="dyn-columns" priority="1">
        <xsl:variable name="position" select="fr:significantPositionInRow(.)"/>
        <xsl:variable name="body" select="fr:significantElementInRowAtPosition(/*/xh:tbody/xf:repeat/xh:tr, $position)"/>
        <xsl:if test="not($body/self::xf:repeat)">
            <xsl:message terminate="yes">Datatable: mismatch, significant element position <xsl:value-of select="$position"/> is a <xsl:value-of
                    select="name()"/> in the header and a <xsl:value-of select="name($body)"/> in the body.</xsl:message>
        </xsl:if>
        <columnSet xmlns="">
            <xsl:if test="$is-internally-sorted">
                <xsl:attribute name="sortKey">
                    <xsl:apply-templates select="$body" mode="dyn-sortKey"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:copy-of select="$body/(@ref, @nodeset)[1]|xh:th/@*"/>
            <header>
                <xsl:copy-of select="."/>
            </header>
            <body>
                <xsl:copy-of select="$body"/>
            </body>
        </columnSet>
    </xsl:template>

    <xsl:template match="/*/xh:thead/xh:tr/xf:group" mode="dyn-columns" priority="1">
        <xsl:variable name="position" select="fr:significantPositionInRow(.)"/>
        <xsl:variable name="body" select="fr:significantElementInRowAtPosition(/*/xh:tbody/xf:group/xh:tr, $position)"/>
        <xsl:if test="not($body/self::xf:group)">
            <xsl:message terminate="yes">Datatable: mismatch, significant element position <xsl:value-of select="$position"/> is a <xsl:value-of
                    select="name()"/> in the header and a <xsl:value-of select="name($body)"/> in the body.</xsl:message>
        </xsl:if>
        <columnSet xmlns="">
            <xsl:if test="$is-internally-sorted">
                <xsl:attribute name="sortKey">
                    <xsl:apply-templates select="$body" mode="dyn-sortKey"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:copy-of select="$body/@ref|xh:th/@*"/>
            <header>
                <xsl:copy-of select="."/>
            </header>
            <body>
                <xsl:copy-of select="$body"/>
            </body>
        </columnSet>
    </xsl:template>

    <xsl:template match="column" mode="dyn-loadingIndicator">
        <xsl:apply-templates select="header/xh:th" mode="dynamic"/>
    </xsl:template>

    <xsl:variable name="fakeColumn">
        <header xmlns="">
            <xh:th class="fr-datatable-columnset-loading-indicator">&#160;...&#160;</xh:th>
        </header>
    </xsl:variable>

    <xsl:template match="columnSet" mode="dyn-loadingIndicator">
        <xsl:apply-templates select="$fakeColumn/header/xh:th"/>
    </xsl:template>

    <!-- Header isolation mode: remove table body and @id attributes -->
    <xsl:template match="xh:table/@id" mode="headerIsolation"/>

    <xsl:template match="xh:tbody" mode="headerIsolation"/>

    <!-- Body filtering mode: remove @id attributes in the table header -->
    <xsl:template match="xh:thead//@id" mode="bodyFiltering"/>

    <xsl:template name="pagination">
        <xsl:param name="prefix" as="xs:string"/>
        <xsl:if test="$paginated">
            <xh:div class="yui-dt-paginator yui-pg-container" style="">

                <xf:group appearance="xxf:internal" xxbl:scope="inner">

                    <xf:group ref=".[$page = 1]" id="{$prefix}-first-disabled">
                        <xh:span class="yui-pg-first">&lt;&lt; first</xh:span>
                    </xf:group>
                    <xf:group ref=".[$page != 1]" id="{$prefix}-first-enabled">
                        <xf:trigger class="yui-pg-first" appearance="minimal" id="{$prefix}-first-trigger">
                            <xsl:if test="$is-modal"><xsl:attribute name="xxf:modal">true</xsl:attribute></xsl:if>
                            <xf:label>&lt;&lt; first </xf:label>
                            <xsl:call-template name="fr-goto-page">
                                <xsl:with-param name="fr-new-page">1</xsl:with-param>
                            </xsl:call-template>
                        </xf:trigger>
                    </xf:group>

                    <xf:group ref=".[$page = 1]" id="{$prefix}-prev-disabled">
                        <xh:span class="yui-pg-previous">&lt; prev</xh:span>
                    </xf:group>
                    <xf:group ref=".[$page != 1]" id="{$prefix}-enabled">
                        <xf:trigger class="yui-pg-previous" appearance="minimal" id="{$prefix}-enabled-trigger">
                            <xsl:if test="$is-modal"><xsl:attribute name="xxf:modal">true</xsl:attribute></xsl:if>
                            <xf:label>&lt; prev</xf:label>
                            <xsl:call-template name="fr-goto-page">
                                <xsl:with-param name="fr-new-page">$page - 1</xsl:with-param>
                            </xsl:call-template>
                        </xf:trigger>
                    </xf:group>

                    <xh:span class="yui-pg-pages">
                        <xf:repeat ref="$pages" id="{$prefix}-pages">
                            <xsl:choose>
                                <xsl:when test="$maxNbPagesToDisplay &lt; 0">
                                    <xf:var name="display">page</xf:var>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xf:var name="display"
                                        value="
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
                            <xf:group ref=".[. = $page and $display = 'page']" id="{$prefix}-page-current">
                                <xf:output class="yui-pg-page" value="$page" id="{$prefix}-page-number">
                                    <!-- <xf:hint>Current page (edit to move to another
                                    page)</xf:hint>-->
                                </xf:output>
                            </xf:group>
                            <xf:group ref=".[. != $page and $display = 'page']" id="{$prefix}-page-goto">
                                <xf:var name="targetPage" value="."/>
                                <xf:trigger class="yui-pg-page" appearance="minimal" id="{$prefix}-page-goto-trigger">
                                    <xsl:if test="$is-modal"><xsl:attribute name="xxf:modal">true</xsl:attribute></xsl:if>
                                    <xf:label>
                                        <xf:output value="."/>
                                    </xf:label>
                                    <xsl:call-template name="fr-goto-page">
                                        <xsl:with-param name="fr-new-page">$targetPage</xsl:with-param>
                                    </xsl:call-template>
                                </xf:trigger>
                            </xf:group>
                            <xf:group ref=".[ $display = 'ellipses']" id="{$prefix}-ellipses">
                                <xh:span class="yui-pg-page">...</xh:span>
                            </xf:group>
                        </xf:repeat>
                    </xh:span>

                    <xf:group ref=".[$page = $nbPages or $nbPages = 0]" id="{$prefix}-next-disabled">
                        <xh:span class="yui-pg-next">next ></xh:span>
                    </xf:group>
                    <xf:group ref=".[$page != $nbPages and $nbPages != 0]" id="{$prefix}-next-enabled">
                        <xf:trigger class="yui-pg-next" appearance="minimal" xxf:modal="true" id="{$prefix}-next-trigger">
                            <xsl:if test="$is-modal"><xsl:attribute name="xxf:modal">true</xsl:attribute></xsl:if>
                            <xf:label>next ></xf:label>
                            <xsl:call-template name="fr-goto-page">
                                <xsl:with-param name="fr-new-page">$page + 1</xsl:with-param>
                            </xsl:call-template>
                        </xf:trigger>
                    </xf:group>

                    <xf:group ref=".[$page = $nbPages or $nbPages = 0]" id="{$prefix}-last-disabled">
                        <xh:span class="yui-pg-last">last >></xh:span>
                    </xf:group>
                    <xf:group ref=".[$page != $nbPages and $nbPages != 0]" id="{$prefix}-last-enabled">
                        <xf:trigger class="yui-pg-last" appearance="minimal" id="{$prefix}-last-trigger">
                            <xsl:if test="$is-modal"><xsl:attribute name="xxf:modal">true</xsl:attribute></xsl:if>
                            <xf:label>last >></xf:label>
                            <xsl:call-template name="fr-goto-page">
                                <xsl:with-param name="fr-new-page">$nbPages</xsl:with-param>
                            </xsl:call-template>
                        </xf:trigger>
                    </xf:group>

                </xf:group>
            </xh:div>

        </xsl:if>
    </xsl:template>

</xsl:transform>
