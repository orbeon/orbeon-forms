ALTER TABLE orbeon_form_definition_attach ADD COLUMN hash_algorithm VARCHAR(255);
ALTER TABLE orbeon_form_definition_attach ADD COLUMN hash_value     VARCHAR(255);

ALTER TABLE orbeon_form_data_attach ADD COLUMN hash_algorithm VARCHAR(255);
ALTER TABLE orbeon_form_data_attach ADD COLUMN hash_value     VARCHAR(255);