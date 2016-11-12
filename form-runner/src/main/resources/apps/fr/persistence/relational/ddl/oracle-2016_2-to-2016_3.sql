CREATE TABLE orbeon_organization (
    id                  NUMBER          NOT NULL,
    depth               NUMBER          NOT NULL,
    pos                 NUMBER          NOT NULL,
    name                VARCHAR2(255)   NOT NULL
);

ALTER TABLE orbeon_form_data
ADD organization_id     NUMBER;

ALTER TABLE orbeon_form_data_attach
ADD organization_id     NUMBER;

ALTER TABLE orbeon_i_current
ADD organization_id     NUMBER;
