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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="data"/>
    <p:param type="input" name="bindings"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#data"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                            xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                            xmlns:xhtml="http://www.w3.org/1999/xhtml"
                            xmlns:xforms="http://www.w3.org/2002/xforms"
                            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
                            xmlns:ev="http://www.w3.org/2001/xml-events"
                            xmlns:dataModel="java:org.orbeon.oxf.fb.DataModel">

                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

                <xsl:variable name="model" select="/*/xhtml:head/xforms:model[@id = 'fr-form-model']"/>

                <!-- Duplicated from model.xml, but we have to right now as the form hasn't been loaded yet at this point -->
                <xsl:variable name="is-custom-instance"
                              select="$model/xforms:instance[@id = 'fr-form-metadata']/*/form-instance-mode = 'custom'"/>

                <!-- Custom instance: add dataModel namespace binding to top-level bind -->
                <xsl:template match="xforms:bind[@id = 'fr-form-binds']">
                    <xsl:copy>
                        <!-- NOTE: add even if not($is-custom-instance) so that things work if we switch to $is-custom-instance -->
                        <xsl:namespace name="dataModel" select="'java:org.orbeon.oxf.fb.DataModel'"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:copy>
                </xsl:template>
                <!-- Custom instance: for nested binds, rewrite @ref/@nodeset -->
                <xsl:template match="xforms:bind[$is-custom-instance and @id and @id != 'fr-form-binds']/@ref | xforms:bind[$is-custom-instance and @id and @id != 'fr-form-binds']/@nodeset">
                    <xsl:attribute name="{name()}" select="dataModel:annotatedBindRef(../@id, .)"/>
                </xsl:template>

                <!-- Temporarily mark read-only instances as read-write -->
                <xsl:template match="xforms:instance[@xxforms:readonly = 'true']">
                    <xsl:copy>
                        <xsl:attribute name="fb:readonly" select="'true'"/><!-- so we remember to set the value back -->
                        <xsl:apply-templates select="@* except @xxforms:readonly | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Update namespace on observers on FB actions so that they don't run at design time -->
                <xsl:template match="xforms:action[ends-with(@id, '-binding')]">
                    <xsl:copy>
                        <xsl:apply-templates select="@* | node()" mode="update-observers"/>
                    </xsl:copy>
                </xsl:template>

                <xsl:template match="@ev:observer | @observer" mode="update-observers">
                    <xsl:attribute name="fb:{local-name()}" select="."/>
                </xsl:template>

                <!-- fr:view → xf:group -->
                <xsl:template match="xhtml:body//fr:view">
                    <xforms:group class="fb-view">
                        <!-- NOTE: We don't copy the view label anymore as it's unneeded -->

                        <!-- Scope $lang which is the language of the form being edited -->
                        <xforms:var name="lang" value="xxforms:get-variable('fr-form-model', 'fb-lang')" as="element()"/>
                        <!-- Scope $form-resources: resources of the form being edited.
                             Use the same logic as in resources-model. In the builder, we don't have a resources-model running
                             for the form being edited, so we duplicate this here. -->
                        <xforms:var name="form-resources" value="instance('fr-form-resources')/(resource[@xml:lang = $lang], resource[1])[1]" as="element(resource)?"/>
                        <!-- Scope $fr-resources for Form Runner resources -->
                        <xforms:var name="fr-resources" value="xxforms:get-variable('fr-resources-model', 'fr-fr-resources')" as="element(resource)?"/>
                        <!-- Scope $fb-resources for Form Builder resources -->
                        <xforms:var name="fb-resources" value="xxforms:get-variable('fr-resources-model', 'fr-form-resources')" as="element(resource)?"/>

                        <!-- Apply all the content -->
                        <xforms:group class="fb-body">
                            <xsl:apply-templates select="fr:body/node()"/>
                        </xforms:group>

                        <!-- Listen to activations on grid cells -->
                        <xforms:action ev:event="DOMActivate" xxforms:phantom="true">
                            <xforms:var name="control-element" value="xxforms:control-element(event('xxforms:absolute-targetid'))"/>
                            <xforms:action if="tokenize($control-element/@class, '\s+') = 'xforms-activable'">
                                <xforms:var name="th-column" value="count($control-element/preceding-sibling::*[@xxforms:element = 'xh:th']) + 1"/>
                                <xforms:var name="new-selected-cell" value="if ($control-element/@xxforms:element = 'xh:th') then
                                    ($control-element/following-sibling::xforms:repeat//*[@xxforms:element = 'xh:td'])[$th-column]/@id
                                    else $control-element/@id"/>
                                <xforms:setvalue ref="xxforms:get-variable('fr-form-model', 'selected-cell')" value="$new-selected-cell"/>
                            </xforms:action>
                        </xforms:action>

                        <!--<xforms:output value="xxforms:get-variable('fr-form-model', 'selected-cell')" xxbl:scope="inner">-->
                            <!--<xforms:label>Selected:</xforms:label>-->
                        <!--</xforms:output>-->

                    </xforms:group>
                </xsl:template>

                <!-- fr:section → fb:section/(@edit-ref, @xxf:update) -->
                <xsl:template match="xhtml:body//fr:section">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:attribute name="edit-ref"/>
                        <xsl:attribute name="xxforms:update" select="'full'"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>

                <!-- fr:grid → fr:grid/@edit-ref -->
                <xsl:template match="xhtml:body//fr:grid">
                    <xsl:copy>
                        <xsl:attribute name="edit-ref"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Convert MIP names -->
                <xsl:template match="xforms:bind/@relevant | xforms:bind/@readonly | xforms:bind/@required | xforms:bind/@constraint | xforms:bind/@calculate | xforms:bind/@xxforms:default">
                    <xsl:attribute name="fb:{local-name()}" select="."/>
                </xsl:template>

                <!-- Add model actions -->
                <xsl:template match="xhtml:head/xforms:model[@id = 'fr-form-model']">
                    <xsl:copy>
                        <xsl:apply-templates select="@* | node()"/>

                        <!-- Upon model creation, recalculation and revalidation, notify Form Builder -->
                        <xsl:for-each select="('xforms-model-construct', 'xforms-recalculate', 'xforms-revalidate', 'xxforms-xpath-error')">
                            <xforms:action ev:event="{.}" ev:target="#observer" class="fb-annotation">
                                <!-- Upon MIP XPath error cancel the default error behavior (which otherwise can open an
                                     error dialog at inopportune times.) -->
                                <xsl:if test=". = 'xxforms-xpath-error'">
                                    <xsl:attribute name="ev:defaultAction">cancel</xsl:attribute>
                                </xsl:if>
                                <!-- Dispatch custom event to FB model -->
                                <xforms:dispatch name="fb-{.}" targetid="/fr-form-model"/>
                            </xforms:action>
                        </xsl:for-each>

                    </xsl:copy>
                </xsl:template>

                <!-- TODO: convert legacy fr:repeat -->

                <!-- Saxon serialization adds an extra meta element, make sure to remove it -->
                <xsl:template match="xhtml:head/meta[@http-equiv = 'Content-Type']"/>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="annotated"/>
    </p:processor>

    <!-- Make sure XBL bindings for section templates are present -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="add-template-bindings.xpl"/>
        <p:input name="data" href="#annotated"/>
        <p:input name="bindings" href="#bindings"/>
        <p:output name="data" ref="data"/>
    </p:processor>


</p:config>