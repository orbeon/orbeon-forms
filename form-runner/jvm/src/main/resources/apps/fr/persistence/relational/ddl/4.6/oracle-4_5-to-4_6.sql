ALTER TABLE orbeon_form_definition ADD form_metadata VARCHAR2(4000);

UPDATE orbeon_form_definition d
   SET d.form_metadata = EXTRACT(
       d.xml,
       '/xh:html/xh:head/xf:model[@id = "fr-form-model"]/xf:instance[@id = "fr-form-metadata"]/metadata',
       'xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms"'
   )
 WHERE d.deleted = 'N' AND d.form_metadata IS NULL;
