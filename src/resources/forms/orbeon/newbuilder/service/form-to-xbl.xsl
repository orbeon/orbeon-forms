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
<xsl:stylesheet version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:exforms="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- Namespace URI e.g. http://orbeon.org/oxf/xml/form-builder/component/APP/FORM -->
    <xsl:variable name="component-namespace" as="xs:string"
                  select="string-join(('http://orbeon.org/oxf/xml/form-builder/component', doc('input:parameters')/*/app, doc('input:parameters')/*/form), '/')"/>

    <xsl:template match="/">

        <xbl:xbl>
            <!-- Create namespace declaration for resolution of CSS selector -->
            <xsl:namespace name="component" select="$component-namespace"/>

            <!-- Add Form Builder metadata -->
            <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
                <display-name lang="en">Section Templates</display-name>
                <display-name lang="fr">Modèles de sections</display-name>
                <display-name lang="ru">Шаблоны разделов</display-name>
                <icon lang="en">
                    <small-icon>/forms/orbeon/newbuilder/images/input.png</small-icon>
                    <large-icon>/forms/orbeon/newbuilder/images/input.png</large-icon>
                </icon>
            </metadata>

            <xsl:apply-templates select="/xhtml:html/xhtml:body//fr:section"/>
        </xbl:xbl>
    </xsl:template>

    <xsl:template match="/xhtml:html/xhtml:body//fr:section">

        <xsl:variable name="fr-section" select="." as="element(fr:section)"/>

        <!-- TODO: for now consider any component in the "fr" namespace, but need to do better -->

        <xsl:variable name="fr-form-model" select="/xhtml:html/xhtml:head//xforms:model[@id = 'fr-form-model']" as="element(xforms:model)"/>
        <xsl:variable name="fr-form-instance" select="$fr-form-model/xforms:instance[@id = 'fr-form-instance']" as="element(xforms:instance)"/>
        <xsl:variable name="fr-resources-instance" select="$fr-form-model/xforms:instance[@id = 'fr-form-resources']" as="element(xforms:instance)"/>

        <!-- Section id -->
        <xsl:variable name="section-id" select="substring-before(@id, '-section')" as="xs:string"/>

        <!-- Section bind -->
        <xsl:variable name="section-bind" select="$fr-form-model//xforms:bind[@id = concat($section-id, '-bind')]" as="element(xforms:bind)"/>
        <xsl:variable name="section-name" select="$section-bind/@nodeset" as="xs:string"/>
        <xsl:variable name="section-resource" select="$section-bind/@nodeset" as="xs:string"/>

        <!-- Section instance data element -->
        <!-- NOTE: could also gather ancestor-or-self::xforms:bind/@nodeset and evaluate expression to be more generic -->
        <xsl:variable name="section-data" select="$fr-form-instance/*/*[name() = $section-name]" as="element()"/>

        <!-- Use section id as component id as section ids are unique -->
        <xsl:variable name="component-id" select="$section-id" as="xs:string"/>

        <!-- Create binding for the section/grid as a component -->
        <!-- TODO: Is using class fr-section-component the best way? -->
        <xbl:binding id="{$component-id}-component" element="component|{$component-id}" class="fr-section-component">

            <!-- Orbeon Form Builder Component Metadata -->
            <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
                <!-- Localized metadata -->
                <xsl:for-each select="$fr-resources-instance/*/resource">
                    <xsl:variable name="lang" select="@xml:lang" as="xs:string"/>

                    <display-name lang="{$lang}">
                        <xsl:value-of select="*[name() = $section-name]/label"/>
                    </display-name>
                    <description lang="{$lang}">
                        <xsl:value-of select="*[name() = $section-name]/help"/>
                    </description>
                    <icon lang="{$lang}">
                        <small-icon>/apps/fr/style/images/silk/plugin.png</small-icon>
                        <large-icon>/apps/fr/style/images/silk/plugin.png</large-icon>
                    </icon>
                </xsl:for-each>

                <!-- Type if any -->
                <xsl:if test="$section-bind/@type">
                    <datatype>
                        <xsl:value-of select="$section-bind/@type"/>
                    </datatype>
                </xsl:if>

                <!-- Control template -->
                <template>
                    <!-- NOTE: Element doesn't have LHHA elements for now -->
                    <xsl:element name="component:{$component-id}" namespace="{$component-namespace}"/>
                </template>
            </metadata>

            <!-- XBL implementation -->
            <xbl:implementation>
                <xforms:model id="{$component-id}-model">
                    <!-- Copy schema attribute if present -->
                    <xsl:copy-of select="$fr-form-model/@schema"/>

                    <!-- Section data model -->
                    <xforms:instance id="fr-form-instance">
                        <xsl:copy-of select="$section-data"/>
                    </xforms:instance>

                    <!-- Template -->
                    <xforms:instance id="fr-form-template">
                        <xsl:copy-of select="$section-data"/>
                    </xforms:instance>

                    <!-- Section constraints -->
                    <xforms:bind>
                        <xsl:copy-of select="$section-bind/xforms:bind"/>
                    </xforms:bind>

                    <!-- Sections resources -->
                    <xforms:instance id="fr-form-resources">
                        <resources xmlns="">
                            <xsl:for-each select="$fr-resources-instance/*/resource">
                                <xsl:variable name="lang" select="@xml:lang" as="xs:string"/>

                                <resource xml:lang="{$lang}">
                                    <xsl:copy-of select="*[name() = $section-name]"/>
                                    <xsl:copy-of select="*[name() = (for $n in $section-data/* return name($n))]"/>
                                </resource>
                            </xsl:for-each>
                        </resources>
                    </xforms:instance>

                    <!-- This is also at the top-level in components.xsl -->
                    <!-- TODO: would be ideal if read-onliness on outer instance would simply propagate here -->
                    <xforms:bind nodeset="instance('fr-form-instance')" readonly="xxforms:instance('fr-parameters-instance')/mode = ('view', 'pdf', 'email')"/>

                    <!-- Actions -->
                    <!-- TODO -->

                    <!-- Schema: simply copy so that the types are available locally -->
                    <!-- NOTE: Could optimized to check if any of the types are actually used -->
                    <xsl:copy-of select="$fr-form-model/xs:schema"/>

                </xforms:model>
            </xbl:implementation>

            <!-- XBL template -->
            <xbl:template>
                
                <xforms:group appearance="xxforms:internal" xxbl:scope="outer">

                    <!-- Inner group -->
                    <xforms:group appearance="xxforms:internal" xxbl:scope="inner">
                        <!-- Variable pointing to external node -->
                        <xxforms:variable id="binding" name="binding" as="node()?">
                            <xxforms:sequence select="." xxbl:scope="outer"/>
                        </xxforms:variable>
                        
                        <xforms:action ev:event="xforms-enabled xforms-value-changed" ev:observer="binding">
                            <!-- Section becomes visible OR binding changes -->
                            <xforms:action>
                                <xforms:action if="$binding/*">
                                    <!-- There are already some nodes, copy them in. This handles the case where existing
                                         external data must be loaded in, for example when editing a form. -->
                                    <xforms:delete nodeset="instance()/*"/>
                                    <xforms:insert context="instance()" origin="$binding/*"/>
                                </xforms:action>
                                <xforms:action if="not($binding/*)">
                                    <!-- No nodes, copy template out. This handles the case where there is not yet existing
                                         external data. -->
                                    <xforms:insert context="$binding" origin="instance('fr-form-template')/*"/>
                                    <xforms:insert context="instance()" origin="instance('fr-form-template')/*"/>
                                </xforms:action>
                            </xforms:action>
                        </xforms:action>

                        <!-- Expose internally a variable pointing to Form Runner resources -->
                        <xxforms:variable name="fr-resources" as="element()?">
                            <xxforms:sequence select="$fr-resources" xxbl:scope="outer"/>
                        </xxforms:variable>

                        <!-- Try to match the current form language, or use the first language available if not found -->
                        <xxforms:variable name="form-resources"
                                          select="instance('fr-form-resources')/(resource[@xml:lang = xxforms:instance('fr-language-instance')], resource[1])[1]" as="element(resource)"/>

                        <xforms:group appearance="xxforms:internal">
                            <!-- Synchronize data with external world upon local value change -->
                            <!-- This assumes the element QName match, or the value is not copied -->
                            <xforms:action ev:event="xforms-value-changed">
                                <xxforms:variable name="source-binding" select="event('xxforms:binding')" as="element()"/>
                                <xforms:setvalue ref="$binding/*[resolve-QName(name(), .) = resolve-QName(name($source-binding), $source-binding)]" value="$source-binding"/>
                            </xforms:action>

                            <!-- Copy grids within section -->
                            <xsl:copy-of select="$fr-section/(fr:grid | fb:grid)"/>

                        </xforms:group>
                    </xforms:group>
                </xforms:group>
            </xbl:template>
        </xbl:binding>


    </xsl:template>

</xsl:stylesheet>
