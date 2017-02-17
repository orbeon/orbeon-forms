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
        name="controls-xforms-action-names"
        select="
            'xforms-value-changed',
            'xforms-enabled',
            'DOMActivate'
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

    <xsl:function name="fr:has-known-action-event" as="xs:boolean">
        <xsl:param name="elem" as="element()"/>
        <xsl:sequence
            select="
                p:split(($elem/@event, $elem/@ev:event)[1]) = (
                    $controls-xforms-action-names,
                    $form-load-fr-action-names,    (: new names :)
                    'xforms-ready',                (: probably no longer needed :)
                    'xforms-model-construct-done'  (: probably no longer needed :)

                )
        "/>
    </xsl:function>

    <xsl:function name="fr:has-action" as="xs:boolean">
        <xsl:param name="model"       as="element(xf:model)"/>
        <xsl:param name="action-name" as="xs:string"/>
        <xsl:sequence
            select="
                exists(
                    $model/xf:action[
                        ends-with(@id, '-binding')
                    ]/
                    xf:action[
                        p:split((@event, @ev:event)[1]) = $action-name
                    ]
                )
        "/>
    </xsl:function>

    <!-- Common implementation of itemset actions (one per model) -->
    <xsl:function name="fr:itemset-action-common-impl" as="element(xf:action)">
        <xf:action event="fr-call-itemset-action">

            <xf:var name="control-name"       value="event('control-name')"/>
            <xf:var name="new-itemset-id"     value="event('new-itemset-id')"/>
            <xf:var name="new-itemset-holder" value="event('new-itemset-holder')"/>

            <xf:var
                name="action-source"
                value="xxf:get-request-attribute('fr-action-source')"/>

            <xf:var
                name="resolved-data-holders"
                value="frf:resolveTargetRelativeToActionSource($action-source, $control-name, false())"/>

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
                <xf:action if="frf:isSingleSelectionControl($element-name) or $element-name = 'autocomplete'">
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
            name="action-bindings"
            select="
                $model/
                    xf:action[
                        ends-with(@id, '-binding')
                    ]"/>

        <xsl:variable
            name="dataset-names"
            select="
                distinct-values(
                    $action-bindings//
                        xf:action[
                            p:has-class('fr-save-to-dataset-action')
                        ]/
                        xf:var[@name = 'dataset-name']/
                        string()
                )"/>

        <xsl:for-each select="$dataset-names">
            <xf:instance id="fr-dataset-{.}"><dataset/></xf:instance>
        </xsl:for-each>

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
        name="action-models"
        select="$fr-form-model, $xbl-models"/>

    <xsl:variable
        name="xbl-models-ids"
        select="$xbl-models/generate-id()"/>

    <xsl:variable
        name="action-models-ids"
        select="$fr-form-model-id, $xbl-models-ids"/>

    <xsl:variable
        name="fr-service-instance-ids"
        select="
            $action-models/
                xf:instance[
                    @id = (
                        'fr-service-request-instance',
                        'fr-service-response-instance'
                    )
                ]/generate-id()"/>

    <xsl:variable
        name="action-bindings"
        select="
            $action-models/
                xf:action[
                    ends-with(@id, '-binding')
                ]"/>

    <xsl:variable
        name="action-bindings-ids"
        select="$action-bindings/generate-id()"/>

    <xsl:variable
        name="service-instances"
        select="
            $action-models/
                xf:instance[
                    p:has-class('fr-service')
                ]"/>

    <xsl:variable
        name="service-instance-ids"
        select="$service-instances/generate-id()"/>

    <xsl:variable
        name="service-submissions-ids"
        select="
            $action-models/
                xf:submission[
                    p:has-class('fr-service')
                ]/generate-id()"/>

    <xsl:variable
        name="models-with-actions-model-ids"
        select="
            distinct-values(
                $action-bindings/
                    (ancestor::xf:model[1])/
                    generate-id()
            )"/>

    <xsl:variable
        name="itemset-actions-elements"
        select="
            $action-bindings//
                xf:action[
                    p:has-class('fr-itemset-action')
                ]"/>

    <xsl:variable
        name="models-with-itemset-actions-models-ids"
        select="
            distinct-values(
                $itemset-actions-elements/
                    (ancestor::xf:model[1])/
                    generate-id()
            )"/>

    <xsl:variable
        name="itemset-actions-ids"
        select="$itemset-actions-elements/generate-id()"/>

    <xsl:variable
        name="itemset-actions-control-ids"
        select="
            for $e in $itemset-actions-elements return
            replace($e/(*:variable | *:var)[@name = 'control-name']/(@value | @select)[1], '^''(.+)''$', '$1-control')"/>

    <!-- Remove service `xf:instance`s (they will be placed in `fr-actions-model`) and existing Form Runner service instances if any -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $action-models-ids
                ]/xf:instance[
                    generate-id() = ($fr-service-instance-ids, $service-instance-ids)
                ]"/>

    <!-- Match and modify service `xf:submission`s -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $action-models-ids
                ]/xf:submission[
                    generate-id() = $service-submissions-ids
                ]">
        <xsl:copy>
            <xsl:copy-of select="@* except (@ref | @instance)"/>
            <xsl:attribute name="ref"         >xxf:instance('fr-service-request-instance')</xsl:attribute>
            <xsl:attribute name="xxf:instance">fr-service-response-instance</xsl:attribute>
        </xsl:copy>
    </xsl:template>

    <!-- Match and modify action `xf:action`s -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $action-models-ids
                ]/xf:action[
                    generate-id() = $action-bindings-ids
                ]">
        <xsl:copy>
            <xsl:copy-of select="@*"/>

            <!-- Main event handler to start the action -->
            <xsl:for-each select="xf:action[fr:has-known-action-event(.)][1]">

                <xsl:variable name="action" select="."/>
                <xsl:variable name="var"    select="$action/xf:var[@name = 'iterate-control-name']"/>

                <xsl:copy>
                    <xsl:copy-of select="$action/@*"/>
                    <!-- Choose to iterate or not on `$iterate-control-name` -->
                    <!-- Also store the absolute id of the source in the request -->
                    <xsl:choose>
                        <xsl:when test="exists($var)">
                            <xsl:copy-of select="$var"/>
                            <xf:action iterate="frf:findRepeatedControlsForTarget(event('xxf:absolute-targetid'), $iterate-control-name)">
                                <xf:action type="xpath">xxf:set-request-attribute('fr-action-source', string(.))</xf:action>
                            </xf:action>
                        </xsl:when>
                        <xsl:otherwise>
                            <xf:action type="xpath">xxf:set-request-attribute('fr-action-source', event('xxf:absolute-targetid'))</xf:action>
                        </xsl:otherwise>
                    </xsl:choose>
                    <xsl:copy-of select="$action/* except $var"/>
                </xsl:copy>
            </xsl:for-each>

            <!-- Request actions -->
            <xsl:for-each select="xf:action[@*:event = 'xforms-submit'][1]">
                <xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xf:var
                        name="request-instance-name"
                        value="{((*:variable | *:var)[@name = 'request-instance-name']/(@value | @select))[1]}"/>
                    <xf:insert
                        ref="xxf:instance('fr-service-request-instance')"
                        origin="saxon:parse(xxf:instance($request-instance-name))"/>

                    <xsl:variable
                        name="request-actions"
                        select=".//xf:action[p:classes() = $request-action-classes]"/>

                    <xsl:if test="exists($request-actions)">
                        <xf:action context="xxf:instance('fr-service-request-instance')">
                            <xsl:for-each select="$request-actions">
                                <xsl:copy>
                                    <xsl:copy-of select="@*"/>
                                    <xsl:choose>
                                        <xsl:when test="p:has-class('fr-set-service-value-action')">
                                            <!-- Keep parameters but override implementation -->
                                            <xsl:copy-of select="(*:variable | *:var)[@name = ('control-name', 'path')]"/>
                                            <!-- Set value -->
                                            <xf:setvalue
                                                ref="$path"
                                                value="
                                                    frf:resolveTargetRelativeToActionSource(
                                                        xxf:get-request-attribute('fr-action-source'),
                                                        $control-name,
                                                        true()
                                                    )"/>
                                        </xsl:when>
                                        <xsl:when test="p:has-class('fr-set-database-service-value-action')">
                                            <!-- Keep parameters but override implementation -->
                                            <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'parameter')]"/>
                                            <!-- Set value and escape single quotes -->
                                            <xf:setvalue
                                                xmlns:sql="http://orbeon.org/oxf/xml/sql"
                                                ref="/sql:config/sql:query/sql:param[xs:integer($parameter)]/(@value | @select)[1]"
                                                value="
                                                    concat(
                                                        '''',
                                                        replace(
                                                            string(
                                                                frf:resolveTargetRelativeToActionSource(
                                                                    xxf:get-request-attribute('fr-action-source'),
                                                                    $control-name,
                                                                    true()
                                                                )
                                                            ),
                                                            '''',
                                                            ''''''
                                                        ),
                                                        ''''
                                                    )"/>
                                        </xsl:when>
                                        <xsl:otherwise/>
                                    </xsl:choose>
                                </xsl:copy>
                            </xsl:for-each>
                        </xf:action>
                    </xsl:if>
                </xsl:copy>
            </xsl:for-each>

            <!-- Response actions -->
            <xsl:for-each select="xf:action[@*:event = 'xforms-submit-done'][1]">
                <xsl:copy>
                    <xsl:copy-of select="@* except @context"/>
                    <xsl:attribute name="context">xxf:instance('fr-service-response-instance')</xsl:attribute>

                    <xsl:variable
                        name="response-actions"
                        select=".//xf:action[p:classes() = $response-action-classes]"/>

                    <xsl:if test="exists($response-actions)">
                        <xsl:for-each select="$response-actions">
                            <xsl:copy>
                                <xsl:copy-of select="@*"/>

                                <xsl:choose>
                                    <xsl:when test="p:has-class('fr-set-control-value-action')">
                                        <!-- Keep parameters but override implementation -->
                                            <xsl:copy-of select="(*:variable | *:var)[@name = ('control-name', 'control-value')]"/>
                                            <!-- Set values (we choose to set all targets returned) -->
                                            <xf:setvalue
                                                iterate="
                                                    frf:resolveTargetRelativeToActionSource(
                                                        xxf:get-request-attribute('fr-action-source'),
                                                        $control-name,
                                                        true()
                                                    )"
                                                ref="."
                                                value="$control-value"/>
                                    </xsl:when>
                                    <xsl:when test="p:has-class('fr-itemset-action')">
                                        <!-- Keep parameters but override implementation -->
                                        <xsl:copy-of select="(*:variable | *:var)[@name = 'control-name']"/>

                                        <xsl:variable
                                            name="resource-items-value"
                                            select="(*:variable | *:var)[@name = 'response-items']/(@value | @select)[1]"/>

                                        <xf:var
                                            xmlns:secure="java:org.orbeon.oxf.util.SecureUtils"
                                            name="new-itemset-id"
                                            value="concat('fr', secure:randomHexId())"/>

                                        <!-- New `<itemset>` outer element (not inserted into the instance yet) -->
                                        <xf:var
                                            name="new-itemset-holder"
                                            value="xf:element('itemset', xf:attribute('id', $new-itemset-id))"/>

                                        <!-- Create `<choices>` elements under the new `<itemset>` outer element -->
                                        <xf:action iterate="xxf:instance('fr-form-resources')/resource">

                                            <xf:var name="fr-lang" value="@xml:lang"/>

                                            <!-- Re-evaluate `$response-items` at each iteration because that can depend on `$fr-lang` -->
                                            <xf:var
                                                name="response-items"
                                                context="instance('fr-service-response-instance')"
                                                value="{$resource-items-value}"/>

                                            <xf:insert
                                                context="$new-itemset-holder"
                                                ref="*"
                                                origin="xf:element('choices')"/>

                                            <xf:var
                                                name="new-choices-holder"
                                                value="$new-itemset-holder/choices[last()]"/>

                                            <!-- Should use a version of `XFormsItemUtils.evaluateItemset()`
                                                 See https://github.com/orbeon/orbeon-forms/issues/3125 -->
                                            <xf:action iterate="$response-items">
                                                <xf:var name="item-label" value="{.//(*:variable | *:var)[@name = ('item-label')]/(@value | @select)[1]}"/>
                                                <xf:var name="item-value" value="{.//(*:variable | *:var)[@name = ('item-value')]/(@value | @select)[1]}"/>
                                                <xf:insert
                                                    context="$new-choices-holder"
                                                    ref="*"
                                                    origin="
                                                        xf:element(
                                                            'item',
                                                            (
                                                                xf:element('label', xs:string($item-label)),
                                                                xf:element('value', xs:string($item-value))
                                                            )
                                                        )"/>
                                            </xf:action>
                                        </xf:action>

                                        <!-- Delegate the rest to common implementation. We should not duplicate much of the code above either, but
                                             the problem is the evaluation of `response-items`, 'item-label', and 'item-value', which must take place
                                             in a context where variables are available, so we cannot use `saxon:evaluate()`. -->
                                        <xf:dispatch name="fr-call-itemset-action" targetid="fr-form-instance">
                                            <xf:property name="control-name"       value="$control-name"/>
                                            <xf:property name="new-itemset-id"     value="$new-itemset-id"/>
                                            <xf:property name="new-itemset-holder" value="$new-itemset-holder"/>
                                        </xf:dispatch>
                                    </xsl:when>
                                    <xsl:when test="p:has-class('fr-save-to-dataset-action')">
                                        <xf:var
                                            name="response"
                                            value="."/>
                                        <xf:insert
                                            context="xxf:instance('fr-service-response-instance')"
                                            ref="instance('fr-dataset-{xf:var[@name = 'dataset-name']}')"
                                            origin="$response"/>
                                    </xsl:when>
                                    <xsl:otherwise/>
                                </xsl:choose>
                            </xsl:copy>
                        </xsl:for-each>
                    </xsl:if>
                </xsl:copy>
            </xsl:for-each>
        </xsl:copy>
    </xsl:template>

    <!-- Match itemsets of controls which can be the target of an itemset action -->
    <xsl:template match="xf:itemset[../@id = $itemset-actions-control-ids]">
        <xsl:copy>
            <!-- Update `@ref` attribute to disable the default itemset if an `@fr:itemsetid` attribute is present -->
            <xsl:if test="@ref">
                <xsl:attribute
                    name="ref"
                    select="concat('if (empty(@fr:itemsetid)) then ', @ref, ' else ()')"/>
           </xsl:if>
            <xsl:apply-templates select="@* except @ref | node()"/>
        </xsl:copy>
        <!-- Add new itemset which kicks in if an `@fr:itemsetid` attribute is present -->
        <xf:itemset ref="id(@fr:itemsetid)[1]/(choices[@xml:lang = xxf:lang()], choices)[1]/item">
            <xf:label ref="label"/>
            <xf:value ref="value"/>
        </xf:itemset>
    </xsl:template>

    <!-- Match models with at least one action -->
    <!-- This does not match the main model handled in components.xsl -->
    <xsl:template match="/xh:html/xh:head//xf:model[generate-id() = $models-with-actions-model-ids]">

        <xsl:variable name="model" select="."/>

        <!-- NOTE: This would look better with XPath 3.1 maps and arrays. -->
        <xsl:variable
            name="has-actions"
            as="xs:boolean+"
            select="
                for $fr-action-name in $form-load-fr-action-names
                    return fr:has-action($model, $fr-action-name)
        "/>

        <!-- If there are any "form load" actions, insert model before main model. This will dispatch the appropriate
             events to the main model as needed. -->
        <xsl:if test="$has-actions = true()">
            <xf:model>
                <xsl:for-each select="1 to count($form-load-xforms-action-names)">
                    <xsl:variable name="p" select="."/>
                    <xsl:if test="$has-actions[$p]">
                        <xf:action event="{$form-load-xforms-action-names[$p]}" observer="{$model/@id}">
                            <xf:dispatch name="{$form-load-fr-action-names[$p]}" targetid="{$model/@id}"/>
                        </xf:action>
                    </xsl:if>
                </xsl:for-each>
            </xf:model>
        </xsl:if>

        <xf:model id="fr-actions-model">
            <xsl:call-template name="action-common-impl"/>
        </xf:model>

        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xsl:if test="generate-id() = $models-with-itemset-actions-models-ids">
                <xsl:copy-of select="fr:itemset-action-common-impl()"/>
            </xsl:if>
            <xsl:copy-of select="fr:common-dataset-actions-impl($model)"/>
        </xsl:copy>
    </xsl:template>

    <!-- Insert service instances -->
    <xsl:template name="action-common-impl">
        <xf:instance id="fr-service-request-instance" xxf:exclude-result-prefixes="#all"><request/></xf:instance>
        <xf:instance id="fr-service-response-instance" xxf:exclude-result-prefixes="#all"><response/></xf:instance>
        <xsl:copy-of select="$service-instances"/>
    </xsl:template>

</xsl:stylesheet>
