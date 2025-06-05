ALTER TABLE orbeon_form_definition_attach ADD hash_algorithm VARCHAR2(255 CHAR);
ALTER TABLE orbeon_form_definition_attach ADD hash_value     VARCHAR2(255 CHAR);

ALTER TABLE orbeon_form_data_attach ADD hash_algorithm VARCHAR2(255 CHAR);
ALTER TABLE orbeon_form_data_attach ADD hash_value     VARCHAR2(255 CHAR);