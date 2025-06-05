ALTER TABLE orbeon_form_definition_attach ADD COLUMN hash_algorithm VARCHAR(1020);
ALTER TABLE orbeon_form_definition_attach ADD COLUMN hash_value     VARCHAR(1020);

ALTER TABLE orbeon_form_data_attach ADD COLUMN hash_algorithm VARCHAR(1020);
ALTER TABLE orbeon_form_data_attach ADD COLUMN hash_value     VARCHAR(1020);