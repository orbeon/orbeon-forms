ALTER TABLE orbeon_form_definition ADD id NUMBER;
UPDATE orbeon_form_definition SET id = orbeon_seq.NEXTVAL;
ALTER TABLE orbeon_form_definition MODIFY id NOT NULL;
ALTER TABLE orbeon_form_definition ADD CONSTRAINT orbeon_form_definition_pk PRIMARY KEY (id);

ALTER TABLE orbeon_form_definition_attach ADD id NUMBER;
UPDATE orbeon_form_definition_attach SET id = orbeon_seq.NEXTVAL;
ALTER TABLE orbeon_form_definition_attach MODIFY id NOT NULL;
ALTER TABLE orbeon_form_definition_attach ADD CONSTRAINT orbeon_form_definition_attach_pk PRIMARY KEY (id);

ALTER TABLE orbeon_form_data_attach ADD id NUMBER;
UPDATE orbeon_form_data_attach SET id = orbeon_seq.NEXTVAL;
ALTER TABLE orbeon_form_data_attach MODIFY id NOT NULL;
ALTER TABLE orbeon_form_data_attach ADD CONSTRAINT orbeon_form_data_attach_pk PRIMARY KEY (id);

ALTER TABLE orbeon_i_current ADD id NUMBER;
UPDATE orbeon_i_current SET id = orbeon_seq.NEXTVAL;
ALTER TABLE orbeon_i_current MODIFY id NOT NULL;
ALTER TABLE orbeon_i_current ADD CONSTRAINT orbeon_i_current_pk PRIMARY KEY (id);

ALTER TABLE orbeon_i_control_text ADD id NUMBER;
UPDATE orbeon_i_control_text SET id = orbeon_seq.NEXTVAL;
ALTER TABLE orbeon_i_control_text MODIFY id NOT NULL;
ALTER TABLE orbeon_i_control_text ADD CONSTRAINT orbeon_i_control_text_pk PRIMARY KEY (id);

ALTER TABLE orbeon_organization ADD CONSTRAINT orbeon_organization_pk PRIMARY KEY (id, pos);
