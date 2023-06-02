CREATE TABLE orbeon_organization (
    id                  INT           NOT NULL,
    depth               INT           NOT NULL,
    pos                 INT           NOT NULL,
    name                VARCHAR(255)  NOT NULL
);

CREATE TABLE orbeon_seq (
    val                 INT           NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1)
);

ALTER TABLE orbeon_form_data
ADD organization_id     INT;

ALTER TABLE orbeon_form_data_attach
ADD organization_id     INT;

ALTER TABLE orbeon_i_current
ADD organization_id     INT;
