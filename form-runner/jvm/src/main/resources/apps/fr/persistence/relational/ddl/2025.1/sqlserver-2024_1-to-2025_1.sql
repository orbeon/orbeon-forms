ALTER TABLE orbeon_form_definition_attach ADD hash_algorithm NVARCHAR(255);
ALTER TABLE orbeon_form_definition_attach ADD hash_value     NVARCHAR(255);

ALTER TABLE orbeon_form_data_attach ADD hash_algorithm NVARCHAR(255);
ALTER TABLE orbeon_form_data_attach ADD hash_value     NVARCHAR(255);