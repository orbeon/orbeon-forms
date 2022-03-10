<!--
  Copyright (C) 2011 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:transform
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"

    xmlns:array="http://www.w3.org/2005/xpath-functions/array"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"

    version="2.0"
>

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- Don't duplicate constants between Scala/XSLT -->
    <xsl:variable name="att-x" select="ClientNames:AttX()" xmlns:ClientNames="java:org.orbeon.oxf.fr.ClientNames"/>
    <xsl:variable name="att-y" select="ClientNames:AttY()" xmlns:ClientNames="java:org.orbeon.oxf.fr.ClientNames"/>
    <xsl:variable name="att-w" select="ClientNames:AttW()" xmlns:ClientNames="java:org.orbeon.oxf.fr.ClientNames"/>
    <xsl:variable name="att-h" select="ClientNames:AttH()" xmlns:ClientNames="java:org.orbeon.oxf.fr.ClientNames"/>

    <xsl:variable name="root" select="/*[1]"/>

    <xsl:variable name="readonly"                 select="$root/@readonly = 'true'"/>

    <!-- Point to the source element for this grid -->
    <!-- TODO LATER: Remove this once we no longer need `fbf:hasEditor($control, 'static-itemset'))` -->
    <xsl:variable name="edit-ref"                 select="$root/@edit-ref"/>
    <xsl:variable name="is-editable"              select="exists($edit-ref)"/>

    <!-- Determine whether we use the 12-column layout by the presence of a nested `<fr:c>` -->
    <xsl:variable name="is-12or24col-input"       select="exists($root/fr:c)"/>

    <!-- NOTE: Later, CSS grids can be used at runtime too -->
    <xsl:variable name="markup"                   select="/*/@markup/string()"/>
    <xsl:variable name="use-css-grids-output"     select="$is-editable or $markup = 'css-grid'"/>

    <xsl:variable name="apply-defaults"           select="$root/@apply-defaults = 'true'"/>

    <xsl:variable name="is-table"                 select="not($use-css-grids-output)"/>

    <xsl:variable name="table-elem"               select="if ($is-table) then 'xh:table' else 'xh:div'"/>
    <xsl:variable name="thead-elem"               select="if ($is-table) then 'xh:thead' else 'xh:div'"/>
    <xsl:variable name="tbody-elem"               select="if ($is-table) then 'xh:tbody' else 'xh:div'"/>
    <xsl:variable name="tr-elem"                  select="if ($is-table) then 'xh:tr'    else 'xh:div'"/>
    <xsl:variable name="th-elem"                  select="if ($is-table) then 'xh:th'    else 'xh:div'"/>
    <xsl:variable name="td-elem"                  select="if ($is-table) then 'xh:td'    else 'xh:div'"/>

    <xsl:variable
        name="rows-array"
        xmlns:cell="java:org.orbeon.oxf.fr.NodeInfoCell"
        select="
            if ($is-12or24col-input) then
                cell:analyze12ColumnGridAndFillHoles($root, not($use-css-grids-output))
            else
                cell:analyzeTrTdGridAndFillHoles($root, false())
    "/>

    <xsl:variable
        name="grid-rows-if-not-12col-input"
        select="
            if ($is-12or24col-input) then
                ()
            else
                ($root/*:body, $root/self::*)[1]/*:tr
   "/>

    <xsl:variable
        name="static-row-count"
        select="array:size($rows-array)"/>

    <!-- Only used for the CSS class -->
    <xsl:variable
        name="static-col-count"
        select="
            if (array:size($rows-array) ge 1) then
                sum(
                    for $map in array:get($rows-array, 1)
                        return map:get($map, 'w')
                )
            else
                0
        "/>

    <!-- Allow `<xf:var>` within grid but must add outer scope -->
    <!-- See discussion in https://github.com/orbeon/orbeon-forms/issues/2738  -->
    <xsl:template match="xf:var">
        <xsl:copy>
            <xsl:attribute name="xxbl:scope">outer</xsl:attribute>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Because we expect that <td class="{expr}"> to be in the outer scope. -->
    <xsl:function name="fr:scope-outer-avt-class" as="element(xf:var)*">
        <xsl:param name="input-class" as="attribute(class)?"/>
        <xsl:if test="exists($input-class)">
            <xf:var name="class-avt" xxbl:scope="outer"><xsl:value-of select="$input-class"/></xf:var>
            <xf:var name="class-value"                 ><xxf:value xxbl:scope="outer" value="xxf:evaluate-avt($class-avt)"/></xf:var>
        </xsl:if>
    </xsl:function>

    <xsl:function name="fr:th-td-tr-classes-attr">
        <xsl:param name="th-td-tr" as="xs:string"/> <!-- 'th' | 'td' | 'tr' -->
        <xsl:param name="class"    as="xs:string?"/>
        <xsl:param name="id"       as="xs:string?"/>
        <xsl:attribute
            name="class"
            select="
                concat('fr-grid-', $th-td-tr),
                '{$class-value}'[exists($class)],
                (
                    concat('{''fb-selected''[xxf:get-variable(''fr-form-model'', ''selected-cell'') = ''', $id, ''']}'),
                    'xforms-activable'
                )[$is-editable and $th-td-tr = 'td'] (: for cell selection :)
            "/>
    </xsl:function>

    <xsl:function name="fr:fb-control-classes" as="xs:string">
        <xsl:param name="control"       as="element()"/>
        <xsl:param name="check-itemset" as="xs:boolean"/>

        <xsl:variable
            use-when="function-available('fbf:hasEditor')"
            name="itemset-editor-class"
            select="'fb-itemset'[$check-itemset and fbf:hasEditor($control, 'static-itemset')]"/>

        <xsl:variable
            use-when="not(function-available('fbf:hasEditor'))"
            name="itemset-editor-class"
            select="()"/>

        <xsl:value-of select="
            'fb-label-is-html'[$control/xf:label/@mediatype = 'text/html'],
            'fb-hint-is-html' [$control/xf:hint /@mediatype = 'text/html'],
            'fb-text-is-html' [$control/fr:text /@mediatype = 'text/html'],
            $itemset-editor-class
        "/>
    </xsl:function>

    <xsl:function name="fr:rowspan-attribute" as="xs:string">
        <xsl:value-of select="if ($use-css-grids-output) then $att-h else 'rowspan'"/>
    </xsl:function>

    <xsl:function name="fr:colspan-attribute" as="xs:string">
        <xsl:value-of select="if ($use-css-grids-output) then $att-w else 'colspan'"/>
    </xsl:function>

    <xsl:function name="fr:bottom-div" as="element(xh:div)">

        <xsl:variable
            name="grid-has-relevant-control-xpath"
            select="
                string-join(
                    (
                        for $id
                            in $root/fr:c/(* except fr:hidden)/@id/string()
                            return concat('xxf:relevant(xxf:binding(''', $id, '''))'),
                        'false()'
                    ),
                    ' or '
                )
            "/>

        <xh:div class="{{'fr-grid-non-empty'[{$grid-has-relevant-control-xpath}]}}" xxbl:scope="outer"/>

    </xsl:function>

    <xsl:function name="fr:rows-content" as="node()*">
        <xsl:param name="is-repeat"    as="xs:boolean"/>
        <xsl:param name="left-column"  as="xs:boolean"/>
        <xsl:param name="right-column" as="xs:boolean"/>
        <xsl:param name="number-rows"  as="xs:boolean"/>
        <xsl:param name="side-block"   as="element(*)*"/>

        <xsl:variable
            name="lhh-in-header"
            select="$is-repeat and $static-row-count = 1"/>

        <xsl:for-each select="1 to array:size($rows-array)">

            <xsl:variable name="static-row-pos" select="."/>
            <xsl:variable name="cells"          select="array:get($rows-array, $static-row-pos)"/>

            <xsl:variable name="cells-content">
                <xsl:for-each select="$cells">
                    <xsl:variable name="map" select="."/>

                    <xsl:variable name="c" select="map:get($map, 'c')"/>
                    <xsl:variable name="x" select="map:get($map, 'x')"/>
                    <xsl:variable name="y" select="map:get($map, 'y')"/>
                    <xsl:variable name="h" select="map:get($map, 'h')"/>
                    <xsl:variable name="w" select="map:get($map, 'w')"/>

                    <xsl:variable name="controls"    select="$c/*" as="element()*"/>
                    <xsl:variable name="has-control" select="exists($controls)"/>
                    <xsl:variable name="control"     select="$controls[1]" as="element()?"/>

                     <xsl:if test="$is-editable">
                        <!-- Point to the contained control -->
                        <!-- Used below by `fbf:hasEditor()` only -->
                        <xf:var
                            name="control"
                            value="id('{$control/@id}', $grid)"
                            as="element()?"/>
                    </xsl:if>
                    <!-- Scope AVT vars -->
                    <xsl:if test="$is-editable or $is-repeat">
                        <xsl:copy-of select="fr:scope-outer-avt-class($c/@class)"/>
                    </xsl:if>

                    <xsl:element name="{$td-elem}">

                        <!-- Attributes -->
                        <xsl:attribute name="xxf:control">true</xsl:attribute><!-- for cell selection -->
                        <xsl:copy-of select="fr:th-td-tr-classes-attr('td', $c/@class, $c/@id)"/>

                        <xsl:if test="$h > 1"><xsl:attribute name="{fr:rowspan-attribute()}" select="$h"/></xsl:if>
                        <xsl:if test="$w > 1"><xsl:attribute name="{fr:colspan-attribute()}" select="$w"/></xsl:if>

                        <xsl:if test="$use-css-grids-output">
                            <xsl:attribute name="{$att-x}" select="$x"/>
                            <xsl:attribute name="{$att-y}" select="$y"/>
                        </xsl:if>

                        <xsl:apply-templates select="$c/(@* except (@class, @xxf:control, @x, @y, @h, @w))"/>

                        <xsl:if test="$is-editable">
                            <xsl:attribute name="xxf:control">true</xsl:attribute>
                        </xsl:if>

                        <!-- Content -->
                        <xf:group xxbl:scope="outer" appearance="xxf:internal">
                            <xsl:for-each select="if (not($is-editable)) then $controls else $control">
                                <xsl:copy>
                                    <xsl:attribute
                                        name="class"
                                        select="
                                            @class,
                                            concat('fr-grid-', $y, '-', $x),
                                            if ($is-editable) then fr:fb-control-classes(., true()) else ()
                                    "/>
                                    <xsl:copy-of
                                        select="
                                            (
                                                @* except @class
                                            )
                                            |
                                            (
                                                node() except (
                                                    if ($lhh-in-header and exists(@id) and empty(self::xf:trigger)) then
                                                        (xf:label, xf:hint, xf:help)
                                                    else
                                                        ()
                                                )
                                            )"/>
                                </xsl:copy>
                            </xsl:for-each>
                        </xf:group>
                    </xsl:element>
                </xsl:for-each>
            </xsl:variable><!-- $cells-content -->

            <xsl:choose>
                <xsl:when test="not($use-css-grids-output)">

                    <xsl:if test="exists($cells[1])"><!-- https://github.com/orbeon/orbeon-forms/issues/5260 -->
                        <xsl:variable name="tr" select="map:get($cells[1], 'c')/parent::xh:tr"/>

                        <xsl:if test="exists($tr/@class)">
                            <xsl:copy-of select="fr:scope-outer-avt-class($tr/@class)"/>
                        </xsl:if>

                        <xsl:element name="{$tr-elem}">

                            <xsl:choose>
                                <xsl:when test="exists($tr)">
                                    <xsl:copy-of select="fr:th-td-tr-classes-attr('tr', $tr/@class, ())"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:attribute name="class">fr-grid-tr</xsl:attribute>
                                </xsl:otherwise>
                            </xsl:choose>

                            <xsl:if test="($left-column or $number-rows) and $static-row-pos = 1">
                                <xsl:copy-of select="$side-block"/>
                            </xsl:if>
                            <xsl:copy-of select="$cells-content"/>
                            <xsl:if test="$right-column and $static-row-pos = 1">
                                <xsl:copy-of select="$side-block"/>
                            </xsl:if>
                        </xsl:element>
                    </xsl:if>
                </xsl:when>
                <xsl:otherwise>
                    <!-- CSS grids output -->
                    <xsl:copy-of select="$cells-content"/>
                </xsl:otherwise>
            </xsl:choose>

        </xsl:for-each>

    </xsl:function>

</xsl:transform>
