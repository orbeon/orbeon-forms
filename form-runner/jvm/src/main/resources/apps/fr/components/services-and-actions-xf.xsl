<!--
  Copyright (C) 2013 Orbeon, Inc.

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
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
    <xsl:import href="actions-common.xsl"/>
    <xsl:import href="synchronize-repeated-content.xsl"/>

    <xsl:function name="fr:action-bindings-2018.2" as="element(fr:action)*">
        <xsl:param name="model" as="element(xf:model)"/>
        <xsl:sequence select="$model/fr:action[@version = '2018.2']"/>
    </xsl:function>

    <xsl:function name="fr:itemset-actions-elements-2018.2" as="element(fr:control-setitems)*">
        <xsl:param name="action-binding" as="element(fr:action)"/>
        <xsl:sequence select="$action-binding//fr:control-setitems"/>
    </xsl:function>

    <!-- Common implementation of itemset actions (one per model) -->
    <xsl:function name="fr:itemset-action-common-impl" as="element(xf:action)">
        <xsl:param name="model-id" as="xs:string"/>

        <xf:action event="fr-call-itemset-action">

            <xf:var name="action-source"      value="event('action-source')"      as="xs:string"/>
            <xf:var name="control-name"       value="event('control-name')"       as="xs:string"/>
            <xf:var name="new-itemset-id"     value="event('new-itemset-id')"     as="xs:string"/>
            <xf:var name="new-itemset-holder" value="event('new-itemset-holder')" as="element(itemset)"/>
            <xf:var name="at"                 value="event('at')"                 as="xs:string?"/>

            <xf:var
                name="resolved-data-holders"
                value="
                    if (empty($at)) then
                        frf:resolveTargetRelativeToActionSource($action-source, $control-name, false(), ())
                    else
                        frf:resolveTargetRelativeToActionSourceFromBinds('{$model-id}', $control-name)[
                            if ($at = 'end') then
                                last()
                            else if ($at = 'start') then
                                1
                            else if ($at castable as xs:integer) then
                                xs:integer($at)
                            else
                                error()
                        ]
            "/>

            <xf:var
                name="choices-count"
                value="count($new-itemset-holder/choices)"/>

            <!--
                If all itemsets are identical, only keep the first one. This avoids duplication when the itemsets
                are not localized. See also: https://github.com/orbeon/orbeon-forms/issues/1191
            -->
            <xf:var
                name="keep-only-one"
                value="
                    $choices-count = 1 or
                    deep-equal(
                        for $ignore in 2 to $choices-count return $new-itemset-holder/choices[1],
                        $new-itemset-holder/choices[position() ge 2]
                    )
                "/>

            <xf:delete
                if="$keep-only-one"
                ref="$new-itemset-holder/choices[position() ge 2]"/>

            <xf:action if="not($keep-only-one)">
                <xf:action iterate="1 to $choices-count">
                    <xf:var name="p" value="."/>
                    <xf:insert
                        context="$new-itemset-holder/choices[$p]"
                        origin="xxf:instance('fr-form-resources')/resource[$p]/@xml:lang"/>
                </xf:action>
            </xf:action>

            <!-- Ensure existence of `<fr:metadata>` -->
            <xf:insert
                if="empty(instance('fr-form-instance')/fr:metadata)"
                context="instance('fr-form-instance')"
                ref="*"
                origin="xf:element('fr:metadata')"/>

            <!-- Insert new `<itemset>` -->
            <xf:insert
                context="instance('fr-form-instance')/fr:metadata"
                ref="*"
                origin="$new-itemset-holder"/>

            <!-- All targeted holders add indirection to itemset -->
            <xf:insert
                iterate="$resolved-data-holders"
                context="."
                origin="xf:attribute('fr:itemsetid', $new-itemset-id)"/>

            <!-- Set map for new itemsets -->
            <xf:var
                name="itemsetmap-node"
                value="frf:findItemsetMapNode($action-source, $control-name, instance('fr-form-instance'))"/>

            <xf:insert
                context="$itemsetmap-node"
                origin="xf:attribute('fr:itemsetmap', frf:addToItemsetMap(@fr:itemsetmap, $control-name, $new-itemset-id))"/>

            <!-- Clear information on descendants -->
            <xf:action iterate="$itemsetmap-node//*[exists(@fr:itemsetmap)]">
                <xf:setvalue
                    ref="."
                    value="frf:removeFromItemsetMap(@fr:itemsetmap, $control-name)"/>
            </xf:action>

            <!-- Garbage-collect unused itemsets and metadata -->
            <xf:action type="xpath">
                frf:garbageCollectMetadataItemsets(instance('fr-form-instance'))
            </xf:action>

            <!-- Filter item values that are out of range -->
            <!-- See: https://github.com/orbeon/orbeon-forms/issues/1019 -->
            <!-- NOTE: We guess whether the control is a select or select1 based on the element name. One exception is
                 autocomplete, which is also a single selection control. -->
            <xf:action>
                <xf:var
                    name="element-name"
                    value="local-name(xxf:control-element(concat($control-name, '-control')))"/>
                <xf:var
                    name="possible-values"
                    value="$new-itemset-holder/choices[1]/item/value/string()"/>
                <xf:action if="frf:isMultipleSelectionControl($element-name)">
                    <xf:action iterate="$resolved-data-holders">
                        <xf:var name="bind" value="."/>
                        <xf:setvalue
                            ref="$bind"
                            value="string-join(xxf:split($bind)[. = $possible-values], ' ')"/>
                    </xf:action>
                </xf:action>
                <xf:action
                    if="not($element-name = 'open-select1') and (
                            frf:isSingleSelectionControl($element-name) or
                            $element-name = 'autocomplete'
                        )">
                    <xf:action iterate="$resolved-data-holders">
                        <xf:var name="bind" value="."/>
                        <xf:setvalue
                            if="not(string($bind) = $possible-values)"
                            ref="$bind"/>
                    </xf:action>
                </xf:action>
            </xf:action>
        </xf:action>
    </xsl:function>

    <!-- Return one dataset instance per unique dataset name appearing in the given model's actions -->
    <xsl:function name="fr:common-dataset-actions-impl" as="element(xf:instance)*">
        <xsl:param name="model" as="element(xf:model)"/>

        <xsl:variable
            name="actions-20182"
            select="$model/fr:action-bindings-2018.2(.)"/>

        <xsl:variable
            name="dataset-names-20182"
            select="$actions-20182//(fr:dataset-write | fr:dataset-clear)/@name/string()"/>

        <xsl:variable
            name="existing-dataset-instances"
            select="$model/xf:instance/@id[starts-with(., 'fr-dataset-')]/substring-after(., 'fr-dataset-')"/>

        <xsl:for-each select="distinct-values($dataset-names-20182)[not(. = $existing-dataset-instances)]">
            <xf:instance id="fr-dataset-{.}"><_/></xf:instance>
        </xsl:for-each>

    </xsl:function>

    <xsl:function name="fr:common-service-actions-impl" as="element(xf:action)*">
        <xsl:param name="model" as="element(xf:model)"/>

        <xsl:variable
            name="actions-20182"
            select="$model/fr:action-bindings-2018.2(.)"/>

        <xsl:variable
            name="all-service-names"
            select="distinct-values($actions-20182//fr:service-call/@service/string())"/>

        <xsl:if test="exists($all-service-names)">
            <xf:action
                event="xforms-submit-error"
                observer="{string-join(for $n in $all-service-names return concat($n, '-submission'), ' ')}"
                type="xpath">
                if (exists(event('fr-action-id'))) then xxf:remove-document-attributes(event('fr-action-id')) else (),
                fr:run-process-by-name('oxf.fr.detail.process', 'action-service-error')
            </xf:action>
        </xsl:if>

        <xsl:if test="exists($actions-20182)">
            <xf:action event="xxforms-action-error" target="#observer" propagate="stop">
                <xf:message level="xxf:log-error" value="concat('Error: ', xxf:trim(event('message')))"/>
                <xf:message level="xxf:log-error" value="event('element')"/>
                <xf:action type="xpath">
                    if (exists(event('fr-action-id'))) then xxf:remove-document-attributes(event('fr-action-id')) else (),
                    fr:run-process-by-name('oxf.fr.detail.process', 'action-action-error')
                </xf:action>
            </xf:action>
        </xsl:if>

    </xsl:function>

    <!--
        Update actions so that the implementation of the actions is up to date. Forms generated with Form Builder
        prior to this change embed a specific implementation of the actions. Forms generated after this change
        do not contain an implementation of the actions and only the parameters are present. See also:

         - annotate.xpl
         - form-to-xbl.xsl
         - https://github.com/orbeon/orbeon-forms/issues/1019
         - https://github.com/orbeon/orbeon-forms/issues/1190
         - https://github.com/orbeon/orbeon-forms/issues/1465
         - https://github.com/orbeon/orbeon-forms/issues/1105
     -->

    <!-- Gather top-level model as well as models within section templates -->
    <xsl:variable
        name="xbl-models"
        select="/xh:html/xh:head/xbl:xbl/xbl:binding/xbl:implementation/xf:model"/>

    <xsl:variable
        name="candidate-action-models"
        select="$fr-form-model, $xbl-models"/>

    <xsl:variable
        name="xbl-models-ids"
        select="$xbl-models/generate-id()"/>

    <xsl:variable
        name="candidate-action-models-ids"
        select="$fr-form-model-id, $xbl-models-ids"/>

    <xsl:variable
        name="fr-service-instance-ids"
        select="
            $candidate-action-models/
                xf:instance[
                    @id = (
                        'fr-service-request-instance',
                        'fr-service-response-instance'
                    )
                ]/generate-id()"/>

    <xsl:variable
        name="action-bindings-2018.2"
        select="$candidate-action-models/fr:action-bindings-2018.2(.)"/>

    <xsl:variable
        name="service-instances"
        select="
            $candidate-action-models/
                xf:instance[
                    p:has-class('fr-service') or p:has-class('fr-database-service')
                ]"/>

    <xsl:variable
        name="service-instance-ids"
        select="$service-instances/generate-id()"/>

    <xsl:variable
        name="service-submissions-ids"
        select="
            $candidate-action-models/
                xf:submission[
                    p:has-class('fr-service') or p:has-class('fr-database-service')
                ]/generate-id()"/>

    <xsl:variable
        name="models-with-actions-model-ids"
        select="
            distinct-values(
                $action-bindings-2018.2/
                    (ancestor::xf:model[1])/
                    generate-id()
            )"/>

    <xsl:variable
        name="itemset-actions-elements-2018.2"
        select="$action-bindings-2018.2/fr:itemset-actions-elements-2018.2(.)"/>

    <xsl:variable
        name="models-with-itemset-actions-models-ids"
        select="
            distinct-values(
                $itemset-actions-elements-2018.2/
                    (ancestor::xf:model[1])/
                    generate-id()
            )"/>

    <!-- Remove service `xf:instance`s (they will be placed in `fr-actions-model`) and existing Form Runner service instances if any -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/xf:instance[
                    generate-id() = ($fr-service-instance-ids, $service-instance-ids)
                ]"/>

    <!-- Match and modify service `xf:submission`s -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/xf:submission[
                    generate-id() = $service-submissions-ids
                ]">
        <xsl:variable
            name="add-request-parameters-to-resource"
            select="@serialization = 'application/x-www-form-urlencoded' and @method = 'get'"/>
        <xsl:variable
            name="library-name"
            select="ancestor::xbl:binding[1]/frf:findAppFromSectionTemplateUri(namespace-uri-for-prefix('component', .))"/>
        <xsl:variable
            name="resource-avt"
            select="
                frf:replaceVarReferencesWithFunctionCallsForAction(
                    @resource,
                    @resource,
                    true(),
                    $library-name,
                    $fr-form-model-vars,
                    'xxf:get-document-attribute(event(''fr-action-id''), ''action-source'')'
                )"/>
        <xsl:variable name="resource" select="concat('$', @id, '-resource')"/>
        <xsl:copy>
            <xsl:copy-of select="@* except (@resource | @ref | @replace | @instance | @xxf:instance | @serialization)"/>
            <xsl:choose>
                <!-- Don't rely on XForms URL parameter serialization, as we want to support parameters where the
                     name is provided through an attribute, e.g. `<param name="my-name">my-value</param>`, this in
                     addition to the "default" parameters where the name is in the element name, e.g.
                     `<my-name>my-value</my-value>`. -->
                <xsl:when test="$add-request-parameters-to-resource">
                    <xsl:attribute name="serialization" select="'none'"/>
                    <xsl:attribute name="resource">{
                        let
                            $resource-without-params := xxf:evaluate-avt('<xsl:value-of select="replace($resource-avt, '''', '''''')"/>'),
                            $parameters-separator    := if (contains($resource-without-params, '?')) then '&amp;' else '?',
                            $parameters-list         :=
                                for $param-element in xxf:instance('fr-service-request-instance')/*
                                return
                                    let
                                        $param-value := $param-element/string(),
                                        $param-name  :=
                                            if (exists($param-element/@name))
                                            then $param-element/@name
                                            else $param-element/local-name()
                                    return
                                        concat(encode-for-uri($param-name), '=', encode-for-uri($param-value)),
                            $parameters-string := string-join($parameters-list, '&amp;')
                        return
                            concat(
                                $resource-without-params,
                                $parameters-separator,
                                $parameters-string
                            )
                    }</xsl:attribute>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of select="@serialization"/>
                    <xsl:attribute name="resource" select="$resource-avt"/>
                    <xsl:attribute name="ref">xxf:instance('fr-service-request-instance')</xsl:attribute>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:attribute name="targetref">xxf:instance('fr-service-response-instance')</xsl:attribute>
            <xsl:choose>
                <!-- See https://github.com/orbeon/orbeon-forms/issues/3945 -->
                <xsl:when test="@replace = 'xxf:binary'">
                    <xsl:copy-of select="@replace"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:attribute name="replace">instance</xsl:attribute>
                </xsl:otherwise>
            </xsl:choose>
            <!--
                An attribute on the submission (so the service) takes precedence over the event.
                As of 2024.1.x, this is not exposed through the "HTTP Service Editor" dialog, so if there
                is an attribute, it's because it has been put there by hand. The idea, though, is that
                until now in the JVM environment all service calls are 'synchronous', but we could mark a
                service explicitly as 'asynchronous'. In the browser environment, all service calls must
                be 'asynchronous'.

                NOTE: Another way would be to entirely disable synchronous calls in the JS environment at the
                XForms level. But are there cases where we still want synchronous calls on `xf:submission`, such as
                when loading resources? For now, we handle services below.
            -->
            <xsl:choose>
                <xsl:when test="@mode = 'synchronous'">
                    <!--
                        This takes precedence over the event, but we force 'asynchronous' anyway in the JS environment.
                        This said, having an explicit 'synchronous' should not happen, see the above comment.
                    -->
                    <xsl:attribute name="mode">{
                    if (fr:is-browser-environment()) then 'asynchronous' else 'synchronous'
                    }</xsl:attribute>
                </xsl:when>
                <xsl:when test="empty(@mode)">
                    <!--
                        Nothing was specified, so we honor the event if it exists, except that in the JS environment
                        we always use 'asynchronous' (see the above comment). If there is no event, we default to
                        'synchronous' in the JVM environment, and to 'asynchronous' in the JS environment.
                    -->
                    <xsl:attribute name="mode">{
                    (
                        if (fr:is-browser-environment()) then 'asynchronous' else (),
                        'asynchronous'[exists(event('fr-async')[. = 'true'])],
                        'synchronous'[exists(event('fr-async')[. = 'false'])],
                        'synchronous'
                    )[1]
                    }</xsl:attribute>
                </xsl:when>
                <xsl:otherwise>
                    <!--
                        This could be 'asynchronous' or an AVT. But we shouldn't support an AVT here in the form definition.
                        So we should be 'asynchronous'. In this case, this takes precedence, so we leave the value as is.
                    -->
                </xsl:otherwise>
            </xsl:choose>
            <!-- For now attribute on submission takes precedence over event. We could easily reverse that. -->
            <xsl:if test="empty(@xxf:response-must-await)">
                <xsl:attribute name="xxf:response-must-await">{
                    (
                        event('fr-response-must-await'),
                        'forever'
                    )[1]
                }</xsl:attribute>
            </xsl:if>
            <!-- https://github.com/orbeon/orbeon-forms/issues/4606 -->
            <xsl:apply-templates select="xf:header"/>
        </xsl:copy>
    </xsl:template>

    <!-- Transform xf:header elements within service submissions to read header values from dynamically-set instance
         first (with a fallback to the default value) -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/xf:submission[
                    generate-id() = $service-submissions-ids
                ]/xf:header">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="xf:name"/>
            <xsl:variable name="header-name" select="normalize-space(xf:name)"/>
            <!-- Read header value from instance, fallback to default value (with support for attribute and text content) -->
            <xf:value value="(xxf:instance('fr-service-headers-instance')/header[@name = '{$header-name}'][xxf:non-blank(.)], {
                if (xf:value/@value) then
                    xf:value/@value
                else
                    concat('''', xf:value/string(), '''')
            })[1]"/>
        </xsl:copy>
    </xsl:template>


    <!-- Match itemsets of controls which can be the target of an itemset action -->
    <xsl:template match="xf:itemset" mode="within-grid">
        <xsl:param name="itemset-action-control-names" tunnel="yes"/>
        <xsl:param name="library-name" as="xs:string?" tunnel="yes"/>
        <xsl:choose>
            <xsl:when test="../@fr:itemsetid-mode = 'always' or (frf:controlNameFromId(../@id) = $itemset-action-control-names and not(../@fr:itemsetid-mode = 'never'))">
                <xsl:copy>
                    <!-- Update `@ref` attribute to disable the default itemset if an `@fr:itemsetid` attribute is present -->
                    <xsl:if test="exists(@ref)">
                        <xsl:attribute
                            name="ref"
                            select="
                                concat(
                                    'if (empty(@fr:itemsetid)) then ',
                                    if (exists(fr:filter/fr:expr)) then
                                        concat(
                                            @ref,
                                            '[boolean(',
                                            frf:replaceVarReferencesWithFunctionCallsFromString(fr:filter/fr:expr, fr:filter/fr:expr, false(), $library-name, $fr-form-model-vars),
                                            ')]'
                                        )
                                    else
                                        @ref,
                                    ' else ()'
                                 )"/>
                    </xsl:if>
                    <xsl:apply-templates select="@* except @ref | node() except fr:filter"/>
                </xsl:copy>
                <!-- Add new itemset which kicks in if an `@fr:itemsetid` attribute is present -->
                <xf:itemset ref="id(@fr:itemsetid)[1]/(choices[@xml:lang = xxf:lang()], choices)[1]/item">
                    <xsl:attribute
                        name="ref"
                        select="
                            concat(
                                'id(@fr:itemsetid)[1]/(choices[@xml:lang = xxf:lang()], choices)[1]/item',
                                if (exists(fr:filter/fr:expr)) then
                                    concat(
                                        '[boolean(',
                                        frf:replaceVarReferencesWithFunctionCallsFromString(fr:filter/fr:expr, fr:filter/fr:expr, false(), $library-name, $fr-form-model-vars),
                                        ')]'
                                    )
                                else
                                    ''
                             )"/>
                    <xf:label ref="label">
                        <!-- Keep mediatype in case it is present so that service can return HTML labels -->
                        <xsl:copy-of select="xf:label/@mediatype"/>
                    </xf:label>
                    <xf:value ref="value"/>
                    <xf:hint ref="hint"/>
                </xf:itemset>
            </xsl:when>
            <xsl:when test="exists(fr:filter/fr:expr)">
                <!-- Only check for the filter -->
                <xsl:copy>
                    <xsl:if test="exists(@ref)">
                        <xsl:attribute
                            name="ref"
                            select="
                                concat(
                                    @ref,
                                    '[boolean(',
                                    frf:replaceVarReferencesWithFunctionCallsFromString(fr:filter/fr:expr, fr:filter/fr:expr, false(), $library-name, $fr-form-model-vars),
                                    ')]'
                                )"/>
                    </xsl:if>
                    <xsl:apply-templates select="@* except @ref | node() except fr:filter"/>
                </xsl:copy>
            </xsl:when>
            <xsl:otherwise>
                <xsl:next-match/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Match models with at least one action -->
    <!-- This does NOT match the main model handled in components.xsl -->
    <xsl:template match="/xh:html/xh:head//xf:model[generate-id() = $models-with-actions-model-ids]">

        <xsl:variable name="model" select="."/>

        <xf:model id="fr-actions-model">
            <xsl:call-template name="action-common-impl">
                <xsl:with-param name="model" select="$model"/>
            </xsl:call-template>
        </xf:model>

        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xsl:if test="generate-id() = $models-with-itemset-actions-models-ids">
                <xsl:copy-of select="fr:itemset-action-common-impl($model/@id/string())"/>
            </xsl:if>
            <xsl:copy-of select="fr:common-dataset-actions-impl($model)"/>
            <xsl:copy-of select="fr:common-service-actions-impl($model)"/>
        </xsl:copy>
    </xsl:template>

    <!-- Insert service instances -->
    <xsl:template name="action-common-impl">
        <xsl:param name="model" as="element(xf:model)"/>

        <xf:instance id="fr-service-request-instance"  xxf:exclude-result-prefixes="#all"><request/></xf:instance>
        <xf:instance id="fr-service-response-instance" xxf:exclude-result-prefixes="#all"><response/></xf:instance>
        <xf:instance id="fr-service-headers-instance"  xxf:exclude-result-prefixes="#all"><headers/></xf:instance>

        <xsl:copy-of select="$model/xf:instance[p:has-class('fr-service') or p:has-class('fr-database-service')]"/>

        <!-- For "continue action" confirmation dialog -->
        <xf:action event="fr-positive" target="#observer">
            <xf:dispatch
                name="{{substring-after(event('context'), '|')}}"
                targetid="{$model/@id}">
                <xf:property name="fr-action-id" value="substring-before(event('context'), '|')"/>
            </xf:dispatch>
        </xf:action>

        <xf:action event="fr-negative" target="#observer" type="xpath">
            xxf:remove-document-attributes(substring-before(event('context'), '|'))
        </xf:action>

    </xsl:template>

</xsl:stylesheet>
