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
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"
    xmlns:ClientNames="java:org.orbeon.oxf.fr.ClientNames"

    xmlns:array="http://www.w3.org/2005/xpath-functions/array"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"

    version="2.0"
>

    <!-- Don't duplicate constants between Scala/XSLT -->
    <xsl:variable name="att-x" select="ClientNames:AttX()"/>
    <xsl:variable name="att-y" select="ClientNames:AttY()"/>
    <xsl:variable name="att-w" select="ClientNames:AttW()"/>
    <xsl:variable name="att-h" select="ClientNames:AttH()"/>

    <xsl:variable name="root" select="/*[1]"/>

    <!-- Point to the source element for this grid -->
    <!-- TODO LATER: Remove this once we no longer need `fbf:hasEditor($control, 'static-itemset'))` -->
    <xsl:variable name="edit-ref"                 select="$root/@edit-ref"/>
    <xsl:variable name="is-editable"              select="exists($edit-ref)"/>

    <!-- Determine whether we use the 12-column layout by the presence of a nested `<fr:c>` -->
    <xsl:variable name="is-12col-input"           select="exists($root/fr:c)"/>

    <!-- NOTE: Later, CSS grids can be used at runtime too -->
    <xsl:variable name="use-css-grids-output"     select="$is-12col-input and $is-editable"/>

    <xsl:variable name="apply-defaults"           select="$root/@apply-defaults = 'true'"/>

    <xsl:variable
        name="rows-array"
        xmlns:cell="java:org.orbeon.oxf.fr.NodeInfoCell"
        select="
            if ($is-12col-input) then
                cell:analyze12ColumnGridAndFillHoles($root, not($use-css-grids-output))
            else
                cell:analyzeTrTdGridAndFillHoles($root, false())
    "/>

    <xsl:variable
        name="grid-rows-if-not-12col-input"
        select="
            if ($is-12col-input) then
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

    <xsl:function name="fr:th-td-classes-attr">
        <xsl:param name="th-td" as="xs:string"/> <!-- Either 'th' or 'td' -->
        <xsl:param name="class" as="xs:string?"/>
        <xsl:param name="id"    as="xs:string?"/>
        <xsl:attribute
            name="class"
            select="
                concat('fr-grid-', $th-td),
                '{$class-value}'[exists($class)],
                (
                    concat('{''fb-selected''[xxf:get-variable(''fr-form-model'', ''selected-cell'') = ''', $id, ''']}'),
                    'xforms-activable'
                )[$is-editable and $th-td = 'td'] (: for cell selection :)
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

</xsl:transform>
