-- Add a simple id as primary key

ALTER TABLE  orbeon_form_data
ADD          id INT NOT NULL DEFAULT 0;

ALTER TABLE  orbeon_form_data
ALTER COLUMN id DROP DEFAULT;

ALTER TABLE  orbeon_form_data
ALTER COLUMN id SET GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1);

CALL SYSPROC.ADMIN_CMD('REORG TABLE orbeon_form_data');

UPDATE orbeon_form_data SET id = DEFAULT;

ALTER TABLE orbeon_form_data
ADD PRIMARY KEY (id);