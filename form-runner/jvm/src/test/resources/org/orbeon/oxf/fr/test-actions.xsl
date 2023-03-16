<!--
  Copyright (C) 2018 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:transform
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"

    version="2.0">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
    <xsl:import href="oxf:/apps/fr/components/actions.xsl"/>
    <xsl:import href="oxf:/apps/fr/components/actions-20182.xsl"/>

    <xsl:variable
        name="fr-form-model"
        select="/xh:html/xh:head/xf:model[@id = 'fr-form-model']"/>

    <xsl:variable
        name="fr-form-model-id"
        select="$fr-form-model/generate-id()"/>

    <xsl:variable
        name="candidate-action-models-ids"
        select="$fr-form-model-id"/>

    <xsl:template match="/">
        <xsl:for-each select="$fr-form-model">
            <xsl:copy>
                <xsl:variable name="processed-actions" as="element(_)">
                    <_>
                        <xsl:apply-templates select="@* | node()"/>
                        <xsl:copy-of select="fr:common-service-actions-impl($fr-form-model)"/>
                    </_>
                </xsl:variable>

                <xsl:apply-templates select="$processed-actions/(@* | node())" mode="filter-out-action-implementation"/>

            </xsl:copy>
        </xsl:for-each>
    </xsl:template>

    <!-- Remove actual actions implementation -->
    <xsl:template match="*[p:has-class('fr-action-impl')]" mode="filter-out-action-implementation">
        <xsl:comment>Action Implementation Here</xsl:comment>
    </xsl:template>

    <xsl:template
        match="_/xf:instance[@id = ('fr-form-instance', 'fr-form-resources', 'fr-form-metadata', 'fr-form-attachments')] | xf:bind"
        mode="filter-out-action-implementation"/>

    <xsl:template
        match="_/comment()"
        mode="filter-out-action-implementation"/>

</xsl:transform>