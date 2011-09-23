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
<xsl:transform version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary"
        xmlns:map="java:java.util.Map"
        xmlns:formRunner="java:org.orbeon.oxf.fr.FormRunner"
        xmlns:controlOps="java:org.orbeon.oxf.fb.ControlOps"
        xmlns:xformsUtils="java:org.orbeon.oxf.xforms.XFormsUtils">

    <xsl:variable name="data" select="/*" as="element()"/>
    <xsl:variable name="parameters" select="doc('input:parameters')/*" as="element()"/>

    <xsl:function name="fr:classes" as="xs:string*">
        <xsl:param name="e" as="element()"/>
        <xsl:copy-of select="tokenize($e/@class, '\s+')"/>
    </xsl:function>

    <xsl:function name="fr:is-container-xbl" as="xs:boolean">
        <xsl:param name="e" as="element()"/>
        <xsl:variable name="classes" select="fr:classes($e)"/>
        <xsl:copy-of select="$classes = 'xbl-fr-section' or ($classes = 'xbl-fr-grid' and $e//table[fr:classes(.) = 'fr-repeat'])"/>
    </xsl:function>

    <xsl:function name="fr:is-container-legacy" as="xs:boolean">
        <xsl:param name="e" as="element()"/>
        <xsl:variable name="classes" select="fr:classes($e)"/>
        <xsl:copy-of select="($classes = 'fr-section-container' and not($e/ancestor::*[fr:classes(.) = 'xbl-fr-section']))
                                or ($e/self::table and $classes = 'fr-repeat' and not($e/ancestor::*[fr:classes(.) = 'xbl-fr-grid']))"/>
    </xsl:function>

    <xsl:function name="fr:is-container" as="xs:boolean">
        <xsl:param name="e" as="element()"/>
        <!-- This is more complicated than it should be cause we want to address XBL versions of fr:section/fr:grid as well as legacy versions of those and fr:repeat -->
        <!-- NOTE: Non-repeated grids are not considered "containers" for this purpose for now -->
        <xsl:copy-of select="fr:is-container-xbl($e) or fr:is-container-legacy($e)"/>
    </xsl:function>

    <xsl:function name="fr:container-static-id" as="xs:string">
        <xsl:param name="e" as="element()"/>

        <xsl:variable name="classes" select="fr:classes($e)"/>
        <xsl:variable name="static-id" select="xformsUtils:getStaticIdFromId($e/@id)"/>

        <xsl:choose>
            <xsl:when test="fr:is-container-xbl($e)">
                <!-- Normal case -->
                <xsl:value-of select="$static-id"/>
            </xsl:when>
            <xsl:when test="ends-with($static-id, '-section-group')">
                <!-- Case of a legacy section -->
                <xsl:value-of select="substring-before($static-id, '-group')"/>
            </xsl:when>
            <xsl:otherwise>
                <!-- Case of a legacy repeat -->
                <xsl:value-of select="replace(($e//tr[fr:classes(.) = 'xforms-repeat-begin-end'])[1]/@id, 'repeat-begin-(.*)', '$1')"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>

    <xsl:variable name="is-a-control-classes" select="('xforms-control', 'xbl-component')" as="xs:string*"/>
    <xsl:variable name="is-not-a-control-classes" select="('xforms-trigger', 'xforms-disabled', 'xforms-group', 'xforms-case', 'xforms-switch', 'xforms-repeat')" as="xs:string*"/>
    <xsl:variable name="control-classes" select="('xforms-input', 'xforms-textarea', 'xforms-select', 'xforms-select1', 'fr-attachment', 'xforms-output')" as="xs:string*"/>
    <xsl:variable name="attachment-classes" select="('fr-attachment', 'xbl-fr-image-attachment')" as="xs:string*"/>

    <xsl:template match="/">
        <config>
            <!-- Template -->
            <template href="input:template" show-grid="false"/>

            <!-- Barcode -->
            <!-- NOTE: Code 39 only take uppercase letters, hence we upper-case(…) -->
            <xsl:variable name="barcode-value" select="upper-case($parameters/document)" as="xs:string?"/>
            <xsl:if test="normalize-space($barcode-value) != '' and pipeline:property(string-join(('oxf.fr.detail.pdf.barcode', $parameters/app, $parameters/form), '.'))">
                <group ref="/*" font-pitch="15.9" font-family="Courier" font-size="12">
                    <barcode left="50" top="780" height="15" value="'{$barcode-value}'"/>
                </group>
            </xsl:if>

            <xsl:variable name="pdfFormats" select="formRunner:getPDFFormats()"/>

            <xsl:for-each select="//*[@id = 'fr-form-group']//*[fr:classes(.) = $is-a-control-classes and not(fr:classes(.) = $is-not-a-control-classes)]">

                <xsl:variable name="control" select="."/>
                <xsl:variable name="static-id" select="xformsUtils:getStaticIdFromId(@id)"/>
                <xsl:variable name="iterations" select="tokenize(xformsUtils:getEffectiveIdSuffix(@id), '-')"/>
                
                <xsl:variable name="ancestor-static-ids" select="ancestor::*[fr:is-container(.)]/fr:container-static-id(.)"/>
                <xsl:variable name="effective-id" select="replace(string-join((for $id in ($ancestor-static-ids, $static-id) return controlOps:controlName($id), $iterations), '$'), '·', '\$')"/>

                <xsl:variable name="classes" select="fr:classes(.)"/>

                <!-- Get the expression to evaluate on the control from the configuration properties -->
                <xsl:variable name="expression" as="xs:string?">
                    <xsl:choose>
                        <xsl:when test="$classes = 'xbl-component'">
                            <!-- XBL component -->
                            <xsl:variable name="component-name" select="for $c in $classes return if ($c != 'xbl-component' or not(starts-with($c, 'xbl-'))) then substring-after($c, 'xbl-') else ()"/>
                            <xsl:copy-of select="formRunner:getPDFFormatExpression($pdfFormats, $parameters/app, $parameters/form, $component-name, ())"/>
                        </xsl:when>
                        <xsl:when test="$classes = $control-classes">
                            <!-- Built-in controls -->
                            <xsl:variable name="component-name" select="$control-classes[. = $classes][1]"/>
                            <xsl:variable name="type" select="(for $c in $classes return if (starts-with($c, 'xforms-type-')) then substring-after($c, 'xforms-type-') else (), 'string')[1]"/>
                            <xsl:copy-of select="formRunner:getPDFFormatExpression($pdfFormats, $parameters/app, $parameters/form, $component-name, $type)"/>
                        </xsl:when>
                    </xsl:choose>
                </xsl:variable>

                <!-- If an expression was found, evaluate it to produce the value of the field -->
                <xsl:if test="$expression">
                    <xsl:variable name="value" select="$control/saxon:evaluate(string($expression))"/>
                    <xsl:if test="$value">
                        <xsl:choose>
                            <xsl:when test="$classes = $attachment-classes">
                                <!-- Handle URL rewriting for image attachments -->
                                <image acro-field-name="'{$effective-id}'" href="{pipeline:rewriteResourceURI($value, true())}"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <field acro-field-name="'{$effective-id}'" value="'{replace($value, '''', '''''')}'"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:if>
                </xsl:if>

                <!-- Also provide a mapping for select where disjoint values are output as separate acrobat fields -->
                <xsl:if test="$classes = 'xforms-select'">
                    <xsl:variable name="expression" select="map:get($pdfFormats, 'select-values')"/>
                    <xsl:if test="$expression">
                        <xsl:for-each select="$control/saxon:evaluate(string($expression))">
                            <xsl:variable name="item-value" as="xs:string" select="."/>
                            <xsl:if test="$item-value">
                                <field acro-field-name="'{$effective-id}${$item-value}'" value="'true'"/>
                            </xsl:if>
                        </xsl:for-each>
                    </xsl:if>
                </xsl:if>
            </xsl:for-each>
        </config>
    </xsl:template>


</xsl:transform>
