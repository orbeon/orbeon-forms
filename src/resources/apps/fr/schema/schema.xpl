<!--
  Copyright (C) 2012 Orbeon, Inc.

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

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the form -->
    <p:param type="input" name="data"/>
    <!-- Schema -->
    <p:param type="output" name="data"/>

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../unroll-form.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="unrolled-form-definition"/>
    </p:processor>

    <p:processor name="fr:xforms-to-schema">
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#unrolled-form-definition"/>
        <p:input name="annotated-document" href="#unrolled-form-definition"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
