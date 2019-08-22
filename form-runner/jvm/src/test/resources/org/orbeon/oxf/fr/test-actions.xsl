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
                <xsl:apply-templates select="@* | node()"/>
                <xsl:copy-of select="fr:common-service-actions-impl($fr-form-model)"/>
            </xsl:copy>
        </xsl:for-each>
    </xsl:template>

</xsl:transform>