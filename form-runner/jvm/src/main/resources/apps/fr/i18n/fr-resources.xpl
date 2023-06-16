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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">

    <!-- Request parameters (app, form) -->
    <p:param type="input" name="instance"/>
    <!-- Form metadata -->
    <p:param type="input" name="data"/>
    <p:param type="output" name="data"/>

    <!-- Support XInclude in resources -->
    <p:processor name="oxf:xinclude">
        <p:input name="config" href="resources.xml"/>
        <p:output name="data" id="resources"/>
    </p:processor>

    <!-- Resources -->
    <p:processor name="fr:resources-patcher">
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#data"/>
        <p:input name="resources" href="#resources"/>
        <!-- Dependency on overridden properties so stylesheet runs again when properties change -->
        <p:input name="properties-local" href="oxf:/config/properties-local.xml"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
