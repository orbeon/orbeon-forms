ALTER TABLE orbeon_form_definition
    ADD form_metadata VARCHAR(4000) COLLATE utf8_bin AFTER form_version;

-- NOTE: The administrator must also republish forms to fully upgrade metadata. Here we only set basic app/form
-- metadata, but because MySQL does not have proper XML extract functions we cannot extract the full metadata, including
-- localized titles and permissions.
UPDATE orbeon_form_definition d
   SET d.form_metadata = CONCAT(
       '<metadata><application-name>',
       REPLACE(REPLACE(d.app, '&', '&amp;'), '<', '&lt;'),
       '</application-name><form-name>',
       REPLACE(REPLACE(d.form, '&', '&amp;'), '<', '&lt;'),
       '</form-name></metadata>'
   )
 WHERE d.deleted = 'N' AND d.form_metadata IS NULL;
