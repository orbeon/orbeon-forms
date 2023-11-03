ALTER TABLE  orbeon_form_definition
ALTER COLUMN created            DATETIME2;

ALTER TABLE  orbeon_form_definition
ALTER COLUMN last_modified_time DATETIME2;

ALTER TABLE  orbeon_form_definition_attach
ALTER COLUMN created            DATETIME2;

ALTER TABLE  orbeon_form_definition_attach
ALTER COLUMN last_modified_time DATETIME2;

ALTER TABLE  orbeon_form_data
ALTER COLUMN created            DATETIME2;

ALTER TABLE  orbeon_form_data
ALTER COLUMN last_modified_time DATETIME2;

ALTER TABLE  orbeon_form_data_attach
ALTER COLUMN created            DATETIME2;

ALTER TABLE  orbeon_form_data_attach
ALTER COLUMN last_modified_time DATETIME2;

ALTER TABLE  orbeon_form_data_lease
ALTER COLUMN expiration         DATETIME2;

ALTER TABLE  orbeon_i_current
ALTER COLUMN created            DATETIME2;

ALTER TABLE  orbeon_i_current
ALTER COLUMN last_modified_time DATETIME2;
