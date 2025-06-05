ALTER TABLE orbeon_form_definition_attach ADD COLUMN hash_algorithm VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE orbeon_form_definition_attach ADD COLUMN hash_value     VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

ALTER TABLE orbeon_form_data_attach ADD COLUMN hash_algorithm VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE orbeon_form_data_attach ADD COLUMN hash_value     VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;