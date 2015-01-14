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
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:exf="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
        xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
        xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- Namespace URI e.g. http://orbeon.org/oxf/xml/form-builder/component/APP/FORM -->
    <xsl:variable name="component-namespace" as="xs:string"
                  select="string-join(('http://orbeon.org/oxf/xml/form-builder/component', doc('input:parameters')/*/app, doc('input:parameters')/*/form), '/')"/>

    <!-- Global stuff -->
    <xsl:variable name="fr-form-model"         select="/xh:html/xh:head//xf:model[@id = 'fr-form-model']" as="element(xf:model)*"/>
    <xsl:variable name="fr-form-instance"      select="$fr-form-model/xf:instance[@id = 'fr-form-instance']"    as="element(xf:instance)"/>
    <xsl:variable name="fr-resources-instance" select="$fr-form-model/xf:instance[@id = 'fr-form-resources']"   as="element(xf:instance)"/>
    <xsl:variable name="fr-metadata-instance"  select="$fr-form-model/xf:instance[@id = 'fr-form-metadata']"    as="element(xf:instance)*"/>

    <!-- Actions and services -->
    <!-- NOTE: Actions and services are implemented, for historical reasons, as XForms instances, submissions, and action
         blocks. This means that we must analyze them to try to make sense of them, and this is a bit fragile. In the
         future, actions should be described in a more declarative format. See also:
         http://wiki.orbeon.com/forms/projects/form-runner-builder/improved-actions-and-services-format
     -->
    <xsl:variable name="actions" select="$fr-form-model/xf:action[ends-with(@id, '-binding')]"/>
    <xsl:variable name="service-instances" select="$fr-form-model/xf:instance[p:classes() = ('fr-service', 'fr-database-service')]"/>
    <xsl:variable name="service-submissions" select="$fr-form-model/xf:submission[p:classes() = ('fr-service', 'fr-database-service')]"/>

    <!-- Distinct source ids for the given action -->
    <!-- NOTE: Source can also be a model -->
    <xsl:function name="fr:action-sources" as="xs:string*">
        <xsl:param name="action" as="element(xf:action)"/>
        <xsl:sequence select="distinct-values($action/xf:action[1]/@*:observer/p:split())"/>
    </xsl:function>

    <!-- Distinct destination ids for the given action -->
    <xsl:function name="fr:action-destinations" as="xs:string*">
        <xsl:param name="action" as="element(xf:action)"/>
        <xsl:sequence select="distinct-values($action//(*:variable | *:var)[@name = 'control-name']/(@value, @select)/replace(., '''([^..]*)''', '$1-control'))"/>
    </xsl:function>

    <!-- Distinct action elements for which the given id is a source or destination or both -->
    <xsl:function name="fr:action-for-id" as="element(xf:action)*">
        <xsl:param name="id" as="xs:string"/>
        <xsl:sequence select="$actions[$id = distinct-values((fr:action-sources(.), fr:action-destinations(.)))]"/>
    </xsl:function>

    <xsl:function name="fr:title" as="xs:string">
        <xsl:param name="lang" as="xs:string"/>
        <xsl:param name="default" as="xs:string"/>
        <xsl:value-of select="(normalize-space($fr-metadata-instance/*/title[@xml:lang = $lang])[. != ''], $default)[1]"/>
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
                <display-name lang="en"><xsl:value-of select="fr:title('en', 'Section Templates')"/></display-name>
                <display-name lang="fr"><xsl:value-of select="fr:title('fr', 'Modèles de sections')"/></display-name>
                <display-name lang="ru"><xsl:value-of select="fr:title('ru', 'Шаблоны разделов')"/></display-name>
                <display-name lang="de"><xsl:value-of select="fr:title('de', 'Templates der Abschnitte')"/></display-name>
                <display-name lang="it"><xsl:value-of select="fr:title('it', 'Templates della sezione')"/></display-name>
                <icon lang="en">
                    <small-icon>/forms/orbeon/builder/images/input.png</small-icon>
                    <large-icon>/forms/orbeon/builder/images/input.png</large-icon>
                </icon>
            </metadata>

            <xsl:apply-templates select="/xh:html/xh:body//fr:section[empty(ancestor::fr:section)]"/>
        </xbl:xbl>
    </xsl:template>

    <xsl:function name="fr:value-except" as="xs:anyAtomicType*">
      <xsl:param name="arg1" as="xs:anyAtomicType*"/>
      <xsl:param name="arg2" as="xs:anyAtomicType*"/>
      <xsl:sequence select="distinct-values($arg1[not(. = $arg2)])"/>
    </xsl:function>

    <!-- We only want to run xxf:default in "new" mode to conform to the semantic of the top-level form. So we rewrite
         xxf:default expressions to test on the mode. See: https://github.com/orbeon/orbeon-forms/issues/786 -->
    <xsl:template match="xf:bind/@xxf:default">
        <xsl:attribute name="{name()}" select="concat('if ($fr-mode = (''new'', ''test'')) then (', ., ') else .')"/>
    </xsl:template>

    <!-- When copying actions, update references to xforms-ready and fr-form-model -->
    <!-- The action implementation is handled in actions.xsl. -->
    <xsl:template match="@*:event[p:split() = 'xforms-ready']" mode="filter-actions">
        <xsl:attribute name="{name(.)}" select="(fr:value-except(p:split(), 'xforms-ready'), 'xforms-model-construct-done')"/>
    </xsl:template>
    <xsl:template match="@*:observer[p:split() = 'fr-form-model']" mode="filter-actions">
        <xsl:param name="model-id" tunnel="yes"/>
        <xsl:attribute name="{name(.)}" select="(fr:value-except(p:split(), 'fr-form-model'), $model-id)"/>
    </xsl:template>

    <!-- Generate one component per top-level section -->
    <xsl:template match="/xh:html/xh:body//fr:section">

        <!-- ==== Section information ============================================================================== -->

        <xsl:variable name="fr-section" select="." as="element(fr:section)"/>

        <!-- TODO: for now consider any component in the "fr" namespace, but need to do better -->

        <!-- Section name -->
        <xsl:variable name="section-name" select="fbf:controlNameFromId(@id)" as="xs:string"/>

        <!-- Section bind -->
        <xsl:variable name="section-bind" select="$fr-form-model//xf:bind[@id = concat($section-name, '-bind')]" as="element(xf:bind)"/>
        <xsl:variable name="section-name" select="$section-bind/((@ref, @nodeset)[1])" as="xs:string"/>
        <xsl:variable name="model-id"     select="concat($section-name, '-model')" as="xs:string"/>

        <!-- Section instance data element -->
        <!-- NOTE: could also gather ancestor-or-self::xf:bind/@ref and evaluate expression to be more generic -->
        <xsl:variable name="section-data" select="$fr-form-instance/*/*[name() = $section-name]" as="element()"/>

        <!-- Binding ids must be unique as the xbl:xbl of the section templates are embedded into the published forms.
             So make the ids unique. See https://github.com/orbeon/orbeon-forms/issues/142 -->
        <xsl:variable name="binding-id" select="concat(doc('input:parameters')/*/app, '-',  $section-name)" as="xs:string"/>

        <!-- Figure out which actions and services are used by the component -->

        <!-- ==== Repeats ========================================================================================== -->

        <xsl:variable name="repeat-ids" select="$fr-section//*[fbf:isRepeat(.)]/fbf:templateId(fbf:controlNameFromId(@id))"/>
        <xsl:variable name="repeat-templates" select="$fr-form-model/xf:instance[@id = $repeat-ids]" as="element()*"/>

        <!-- ==== Actions and services ============================================================================= -->

        <!-- Controls under this section which are the source or destination of an action or both -->
        <xsl:variable name="action-controls" select="$fr-section//*[@id = ($all-action-sources, $all-action-destinations)]"/>
        <!-- Unique xf:action elements which use controls under this section -->
        <xsl:variable name="relevant-actions" select="$actions[@id = distinct-values($action-controls/@id/fr:action-for-id(.)/@id)]"/>
        <!-- Unique service ids used by relevant actions -->
        <xsl:variable name="relevant-service-ids" select="distinct-values($relevant-actions/xf:action[position() = (2, 3)]/@*:observer/replace(., '(.*)-submission$', '$1'))"/>
        <!-- Unique service instances and submissions used by relevant actions -->
        <xsl:variable name="relevant-services" select="
            $service-instances[replace(@id, '(.*)-instance$', '$1') = $relevant-service-ids] |
            $service-submissions[replace(@id, '(.*)-submission$', '$1') = $relevant-service-ids]"/>

        <!-- Create binding for the section/grid as a component -->
        <!-- NOTE: In component|foo-section, the namespace already contains the app name. So it's not necessary to
             include the app name in the local name. See https://github.com/orbeon/orbeon-forms/issues/142 -->
        <xbl:binding
                id="{$binding-id}-component"
                element="component|{$section-name}"
                class="fr-section-component">

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

                <!-- Control template -->
                <templates>
                    <!-- Type if any -->
                    <xsl:if test="$section-bind/@type">
                        <bind type="{$section-bind/@type}"/>
                    </xsl:if>
                    <view>
                        <!-- NOTE: Element doesn't have LHHA elements for now -->
                        <xsl:element name="component:{$section-name}" namespace="{$component-namespace}"/>
                    </view>
                </templates>
            </metadata>

            <!-- XBL implementation -->
            <xbl:implementation>
                <xf:model id="{$model-id}">
                    <!-- Copy schema attribute if present -->
                    <xsl:copy-of select="$fr-form-model/(@schema | @xxf:custom-mips)"/>

                    <!-- Section data model -->
                    <xf:instance id="fr-form-instance" xxbl:mirror="true">
                        <empty/>
                    </xf:instance>

                    <!-- Template -->
                    <xf:instance id="fr-form-template">
                        <xsl:copy-of select="$section-data"/>
                    </xf:instance>

                    <!-- Repeat templates if any -->
                    <xsl:copy-of select="$repeat-templates"/>

                    <!-- Section constraints -->
                    <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                        <xsl:apply-templates select="$section-bind/xf:bind"/>
                    </xf:bind>

                    <!-- Sections resources: include resource for section and for all leaf controls -->
                    <xf:instance id="fr-form-resources">
                        <resources xmlns="">
                            <xsl:variable name="resources" select="$fr-resources-instance/*"/>
                            <xsl:for-each select="$resources/resource">
                                <xsl:variable name="lang" select="@xml:lang" as="xs:string"/>

                                <resource xml:lang="{$lang}">
                                    <xsl:copy-of select="fbf:iterateSelfAndDescendantBindsResourceHoldersXPath($section-bind, $lang, $resources)"/>
                                </resource>
                            </xsl:for-each>
                        </resources>
                    </xf:instance>

                    <!-- Keep track of whether fields should be readonly because the node we're bound to is readonly -->
                    <xf:instance id="readonly"><readonly/></xf:instance>

                    <!-- This is also at the top-level in components.xsl -->
                    <xf:var name="fr-params" value="xxf:instance('fr-parameters-instance')"/>
                    <xf:var name="fr-mode"   value="$fr-params/mode"/>
                    <!-- NOTE: Make content readonly in orbeon/builder too because if the form author enters initial
                         data or adds a repeat iteration into the section template, that data will be either incorrect
                         or lost if the toolbox is reloaded. A better fix can be considered, but more complex at the
                         Form Builder level. -->
                    <xf:bind
                        ref="instance('fr-form-instance')"
                        readonly="$fr-mode = ('view', 'pdf', 'email')
                               or instance('readonly') = 'true'
                               or ($fr-params/app = 'orbeon' and $fr-params/form = 'builder')"/>

                    <!-- Schema: simply copy so that the types are available locally -->
                    <!-- NOTE: Could optimized to check if any of the types are actually used -->
                    <xsl:copy-of select="$fr-form-model/xs:schema"/>

                    <!-- Section becomes relevant -->
                    <xf:action ev:event="xforms-model-construct-done">
                        <!-- At this point, the outside instance has already been copied inside. If there are no child
                             elements, it means that the data has not yet been copied out (the section could also be
                             empty, but this is not allowed currently). See also:
                             https://github.com/orbeon/orbeon-forms/issues/786 -->
                        <xf:action if="empty(instance()/*)">
                            <!-- Copy again from the template (the mirror copies it out too) -->
                            <xf:insert context="instance()" origin="instance('fr-form-template')/*"/>>
                            <!-- RRR with defaults -->
                            <xf:rebuild/>
                            <xf:recalculate xxf:defauls="true"/>
                            <xf:revalidate/>
                        </xf:action>
                    </xf:action>

                    <!-- Services and actions -->
                    <xsl:if test="exists(($relevant-services, $relevant-actions))">
                        <xf:instance id="fr-service-request-instance" xxf:exclude-result-prefixes="#all">
                            <request/>
                        </xf:instance>
                        <xf:instance id="fr-service-response-instance" xxf:exclude-result-prefixes="#all">
                            <response/>
                        </xf:instance>
                        <xsl:apply-templates select="$relevant-services, $relevant-actions" mode="filter-actions">
                            <xsl:with-param name="model-id" tunnel="yes" select="$model-id"/>
                        </xsl:apply-templates>
                    </xsl:if>

                </xf:model>
            </xbl:implementation>

            <!-- XBL template -->
            <xbl:template>
                <!-- Point to the context of the current element.
                     NOTE: FB doesn't place a @ref. -->
                <xf:var name="context" id="context" value="xxf:binding-context('{$binding-id}-component')"/>

                <!-- Propagate readonly of containing section -->
                <xf:var name="readonly" as="xs:boolean" value="exf:readonly($context)">
                    <xf:setvalue ev:event="xforms-enabled xforms-value-changed" ref="instance('readonly')" value="exf:readonly($context)"/>
                </xf:var>

                <!-- Expose internally a variable pointing to Form Runner resources -->
                <xf:var name="fr-resources" as="element()?">
                    <xxf:value value="$fr-resources" xxbl:scope="outer"/>
                </xf:var>

                <!-- Try to match the current form language, or use the first language available if not found -->
                <!-- NOTE: Put this in the view, as the variable doesn't update properly if in the model.
                     See: https://github.com/orbeon/orbeon-forms/issues/738 -->
                <!-- NOTE: This won't be needed once all code uses xxf:r(). -->
                <xf:var name="form-resources"
                        value="instance('fr-form-resources')/(resource[@xml:lang = xxf:instance('fr-language-instance')], resource[1])[1]" as="element(resource)"/>

                <xf:group appearance="xxf:internal">
                    <!-- Copy section content -->
                    <xsl:copy-of select="$fr-section/*"/>
                </xf:group>
            </xbl:template>
        </xbl:binding>


    </xsl:template>

</xsl:stylesheet>