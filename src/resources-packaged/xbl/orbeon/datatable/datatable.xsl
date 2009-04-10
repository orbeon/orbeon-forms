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
    </xsl:variable>

    <!-- Set some variables that will dictate the geometry of the widget -->
    <xsl:variable name="scrollH" select="/fr:datatable/@scrollable = ('horizontal', 'both') and /fr:datatable/@width"/>
    <xsl:variable name="scrollV" select="/fr:datatable/@scrollable = ('vertical', 'both') and /fr:datatable/@height"/>
    <xsl:variable name="height" select="if ($scrollV) then concat('height: ', /fr:datatable/@height, ';') else ''"/>
    <xsl:variable name="width" select="if (/fr:datatable/@width) then concat('width: ', /fr:datatable/@width, ';') else ''"/>
    <xsl:variable name="id" select="if (/fr:datatable/@id) then /fr:datatable/@id else generate-id(/fr:datatable)"/>
    <xsl:variable name="paginated" select="/fr:datatable/@paginated = 'true'"/>
    <xsl:variable name="rowsPerPage"
        select="if (/fr:datatable/@rowsPerPage castable as xs:integer) then /fr:datatable/@rowsPerPage cast as xs:integer else 10"/>

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

    <xsl:template match="fr:datatable">
        <!-- Matches the bound element -->


        <xhtml:div id="{$id}-container">
            <!-- See http://snippets.dzone.com/posts/show/216... for the display: table hack-->
            <xsl:copy-of select="namespace::*"/>

  

            <xsl:variable name="pass1">
                <!-- 
                This pass generates the XHTML structure .
                and uses the default mode.
                
                -->


                <xhtml:table id="{$id}-table" class="datatable {if ($scrollV) then 'fr-scrollV' else ''}  {if ($scrollH) then 'fr-scrollH' else ''} "
                    style="{$height} {$width}">
                    <!-- Copy attributes that are not parameters! -->
                    <xsl:apply-templates select="@*[not(name() = ($parameters/*, 'id' ))]"/>
                    <xsl:if test="not(xhtml:colgroup)">
                        <!-- If there is no colgroup element, add one -->
                        <xhtml:colgroup>
                            <xsl:for-each select="((xhtml:tbody|self::*)/(xforms:repeat|self::*)/xhtml:tr)[1]/xhtml:td">
                                <xhtml:col/>
                            </xsl:for-each>
                        </xhtml:colgroup>
                    </xsl:if>
                    <xsl:if test="not(xhtml:thead)">
                        <!-- If there is no thead element, add one -->
                        <xhtml:thead>
                            <xhtml:tr>
                                <xsl:for-each select="((xhtml:tbody|self::*)/(xforms:repeat|self::*)/xhtml:tr)[1]/xhtml:td">
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
                <xforms:instance id="sort">
                    <xsl:variable name="sorted"
                        select="$pass1/xhtml:table/xhtml:thead/xhtml:tr/xhtml:th[@fr:sorted = ('descending', 'ascending')][1]/@fr:sorted"/>
                    <sort currentId="{if ($sorted) then count($sorted/../preceding-sibling::xhtml:th) + 1 else -1}" currentOrder="{$sorted}" xmlns="">
                        <xsl:for-each select="$pass1/xhtml:table/xhtml:thead/xhtml:tr/xhtml:th">
                            <key type="{if (@fr:sort-type = 'number') then 'number' else 'text'}">
                                <xsl:variable name="position" select="count(preceding-sibling::xhtml:th) + 1"/>
                                <xsl:if test="@fr:sortable='true'">
                                    <xsl:variable name="sortKeys">
                                        <xsl:apply-templates
                                            select="$pass1/xhtml:table/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td[position() = $position]"
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
                <xsl:if test="$paginated">
                    <xforms:instance id="page">
                        <page xmlns="">1</page>
                    </xforms:instance>
                    <xforms:bind id="page-binding" instance="page"/>
                </xsl:if>
                <xforms:bind nodeset="@currentId" type="xs:integer"/>

            </xforms:model>
            
            <xsl:if test="$paginated">
                <xxforms:variable name="page" model="datatable" select="instance('page')"/>
                <xxforms:variable name="nbRows" select="count({$pass1//xforms:repeat[1]/@nodeset})"/>
                <xxforms:variable name="nbPages" select="ceiling($nbRows div {$rowsPerPage}) cast as xs:integer"/>
                <xxforms:variable name="pages" select="for $p in 1 to $nbPages return xxforms:element('page', $p)"/>
            </xsl:if>
            
            <xsl:variable name="pagination">
                <xsl:if test="$paginated">
                    <xhtml:div class="yui-dt-paginator yui-pg-container" style="">
                          
                        <xforms:group ref=".[$page = 1]">
                            <xhtml:span class="yui-pg-first">&lt;&lt; first</xhtml:span>
                        </xforms:group>
                        <xforms:group ref=".[$page != 1]">
                            <xforms:trigger class="yui-pg-first" appearance="minimal">
                                <xforms:label>&lt;&lt; first</xforms:label>
                                <xforms:setvalue ev:event="DOMActivate" model="datatable" ref="instance('page')" value="1"/>
                            </xforms:trigger>
                        </xforms:group>
                        
                        <xforms:group ref=".[$page = 1]">
                            <xhtml:span class="yui-pg-previous">&lt; prev</xhtml:span>
                        </xforms:group>
                        <xforms:group ref=".[$page != 1]">
                            <xforms:trigger class="yui-pg-previous" appearance="minimal">
                                <xforms:label>&lt; prev</xforms:label>
                                <xforms:setvalue ev:event="DOMActivate" model="datatable" ref="instance('page')" value=". - 1"/>
                            </xforms:trigger>
                        </xforms:group>
                        
                        <xhtml:span class="yui-pg-pages">
                            <xforms:repeat nodeset="$pages">
                                <xforms:group ref=".[. = $page]">
                                    <xforms:output class="yui-pg-page" value="."/>
                                </xforms:group>
                                <xforms:group ref=".[. != $page]">
                                    <xxforms:variable name="targetPage" select="."/>
                                    <xforms:trigger class="yui-pg-page" appearance="minimal">
                                        <xforms:label>
                                            <xforms:output value="."/>
                                        </xforms:label>
                                        <xforms:setvalue ev:event="DOMActivate" model="datatable" ref="instance('page')" value="$targetPage"/>
                                    </xforms:trigger>
                                </xforms:group>
                            </xforms:repeat>
                        </xhtml:span>
                        
                        <xforms:group ref=".[$page = $nbPages]">
                            <xhtml:span class="yui-pg-next">next ></xhtml:span>
                        </xforms:group>
                        <xforms:group ref=".[$page != $nbPages]">
                            <xforms:trigger class="yui-pg-next" appearance="minimal">
                                <xforms:label>next ></xforms:label>
                                <xforms:setvalue ev:event="DOMActivate" model="datatable" ref="instance('page')" value=". + 1"/>
                            </xforms:trigger>
                        </xforms:group>
                        
                        <xforms:group ref=".[$page = $nbPages]">
                            <xhtml:span class="yui-pg-last">last >></xhtml:span>
                        </xforms:group>
                        <xforms:group ref=".[$page != $nbPages]">
                            <xforms:trigger class="yui-pg-last" appearance="minimal">
                                <xforms:label>last >></xforms:label>
                                <xforms:setvalue ev:event="DOMActivate" model="datatable" ref="instance('page')" value="$nbPages"/>
                            </xforms:trigger>
                        </xforms:group>
                        
                        <!-- <xhtml:div>
                            <xhtml:p>
                            <xforms:output value="$page">
                            <xforms:label>Page: </xforms:label>
                            </xforms:output>
                            </xhtml:p>
                            <xhtml:p>
                            <xforms:output value="$nbRows">
                            <xforms:label>nbRows: </xforms:label>
                            </xforms:output>
                            </xhtml:p>
                            <xhtml:p>
                            <xforms:output value="$nbPages">
                            <xforms:label>nbPages: </xforms:label>
                            </xforms:output>
                            </xhtml:p>
                            </xhtml:div>-->
                    </xhtml:div>
                    
                    
                </xsl:if>
            </xsl:variable>

            <xsl:copy-of select="$pagination"/>

            <xsl:apply-templates select="$pass1" mode="YUI"/>

            <xsl:copy-of select="$pagination"/>

        </xhtml:div>

        <!-- End of template on the bound element -->
    </xsl:template>


    <!-- 
        
        Default mode (pass1)
        
    -->

    <xsl:template match="xhtml:tr/@repeat-nodeset">
        <!-- Remove  repeat-nodeset attributes -->
    </xsl:template>

    <xsl:template match="xhtml:tr[@repeat-nodeset]">
        <!-- Generate xforms:repeat for  repeat-nodeset attributes-->
        <xforms:repeat nodeset="{@repeat-nodeset}">
            <xhtml:tr>
                <xsl:apply-templates select="@*|node()"/>
            </xhtml:tr>
        </xforms:repeat>
    </xsl:template>

    <xsl:template match="xhtml:td/xforms:output/xforms:label">
        <!-- Remove xforms:label since they are used as headers -->
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
        <xhtml:div class="yui-dt-liner dt-{$id}-col-{count(preceding-sibling::xhtml:th) + 1}">
            <xhtml:span class="yui-dt-label">
                <xsl:choose>
                    <xsl:when test="@fr:sortable = 'true'">
                        <xforms:group model="datatable" instance="sort">
                            <xforms:group ref=".[$nextSortOrder = 'ascending']">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xsl:apply-templates select="node()" mode="YUI"/>
                                    </xforms:label>
                                    <xforms:hint>Click to sort <xforms:output value="$nextSortOrder"/></xforms:hint>
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
                                    <xforms:hint>Click to sort <xforms:output value="$nextSortOrder"/></xforms:hint>
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
        <xforms:repeat>
            <xsl:attribute name="nodeset">
                <xsl:if test="$paginated">(</xsl:if>
                <xsl:text>if ($sort/@currentId = -1 or $sort/@currentOrder = '') then </xsl:text>
                <xsl:value-of select="@nodeset"/>
                <xsl:text> else exf:sort(</xsl:text>
                <xsl:value-of select="@nodeset"/>
                <xsl:text>, $key , $key/@type, $sort/@currentOrder)</xsl:text>
                <xsl:if test="$paginated">)[position() >= ($page - 1) * <xsl:value-of select="$rowsPerPage"/> + 1 and position() &lt;= $page *
                        <xsl:value-of select="$rowsPerPage"/>]</xsl:if>
            </xsl:attribute>
            <xsl:apply-templates select="@*[not(name()='nodeset')]|node()" mode="YUI"/>
        </xforms:repeat>
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
            <xhtml:div class="yui-dt-liner dt-{$id}-col-{count(preceding-sibling::xhtml:td) + 1}">
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


</xsl:transform>
