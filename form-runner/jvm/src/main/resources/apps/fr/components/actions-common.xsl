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
<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:variable
        name="form-load-fr-action-names"
        select="
            'fr-run-form-load-action-before-data',
            'fr-run-form-load-action-after-data',
            'fr-run-form-load-action-after-controls'
    "/>

    <!-- NOTE: The order of this must match `$fr-action-names` above. -->
    <xsl:variable
        name="form-load-xforms-action-names"
        select="
            'xforms-model-construct',
            'xforms-model-construct-done',
            'xforms-ready'
    "/>

    <xsl:variable
        name="form-load-2018.2-action-names"
        select="
            'form-load-before-data',
            'form-load-after-data',
            'form-load-after-controls'
    "/>

    <xsl:variable
        name="controls-xforms-action-names"
        select="
            'xforms-value-changed',
            'xforms-enabled',
            'DOMActivate',
            'xxforms-visible',
            'xforms-disabled',
            'xxforms-hidden',
            'xforms-select',
            'xforms-deselect'
    "/>

    <xsl:variable
        name="controls-2018.2-action-names"
        select="
            'value-changed',
            'enabled',
            'activated',
            'visible',
            'disabled',
            'hidden',
            'item-selected',
            'item-deselected'
    "/>

    <xsl:variable
        name="request-action-classes"
        select="
            'fr-set-service-value-action',
            'fr-set-database-service-value-action'
    "/>

    <xsl:variable
        name="response-action-classes"
        select="
            'fr-set-control-value-action',
            'fr-itemset-action',
            'fr-save-to-dataset-action'
    "/>

</xsl:stylesheet>
