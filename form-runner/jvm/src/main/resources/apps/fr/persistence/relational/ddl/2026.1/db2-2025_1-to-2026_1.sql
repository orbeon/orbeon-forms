ALTER TABLE orbeon_form_definition ADD COLUMN id INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1);
UPDATE orbeon_form_definition SET id = DEFAULT WHERE id IS NULL;
ALTER TABLE orbeon_form_definition ALTER COLUMN id SET NOT NULL;
ALTER TABLE orbeon_form_definition ADD PRIMARY KEY (id);

ALTER TABLE orbeon_form_definition_attach ADD COLUMN id INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1);
UPDATE orbeon_form_definition_attach SET id = DEFAULT WHERE id IS NULL;
ALTER TABLE orbeon_form_definition_attach ALTER COLUMN id SET NOT NULL;
ALTER TABLE orbeon_form_definition_attach ADD PRIMARY KEY (id);

ALTER TABLE orbeon_form_data_attach ADD COLUMN id INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1);
UPDATE orbeon_form_data_attach SET id = DEFAULT WHERE id IS NULL;
ALTER TABLE orbeon_form_data_attach ALTER COLUMN id SET NOT NULL;
ALTER TABLE orbeon_form_data_attach ADD PRIMARY KEY (id);

ALTER TABLE orbeon_i_current ADD COLUMN id INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1);
UPDATE orbeon_i_current SET id = DEFAULT WHERE id IS NULL;
ALTER TABLE orbeon_i_current ALTER COLUMN id SET NOT NULL;
ALTER TABLE orbeon_i_current ADD PRIMARY KEY (id);

ALTER TABLE orbeon_i_control_text ADD COLUMN id INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1);
UPDATE orbeon_i_control_text SET id = DEFAULT WHERE id IS NULL;
ALTER TABLE orbeon_i_control_text ALTER COLUMN id SET NOT NULL;
ALTER TABLE orbeon_i_control_text ADD PRIMARY KEY (id);

ALTER TABLE orbeon_organization ADD PRIMARY KEY (id, pos);
