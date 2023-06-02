CREATE TABLE orbeon_organization (
    id                  INT                                                               NOT NULL,
    depth               INT                                                               NOT NULL,
    pos                 INT                                                               NOT NULL,
    name                VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin        NOT NULL
)   ENGINE = InnoDB;

ALTER TABLE orbeon_form_data
ADD organization_id     INT                                                                       ;

ALTER TABLE orbeon_form_data_attach
ADD organization_id     INT                                                                       ;

ALTER TABLE orbeon_i_current
ADD organization_id     INT                                                                       ;

CREATE TABLE orbeon_seq (
    val                 INT                    PRIMARY KEY AUTO_INCREMENT                 NOT NULL
)   ENGINE = InnoDB;

CREATE        INDEX orbeon_form_definition_i1     ON orbeon_form_definition        (app, form);
CREATE        INDEX orbeon_form_definition_att_i1 ON orbeon_form_definition_attach (app, form, file_name);
CREATE        INDEX orbeon_from_data_i1           ON orbeon_form_data              (app, form, document_id, draft);
CREATE        INDEX orbeon_from_data_attach_i1    ON orbeon_form_data_attach       (app, form, document_id, file_name, draft);
CREATE UNIQUE INDEX orbeon_i_current_i1           ON orbeon_i_current              (data_id, draft);
CREATE        INDEX orbeon_i_control_text_i1      ON orbeon_i_control_text         (data_id);
