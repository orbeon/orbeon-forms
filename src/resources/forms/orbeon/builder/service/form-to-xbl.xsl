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
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
        xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderFunctions"
        xmlns:controlOps="java:org.orbeon.oxf.fb.ControlOps"
        xmlns:gridOps="java:org.orbeon.oxf.fb.GridOps"
        xmlns:exf="http://www.exforms.org/exf/1-0">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- Namespace URI e.g. http://orbeon.org/oxf/xml/form-builder/component/APP/FORM -->
    <xsl:variable name="component-namespace" as="xs:string"
                  select="string-join(('http://orbeon.org/oxf/xml/form-builder/component', doc('input:parameters')/*/app, doc('input:parameters')/*/form), '/')"/>

    <!-- Global stuff -->
    <xsl:variable name="fr-form-model" select="/xhtml:html/xhtml:head//xforms:model[@id = 'fr-form-model']" as="element(xforms:model)"/>
    <xsl:variable name="fr-form-instance" select="$fr-form-model/xforms:instance[@id = 'fr-form-instance']" as="element(xforms:instance)"/>
    <xsl:variable name="fr-resources-instance" select="$fr-form-model/xforms:instance[@id = 'fr-form-resources']" as="element(xforms:instance)"/>

    <!-- Actions and services -->
    <!-- NOTE: Actions and services are implemented, for historical reasons, as XForms instances, submissions, and action
         blocks. This means that we must analyze them to try to make sense of them, and this is a bit fragile. In the
         future, actions should be described in a more declarative format. See also:
         http://wiki.orbeon.com/forms/projects/form-runner-builder/improved-actions-and-services-format
     -->
    <xsl:variable name="actions" select="$fr-form-model/xforms:action[ends-with(@id, '-binding')]"/>
    <xsl:variable name="service-instances" select="$fr-form-model/xforms:instance[tokenize(@class, '\s+') = ('fr-service', 'fr-database-service')]"/>
    <xsl:variable name="service-submissions" select="$fr-form-model/xforms:submission[tokenize(@class, '\s+') = ('fr-service', 'fr-database-service')]"/>

    <!-- Distinct source ids for the given action -->
    <!-- NOTE: Source can also be a model -->
    <xsl:function name="fr:action-sources" as="xs:string*">
        <xsl:param name="action" as="element(xforms:action)"/>
        <xsl:sequence select="distinct-values($action/xforms:action[1]/@*:observer/tokenize(., '\s+'))"/>
    </xsl:function>

    <!-- Distinct destination ids for the given action -->
    <xsl:function name="fr:action-destinations" as="xs:string*">
        <xsl:param name="action" as="element(xforms:action)"/>
        <xsl:sequence select="distinct-values($action//(*:variable | *:var)[@name = 'control-name']/(@value, @select)/replace(., '''([^..]*)''', '$1-control'))"/>
    </xsl:function>

    <!-- Distinct action elements for which the given id is a source or destination or both -->
    <xsl:function name="fr:action-for-id" as="element(xforms:action)*">
        <xsl:param name="id" as="xs:string"/>
        <xsl:sequence select="$actions[$id = distinct-values((fr:action-sources(.), fr:action-destinations(.)))]"/>
    </xsl:function>

    <xsl:variable name="all-action-sources" select="distinct-values($actions/fr:action-sources(.))"/>
    <xsl:variable name="all-action-destinations" select="distinct-values($actions/fr:action-destinations(.))"/>

    <!-- Generate XBL container -->
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
                    <small-icon>/forms/orbeon/builder/images/input.png</small-icon>
                    <large-icon>/forms/orbeon/builder/images/input.png</large-icon>
                </icon>
            </metadata>

            <xsl:apply-templates select="/xhtml:html/xhtml:body//fr:section"/>
        </xbl:xbl>
    </xsl:template>

    <xsl:function name="fr:value-except" as="xs:anyAtomicType*">
      <xsl:param name="arg1" as="xs:anyAtomicType*"/>
      <xsl:param name="arg2" as="xs:anyAtomicType*"/>
      <xsl:sequence select="distinct-values($arg1[not(. = $arg2)])"/>
    </xsl:function>

    <!-- When copying actions, update references to instance and resources -->
    <xsl:template match="xforms:setvalue/@ref[starts-with(., 'instance(''fr-form-instance'')/*')] |
                         xforms:setvalue/@value[starts-with(., 'instance(''fr-form-instance'')/*')]" mode="filter-actions">
        <xsl:attribute name="{name()}" select="concat('instance(''fr-form-instance'')', substring-after(., 'instance(''fr-form-instance'')/*'))"/>
    </xsl:template>
    <xsl:template match="xxforms:variable[@name = 'control-resources']" mode="filter-actions">
        <xforms:var name="control-resources" value="$form-resources/*[name() = $control-name]"/>
    </xsl:template>

    <!-- When copying actions, update references to xforms-ready and fr-form-model -->
    <xsl:template match="@*:event[tokenize(., '\s+') = 'xforms-ready']" mode="filter-actions">
        <xsl:attribute name="{name(.)}" select="(fr:value-except(tokenize(., '\s+'), 'xforms-ready'), 'xforms-model-construct-done')"/>
    </xsl:template>
    <xsl:template match="@*:observer[tokenize(., '\s+') = 'fr-form-model']" mode="filter-actions">
        <xsl:param name="component-id" tunnel="yes"/>
        <xsl:attribute name="{name(.)}" select="(fr:value-except(tokenize(., '\s+'), 'fr-form-model'), concat($component-id, '-model'))"/>
    </xsl:template>

    <!-- Generate one component per section -->
    <xsl:template match="/xhtml:html/xhtml:body//fr:section">

        <!-- ==== Section information ============================================================================== -->

        <xsl:variable name="fr-section" select="." as="element(fr:section)"/>

        <!-- TODO: for now consider any component in the "fr" namespace, but need to do better -->

        <!-- Section id -->
        <xsl:variable name="section-id" select="substring-before(@id, '-section')" as="xs:string"/>

        <!-- Section bind -->
        <xsl:variable name="section-bind" select="$fr-form-model//xforms:bind[@id = concat($section-id, '-bind')]" as="element(xforms:bind)"/>
        <xsl:variable name="section-name" select="$section-bind/((@ref, @nodeset)[1])" as="xs:string"/>

        <!-- Section instance data element -->
        <!-- NOTE: could also gather ancestor-or-self::xforms:bind/@ref and evaluate expression to be more generic -->
        <!-- TODO: What do do with custom data model? -->
        <xsl:variable name="section-data" select="$fr-form-instance/*/*[name() = $section-name]" as="element()"/>

        <!-- NOTE: We should make component ids and names unique, as shown in the commented-out code below. The issue is
             that if we change that, section templates already in use in forms stop working. So we need another
             solution.See: http://forge.ow2.org/tracker/index.php?func=detail&aid=316287&group_id=168&atid=350207 -->
        <xsl:variable name="component-id" select="$section-id" as="xs:string"/>
        <!-- Use section id as component id as section ids are unique -->
        <!--<xsl:variable name="component-id" select="concat(doc('input:parameters')/*/app, '-',  $section-id)" as="xs:string"/>-->

        <!-- Figure out which actions and services are used by the component -->

        <!-- ==== Repeats ========================================================================================== -->

        <xsl:variable name="repeat-ids" select="$fr-section//*[gridOps:isRepeat(.)]/fbf:templateId(controlOps:controlName(@id))"/>
        <xsl:variable name="repeat-templates" select="$fr-form-model/xforms:instance[@id = $repeat-ids]" as="element()*"/>

        <!-- ==== Actions and services ============================================================================= -->

        <!-- Controls under this section which are the source or destination of an action or both -->
        <xsl:variable name="action-controls" select="$fr-section//*[@id = ($all-action-sources, $all-action-destinations)]"/>
        <!-- Unique xforms:action elements which use controls under this section -->
        <xsl:variable name="relevant-actions" select="$actions[@id = distinct-values($action-controls/@id/fr:action-for-id(.)/@id)]"/>
        <!-- Unique service ids used by relevant actions -->
        <xsl:variable name="relevant-service-ids" select="distinct-values($relevant-actions/xforms:action[position() = (2, 3)]/@*:observer/replace(., '(.*)-submission$', '$1'))"/>
        <!-- Unique service instances and submissions used by relevant actions -->
        <xsl:variable name="relevant-services" select="
            $service-instances[replace(@id, '(.*)-instance$', '$1') = $relevant-service-ids] |
            $service-submissions[replace(@id, '(.*)-submission$', '$1') = $relevant-service-ids]"/>

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

                    <!-- Repeat templates if any -->
                    <xsl:copy-of select="$repeat-templates"/>

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

                    <!-- Try to match the current form language, or use the first language available if not found -->
                    <xforms:var name="form-resources"
                                value="instance('fr-form-resources')/(resource[@xml:lang = xxforms:instance('fr-language-instance')], resource[1])[1]" as="element(resource)"/>

                    <!-- Keep track of whether fields should be readonly because the node we're bound to is readonly -->
                    <xforms:instance id="readonly"><readonly/></xforms:instance>

                    <!-- This is also at the top-level in components.xsl -->
                    <xforms:var name="fr-mode" value="xxforms:instance('fr-parameters-instance')/mode"/>
                    <xforms:bind ref="instance('fr-form-instance')" readonly="$fr-mode = ('view', 'pdf', 'email') or instance('readonly') = 'true'"/>

                    <!-- Schema: simply copy so that the types are available locally -->
                    <!-- NOTE: Could optimized to check if any of the types are actually used -->
                    <xsl:copy-of select="$fr-form-model/xs:schema"/>

                    <!-- Services and actions -->
                    <xsl:if test="exists(($relevant-services, $relevant-actions))">
                        <xforms:instance id="fr-service-request-instance" xxforms:exclude-result-prefixes="#all">
                            <request/>
                        </xforms:instance>
                        <xforms:instance id="fr-service-response-instance" xxforms:exclude-result-prefixes="#all">
                            <response/>
                        </xforms:instance>
                        <xsl:apply-templates select="$relevant-services, $relevant-actions" mode="filter-actions">
                            <xsl:with-param name="component-id" tunnel="yes" select="$component-id"/>
                        </xsl:apply-templates>
                    </xsl:if>

                </xforms:model>
            </xbl:implementation>

            <!-- XBL template -->
            <xbl:template>

                <xforms:group appearance="xxforms:internal" xxbl:scope="outer">

                    <!-- Inner group -->
                    <xforms:group appearance="xxforms:internal" xxbl:scope="inner">
                        <!-- Variable pointing to external node -->
                        <xforms:var id="binding" name="binding" as="node()?">
                            <xxforms:sequence select="." xxbl:scope="outer"/>
                        </xforms:var>

                        <xforms:action ev:event="xforms-enabled xforms-value-changed" ev:observer="binding">
                            <!-- Section becomes visible OR binding changes -->
                            <xforms:action>
                                <xforms:action if="$binding/*">
                                    <!-- There are already some nodes, copy them in. This handles the case where existing
                                         external data must be loaded in, for example when editing a form. -->
                                    <xforms:delete ref="instance()/*"/>
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

                        <!-- Propagate readonly -->
                        <xforms:var name="readonly" as="xs:boolean" value="exf:readonly($binding)">
                            <xforms:setvalue ev:event="xforms-enabled xforms-value-changed" ref="instance('readonly')" value="exf:readonly($binding)"/>
                        </xforms:var>

                        <!-- Expose internally a variable pointing to Form Runner resources -->
                        <xforms:var name="fr-resources" as="element()?">
                            <xxforms:sequence select="$fr-resources" xxbl:scope="outer"/>
                        </xforms:var>

                        <xforms:group appearance="xxforms:internal">
                            <!-- Synchronize data with external world upon local value change -->
                            <!-- This assumes the element QName match, or the value is not copied -->
                            <xforms:action ev:event="xforms-value-changed" if="exists($binding/*) and exists(event('xxforms:binding'))">
                                <xforms:var name="source-binding" value="event('xxforms:binding')" as="element()"/>
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
