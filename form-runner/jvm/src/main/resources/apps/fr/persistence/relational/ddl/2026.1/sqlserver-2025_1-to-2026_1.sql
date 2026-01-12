ALTER TABLE orbeon_form_definition ADD id INT IDENTITY(1, 1) NOT NULL;
ALTER TABLE orbeon_form_definition ADD PRIMARY KEY (id);

ALTER TABLE orbeon_form_definition_attach ADD id INT IDENTITY(1, 1) NOT NULL;
ALTER TABLE orbeon_form_definition_attach ADD PRIMARY KEY (id);

ALTER TABLE orbeon_form_data_attach ADD id INT IDENTITY(1, 1) NOT NULL;
ALTER TABLE orbeon_form_data_attach ADD PRIMARY KEY (id);

ALTER TABLE orbeon_i_current ADD id INT IDENTITY(1, 1) NOT NULL;
ALTER TABLE orbeon_i_current ADD PRIMARY KEY (id);

ALTER TABLE orbeon_i_control_text ADD id INT IDENTITY(1, 1) NOT NULL;
ALTER TABLE orbeon_i_control_text ADD PRIMARY KEY (id);

ALTER TABLE orbeon_organization ADD PRIMARY KEY (id, pos);
