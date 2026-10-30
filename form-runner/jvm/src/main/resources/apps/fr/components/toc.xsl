<xsl:stylesheet
        version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
        xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:map="http://www.w3.org/2005/xpath-functions/map"
        xmlns:array="http://www.w3.org/2005/xpath-functions/array">

    <xsl:template match="text()" mode="fr-toc-sections"/>
    <xsl:template match="*" mode="fr-toc-sections">
        <xsl:apply-templates mode="fr-toc-sections"/>
    </xsl:template>

    <!-- TOC: handle section -->
    <xsl:template match="fr:section" mode="fr-toc-sections">

        <xsl:param name="is-wizard"              tunnel="yes" as="xs:boolean"/>
        <xsl:param name="static-app"             tunnel="yes" as="xs:string"/>
        <xsl:param name="static-form"            tunnel="yes" as="xs:string"/>
        <xsl:param name="static-subsections-nav" tunnel="yes" as="xs:boolean"/>
        <xsl:param name="static-subsections-toc" tunnel="yes" as="xs:string"/>
        <xsl:param name="static-initial-focus"   tunnel="yes" as="xs:boolean?"/>

        <xsl:variable
            name="static-current-section"
            select="."/>
        <xsl:variable
            name="static-section-id"
            select="$static-current-section/@id"/>
        <xsl:variable
            name="static-is-top-level-section"
            select="empty($static-current-section/ancestor::fr:section)"/>
        <xsl:variable
            name="static-section-has-html-label"
            select="$static-current-section/xf:label/@mediatype = 'text/html'"/>
        <xsl:variable
            name="static-top-level-section-id"
            select="($static-current-section/ancestor-or-self::fr:section/@id)[1]"/>
        <xsl:variable
            name="static-top-level-section-case-id"
            select="concat($static-top-level-section-id, '-case')"/>
        <xsl:variable
            name="use-paging"
            select="$static-current-section/@fr-use-wizard-repeat-paging = 'true'"/>

        <!-- Propagate binding so that entry for section disappears if the section is non-relevant -->
        <xsl:element name="xf:{if ($use-paging) then 'repeat' else 'group'}">

            <xsl:choose>
                <xsl:when test="$use-paging">
                    <xsl:variable
                        name="repeat-expression"
                        as="xs:string?"
                        select="
                            (
                                $static-current-section/@ref,
                                $static-current-section/@nodeset,
                                for $b in $static-current-section/@bind
                                    return
                                        concat(
                                            'bind(''',
                                            $b,
                                            ''')'
                                        )
                            )[1]"/>

                    <xsl:attribute
                        name="ref"
                        select="concat($repeat-expression, '/*')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of select="@model | @context | @bind | @ref"/>
                </xsl:otherwise>
            </xsl:choose>

            <xf:var name="fr-toc-top-level-section-available">
                <xsl:choose>
                    <xsl:when test="$is-wizard">
                        <xxf:value
                            value="
                                exists(
                                    index-of(
                                        xxf:split($fr-wizard-available-top-level-sections),
                                        '{frf:controlNameFromIdOpt($static-top-level-section-id)}'
                                    )
                                )"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xxf:value value="true()"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xf:var>

            <!--
                TODO:
                    - `exists(@fr:section-status)` does not work!
                    - `exists(@fr:section-status/string())` works partially (see #7494)
                    - the workaround below works
            -->
            <xf:var
                name="fr-toc-top-level-section-visited"
                value="exists(@*[namespace-uri() = 'http://orbeon.org/oxf/xml/form-runner' and local-name() = 'section-status'])"/>

            <xf:var
                name="fr-toc-top-level-section-tokens"
                value="xxf:split(@fr:section-status)"/>

            <xsl:choose>
                <xsl:when test="not($is-wizard)">
                    <xf:var
                        name="fr-toc-top-level-section-active"
                        value="false()"/>
                    <xf:var
                        name="fr-toc-section-active"
                        value="false()"/>
                </xsl:when>
                <xsl:when test="$use-paging">
                    <xf:var
                        name="fr-toc-top-level-section-active"
                        value="
                            not($fr-wizard-separate-toc-mode) and
                            $fr-wizard-current-top-level-case-id = '{$static-section-id}-case' and
                            xxf:repeat-position() = $fr-wizard-local/current-index/data(.)"/>
                    <xf:var
                        name="fr-toc-section-active"
                        value="
                            not($fr-wizard-separate-toc-mode) and
                            $fr-wizard-current-nav-case-id = '{$static-section-id}-case' and
                            xxf:repeat-position() = $fr-wizard-local/current-index/data(.)"/>
                </xsl:when>
                <xsl:otherwise>
                    <xf:var
                        name="fr-toc-top-level-section-active"
                        value="
                            not($fr-wizard-separate-toc-mode) and
                            $fr-wizard-current-top-level-case-id = '{$static-section-id}-case'"/>
                    <xf:var
                        name="fr-toc-section-active"
                        value="
                            not($fr-wizard-separate-toc-mode) and
                            $fr-wizard-current-nav-case-id = '{$static-section-id}-case'"/>
                </xsl:otherwise>
            </xsl:choose>

            <xsl:variable name="section-label-expr" as="xs:string">
                <xsl:choose>
                    <xsl:when test="$use-paging">
                        <xsl:choose>
                            <xsl:when test="exists($static-current-section/fr:iteration-label)">
                                <xsl:value-of select="
                                    concat(
                                        'xxf:value(''',
                                        $static-section-id,
                                        '-repeater-iteration-label'', false())[xxf:repeat-position()]'
                                    )"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="
                                    concat(
                                        'concat(xxf:label(''',
                                        $static-section-id,
                                        '''), '' '', xxf:repeat-position())'
                                    )"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="
                            concat(
                                'xxf:label(''',
                                $static-section-id,
                                ''')'
                            )"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:variable>

            <!-- Short label -->
            <xsl:variable
                    name="static-section-has-short-label"
                    select="exists($static-current-section/fr:short-label)"/>
            <xsl:variable
                    name="static-section-has-html-short-label"
                    select="$static-current-section/fr:short-label/@mediatype = 'text/html'"/>
            <xsl:variable
                    name="short-label-expr"
                    select="concat('xxf:value(''', $static-section-id,  '-short-label'')')"/>

            <xh:li>

                <xsl:attribute name="class">{
                    'disabled'           [not($fr-toc-top-level-section-available)],
                    'active'             [$fr-toc-section-active or $fr-toc-top-level-section-active],
                    'started'            [$fr-toc-top-level-section-visited],
                    $fr-toc-top-level-section-tokens
                    <xsl:if test="$is-wizard">
                        ,
                        'first-page'         [$fr-wizard-relevant-top-level-case-ids[1]      = '<xsl:value-of select="$static-section-id"/>-case'],
                        'last-top-level-page'[$fr-wizard-relevant-top-level-case-ids[last()] = '<xsl:value-of select="$static-section-id"/>-case'],
                        'last-page'          [$fr-wizard-relevant-nav-case-ids[last()]       = '<xsl:value-of select="$static-section-id"/>-case']
                    </xsl:if>
                }</xsl:attribute>
                <xsl:attribute name="aria-current">{
                    if ($fr-toc-section-active) then 'page' else 'false'
                }</xsl:attribute>
                <xsl:attribute name="aria-disabled">{
                    if (not($fr-toc-top-level-section-available)) then 'true' else 'false'
                }</xsl:attribute>

                <xsl:if test="$static-section-has-html-label and $is-wizard">
                    <xf:var name="inner-section-label" xxbl:scope="inner">
                        <xxf:value xxbl:scope="outer" value="{$section-label-expr}"/>
                    </xf:var>
                    <xf:var name="fr-toc-section-hint">
                        <xxf:value xxbl:scope="inner" value="frf:removeHtmlTags($inner-section-label)"/>
                    </xf:var>
                </xsl:if>

                <xf:trigger appearance="minimal">
                    <xf:label>
                        <xh:span class="fr-toc-title">
                            <xsl:choose>
                                <xsl:when test="$static-section-has-short-label">
                                    <xf:output value="{$short-label-expr}">
                                        <xsl:if test="$static-section-has-html-short-label"><xsl:attribute name="mediatype">text/html</xsl:attribute></xsl:if>
                                    </xf:output>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xf:output value="{$section-label-expr}">
                                        <xsl:if test="$static-section-has-html-label"><xsl:attribute name="mediatype">text/html</xsl:attribute></xsl:if>
                                    </xf:output>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xh:span>
                        <xsl:choose>
                            <xsl:when test="$is-wizard">
                                <xh:span class="fr-toc-edit {{'xforms-hidden'[not($fr-wizard-local/section-status/data(.))]}}"><xf:output value="xxf:r('components.wizard.edit', '|fr-fr-resources|')"/></xh:span>
                                <xsl:if test="$static-is-top-level-section">
                                    <!-- NOTE: Hide labels when not in `separate-toc` mode as they take a lot of space -->
                                    <xh:span class="fr-toc-status {{'sr-only'[not($fr-wizard-local/section-status/data(.))]}} label{{
                                        if ($fr-toc-top-level-section-tokens = 'invalid') then
                                            ' label-important fr-section-status-errors'
                                        else if ($fr-toc-top-level-section-tokens = 'incomplete') then
                                            ' label-warning fr-section-status-incomplete'
                                        else if ($fr-toc-top-level-section-visited) then
                                            ' label-success fr-section-status-complete'
                                        else
                                            ' fr-section-status-not-started'
                                    }}"><xf:output value="
                                            xxf:r(
                                                if ($fr-toc-top-level-section-tokens = 'invalid') then
                                                    'components.wizard.errors'
                                                else if ($fr-toc-top-level-section-tokens = 'incomplete') then
                                                    'components.wizard.incomplete'
                                                else if ($fr-toc-top-level-section-visited) then
                                                    'components.wizard.complete'
                                                else
                                                    'components.wizard.not-started',
                                                '|fr-fr-resources|'
                                            )"/></xh:span>
                                </xsl:if>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- TODO: we could optionally show section status in non-wizard mode -->
                            </xsl:otherwise>
                        </xsl:choose>
                    </xf:label>

                    <!-- The hint shows the full label, which is useful when we have a short label, or when full label
                         gets truncated by ellipses. We remove the markup as hint ends on `<button title="…">`,
                         which doesn't support HTML. -->
                    <xf:hint
                        value="{
                            if ($static-section-has-html-label and $is-wizard) then
                                '$fr-toc-section-hint'
                            else if ($static-section-has-html-label) then
                                concat(
                                    'frf:removeHtmlTags(',
                                    $section-label-expr,
                                    ')'
                                )
                            else
                                $section-label-expr
                        }"/>

                    <!-- DOMActivate handler which depends on `$static-validation-mode` -->
                    <xf:action event="DOMActivate">
                        <xf:dispatch
                            name="fr-wizard-update-validity"
                            targetid="fr-wizard-update-validity"/>
                        <xf:action if="$fr-toc-top-level-section-available">

                            <!-- Figure out the nav section id based on whether we support subsection navigation AND where in the hierarchy
                                 the clicked TOC section is. -->
                            <xsl:variable
                                name="static-nav-section-id"
                                select="
                                    if ($static-subsections-nav) then
                                        (
                                            if ($static-is-top-level-section) then
                                                (: Current section is a top-level section. Try first subsection or grid if any,
                                                   otherwise the top-level section itself, which means that there are no subsections
                                                   or grids. Unfortunately, there is some logic duplication between here and the
                                                   computation of `$static-nav-sections-or-grids` above. :)
                                                (
                                                    $static-current-section[
                                                        not(@subsections-nav = 'false') and
                                                        not(frf:isRepeat(.))            and
                                                        exists(fr:section)
                                                    ]/(fr:section | fr:grid)[1],
                                                    $static-current-section
                                                )[1]
                                            else
                                                (: Current section is a subsection. Take first subsection from the top, as only two levels of
                                                   section navigation are supported. :)
                                                ($static-current-section/ancestor-or-self::fr:section)[2]
                                        )/(
                                            @id,
                                            concat(generate-id(), '-wizard-grid')
                                        )[1]
                                    else
                                        $static-top-level-section-id
                            "/>

                            <xsl:if test="$is-wizard">
                                <!-- Remember where we are toggling to, toggle... -->
                                <xf:toggle
                                    case="{$static-nav-section-id}-case"/>
                            </xsl:if>

                            <xsl:if test="$use-paging">
                                <xf:var name="fr-toc-new-index" value="xxf:repeat-position()"/>
                                <xsl:if test="$is-wizard">
                                    <xf:setvalue
                                        ref="$fr-wizard-local/current-index"
                                        value="$fr-toc-new-index"/>
                                </xsl:if>
                                <xf:setindex
                                    repeat="{$static-section-id}-repeater-repeat"
                                    index="$fr-toc-new-index"/>
                            </xsl:if>

                            <!-- ...and focus on specific subsection -->
                            <xsl:choose>
                                <xsl:when test="$is-wizard">
                                    <xsl:if test="($static-initial-focus, p:property(string-join(('oxf.fr.detail.initial-focus', $static-app, $static-form), '.')))[1]">
                                        <xf:setfocus
                                            control="{$static-section-id}"
                                            includes="{{$fr-wizard-focus-includes}}"
                                            excludes="{{$fr-wizard-focus-excludes}}"/>
                                    </xsl:if>
                                </xsl:when>
                                <xsl:otherwise>
                                    <!-- Not all readonly controls support focus, so we try to scroll instead  -->
                                    <xf:action type="javascript">
                                        <xf:param
                                            name="sectionId"
                                            value="
                                                xxf:client-id('{
                                                    $static-section-id
                                                }{
                                                    '-repeater-iteration-label'[
                                                        exists($static-current-section/fr:iteration-label)
                                                    ]
                                                }')"/>
                                        <xf:body>
                                            const elem = window.document.getElementById(sectionId);
                                            if (elem)
                                                elem.scrollIntoView();
                                        </xf:body>
                                    </xf:action>
                                </xsl:otherwise>
                            </xsl:choose>

                            <xsl:if test="$is-wizard">
                                <!-- Show body if needed -->
                                <xf:action if="$fr-wizard-local/separate-toc/data(.)">
                                    <xf:setvalue
                                        ref="$fr-wizard-local/show-toc"
                                        value="false()"/>

                                    <!-- See https://github.com/orbeon/orbeon-forms/issues/3668 -->
                                    <xf:action if="not($fr-wizard-local/show-body/data(.))">

                                        <xf:dispatch name="fr-section-shown" targetid="fr-wizard" xxbl:scope="inner">
                                            <xf:property name="section-binding" value="xxf:binding($fr-wizard-current-top-level-section-id)" xxbl:scope="outer"/>
                                            <xf:property name="section-name"    value="$current-top-level-section-name"/>
                                            <xf:property name="validate"        value="$local/validate/data(.)"/>
                                            <xf:property name="separate-toc"    value="$local/separate-toc/data(.)"/>
                                        </xf:dispatch>

                                        <xf:setvalue
                                            ref="$fr-wizard-local/show-body"
                                            value="true()"/>
                                    </xf:action>

                                </xf:action>
                            </xsl:if>
                        </xf:action>
                    </xf:action>
                </xf:trigger>

                <xsl:choose>
                    <xsl:when test="$static-subsections-toc = 'all' and exists(.//fr:section)">
                        <xh:div role="navigation">
                            <xh:ul class="nav">
                                <xsl:apply-templates mode="fr-toc-sections"/>
                            </xh:ul>
                        </xh:div>
                    </xsl:when>
                    <xsl:when test="$static-subsections-toc = 'active' and exists(.//fr:section)">
                        <xh:div role="navigation">
                            <xh:ul class="nav{{' xforms-hidden'[not($fr-toc-top-level-section-active)]}}">
                                <xsl:apply-templates mode="fr-toc-sections"/>
                            </xh:ul>
                        </xh:div>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- No content -->
                    </xsl:otherwise>
                </xsl:choose>
            </xh:li>
        </xsl:element>
    </xsl:template>

</xsl:stylesheet>
