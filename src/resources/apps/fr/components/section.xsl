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
        xmlns:xbl="http://www.w3.org/ns/xbl">

    <!-- NOTE: This won't be needed once XBL components properties can be inherited at the form level -->
    <xsl:template match="xhtml:body//fr:section[not(@editable = 'true')] | xbl:binding/xbl:template//fr:section[not(@editable = 'true')]">
        <fr:section animate="{$is-animate-sections}" collapse="{$is-section-collapse}" mode="{$mode}">
            <xsl:apply-templates select="@* except (@animate, @collapse, @mode)"/>
            <xsl:apply-templates select="node()"/>
        </fr:section>
    </xsl:template>

    <!-- This is present temporarily for Form Builder "classic" -->
    <xsl:template match="xhtml:body//fr:section[@editable = 'true'] | xbl:binding/xbl:template//fr:section[@editable = 'true']">
      <xsl:if test="normalize-space(@id) = ''">
          <xsl:message terminate="yes">"id" attribute is mandatory</xsl:message>
      </xsl:if>

      <xsl:variable name="open" as="xs:boolean" select="if ($mode = 'view') then true() else if (@open = 'false') then false() else true()"/>
      <xsl:variable name="section-id" as="xs:string" select="@id"/>

      <xsl:variable name="ancestor-sections" as="xs:integer" select="count(ancestor::fr:section)"/>

      <!-- Section content area -->

      <xforms:group id="{$section-id}-group">
          <!-- Support single-node bindings and context -->
          <xsl:copy-of select="@ref | @bind | @context"/>
          <xsl:attribute name="class" select="string-join(('fr-section-container', @class), ' ')"/>

          <!-- Section title area: open/close button, title, help -->
          <xsl:element name="{if ($ancestor-sections = 0) then 'xhtml:h2' else 'xhtml:h3'}">
              <xsl:attribute name="class" select="'fr-section-title'"/>

              <!-- Open/close button -->
              <xforms:group appearance="xxforms:internal">
                  <xsl:if test="$is-section-collapse">
                      <xforms:switch id="switch-button-{$section-id}" xxforms:readonly-appearance="dynamic" class="fr-section-arrow">
                          <xforms:case id="case-button-{$section-id}-closed" selected="{if (not($open)) then 'true' else 'false'}">
                              <!-- "+" trigger -->
                              <xforms:trigger appearance="minimal" id="button-{$section-id}-open" class="fr-section-open-close">
                                  <xforms:label>
                                      <xhtml:img width="12" height="12" src="/apps/fr/style/images/mozilla/arrow-rit-hov.gif" alt="" title="{{$fr-resources/components/labels/open-section}}"/>
                                  </xforms:label>
                              </xforms:trigger>
                          </xforms:case>
                          <xforms:case id="case-button-{$section-id}-open" selected="{if ($open) then 'true' else 'false'}">
                              <!-- "-" trigger -->
                              <xforms:trigger appearance="minimal" id="button-{$section-id}-close" class="fr-section-open-close">
                                  <xforms:label>
                                      <xhtml:img width="12" height="12" src="/apps/fr/style/images/mozilla/arrow-dn-hov.gif" alt="" title="{{$fr-resources/components/labels/close-section}}"/>
                                  </xforms:label>
                              </xforms:trigger>
                          </xforms:case>
                      </xforms:switch>

                      <!-- Handle DOMActivate event to open/close the switches -->
                      <xforms:action ev:event="DOMActivate" ev:target="{$section-id} button-{$section-id}-open button-{$section-id}-close">
                          <xxforms:variable name="expand" select="xxforms:case('switch-{$section-id}') = 'case-{$section-id}-closed'" as="xs:boolean"/>
                          <xforms:action if="$expand">
                              <!-- Expand -->
                              <xforms:toggle case="case-{$section-id}-open"/>
                              <xforms:toggle case="case-button-{$section-id}-open"/>
                          </xforms:action>
                          <xforms:action if="not($expand)">
                              <!-- Collapse -->
                              <xforms:toggle case="case-{$section-id}-closed"/>
                              <xforms:toggle case="case-button-{$section-id}-closed"/>
                          </xforms:action>
                      </xforms:action>
                  </xsl:if>

                  <xsl:choose>
                      <xsl:when test="@editable = 'true'">
                          <!-- Editable section title -->
                          <xsl:variable name="input" as="element(fr:inplace-input)">
                              <fr:inplace-input id="{$section-id}-editable-title" ref="{xforms:label/@ref}">
                                  <xsl:apply-templates select="xforms:hint | xforms:alert"/>
                                  <!-- Put a hidden label for the error summary -->
                                  <xforms:label class="fr-hidden" ref="$fr-resources/components/labels/section-name"/>
                              </fr:inplace-input>
                          </xsl:variable>
                          <xsl:apply-templates select="$input"/>
                          <span class="fr-section-buttons">
                              <xsl:apply-templates select="fr:buttons/node()"/>
                          </span>
                      </xsl:when>
                      <xsl:when test="$is-section-collapse">
                          <!-- Set the section id to this trigger: this id matching is needed for noscript help -->
                          <xforms:trigger id="{$section-id}" appearance="minimal" class="fr-section-label">
                              <xsl:apply-templates select="xforms:label | xforms:help | xforms:alert"/>
                          </xforms:trigger>
                      </xsl:when>
                      <xsl:otherwise>
                          <!-- Set the section id to this output: this id matching is needed for noscript help -->
                          <xforms:output id="{$section-id}" appearance="minimal" value="''">
                              <xsl:apply-templates select="xforms:label | xforms:help | xforms:alert"/>
                          </xforms:output>
                      </xsl:otherwise>
                  </xsl:choose>

              </xforms:group>

          </xsl:element>

          <xforms:switch id="switch-{$section-id}" xxforms:readonly-appearance="dynamic">
              <!-- Closed section -->
              <xforms:case id="case-{$section-id}-closed" selected="{if (not($open)) then 'true' else 'false'}"/>
              <!-- Open section -->
              <xforms:case id="case-{$section-id}-open" selected="{if ($open) then 'true' else 'false'}">
                  <xsl:if test="$is-animate-sections">
                      <xsl:attribute name="class">xxforms-animate</xsl:attribute>
                  </xsl:if>
                  <xhtml:div class="fr-section-content">
                      <!-- Section content except label, event handlers, and buttons -->
                      <xsl:apply-templates select="* except (xforms:label, *[@ev:*], fr:buttons)"/>
                  </xhtml:div>
              </xforms:case>
          </xforms:switch>
          <!-- Event handlers children of fr:section -->
          <xsl:apply-templates select="*[@ev:*]"/>
      </xforms:group>

  </xsl:template>
</xsl:stylesheet>
