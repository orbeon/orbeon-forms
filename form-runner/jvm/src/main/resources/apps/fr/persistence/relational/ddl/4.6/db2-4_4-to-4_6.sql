ALTER TABLE orbeon_form_definition ADD form_metadata VARCHAR(4000);

UPDATE orbeon_form_definition d
  SET d.form_metadata = XMLSERIALIZE(XMLQUERY('
     declare namespace xh = "http://www.w3.org/1999/xhtml";
     declare namespace xf = "http://www.w3.org/2002/xforms";
     $xml/xh:html/xh:head/xf:model[@id = "fr-form-model"]/xf:instance[@id = "fr-form-metadata"]/metadata
     ' passing d.xml as "xml"
   ) AS VARCHAR(4000) EXCLUDING XMLDECLARATION)
WHERE d.deleted = 'N' AND d.form_metadata IS NULL;
