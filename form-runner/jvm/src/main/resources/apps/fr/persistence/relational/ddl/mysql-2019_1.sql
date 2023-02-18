ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE orbeon_form_definition (
    created             TIMESTAMP(6)                                                                    ,
    last_modified_time  TIMESTAMP(6)                                                                    ,
    last_modified_by    VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    app                 VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    form                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    form_version        INT                                                                     NOT NULL,
    form_metadata       VARCHAR(4000)                                COLLATE utf8mb4_bin                ,
    deleted             CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
    xml                 MEDIUMTEXT             CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
)   ENGINE = InnoDB;

CREATE TABLE orbeon_form_definition_attach (
    created             TIMESTAMP(6)                                                                    ,
    last_modified_time  TIMESTAMP(6)                                                                    ,
    last_modified_by    VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    app                 VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    form                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    form_version        INT                                                                     NOT NULL,
    deleted             CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
    file_name           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    file_content        LONGBLOB
)   ENGINE = InnoDB;

CREATE TABLE orbeon_form_data (
    id                  INT                    PRIMARY KEY AUTO_INCREMENT                       NOT NULL,
    created             TIMESTAMP(6)                                                                    ,
    last_modified_time  TIMESTAMP(6)                                                                    ,
    last_modified_by    VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    username            VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    groupname           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    organization_id     INT                                                                             ,
    app                 VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    form                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    form_version        INT                                                                     NOT NULL,
    stage               VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    document_id         VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    draft               CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
    deleted             CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
    xml                 MEDIUMTEXT             CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
)   ENGINE = InnoDB;

CREATE TABLE orbeon_form_data_attach (
    created             TIMESTAMP(6)                                                                    ,
    last_modified_time  TIMESTAMP(6)                                                                    ,
    last_modified_by    VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    username            VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    groupname           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    organization_id     INT                                                                             ,
    app                 VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    form                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    form_version        INT                                                                     NOT NULL,
    document_id         VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    draft               CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
    deleted             CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
    file_name           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
    file_content        LONGBLOB
)   ENGINE = InnoDB;

CREATE TABLE orbeon_form_data_lease (
    document_id         VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin        NOT NULL PRIMARY KEY,
    username            VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin        NOT NULL            ,
    groupname           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                            ,
    expiration          TIMESTAMP(6)                                                            NOT NULL
);

CREATE TABLE orbeon_organization (
    id                  INT                                                                     NOT NULL,
    depth               INT                                                                     NOT NULL,
    pos                 INT                                                                     NOT NULL,
    name                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin        NOT NULL
)   ENGINE = InnoDB;

CREATE TABLE orbeon_seq (
    val                 INT                    PRIMARY KEY AUTO_INCREMENT                       NOT NULL
)   ENGINE = InnoDB;

CREATE TABLE orbeon_i_current (
    data_id             INT                                                                     NOT NULL,
    created             TIMESTAMP(6)                                                            NOT NULL,
    last_modified_time  TIMESTAMP(6)                                                            NOT NULL,
    last_modified_by    VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                 ,
    username            VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                 ,
    groupname           VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                 ,
    organization_id     INT                                                                             ,
    app                 VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin         NOT NULL,
    form                VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin         NOT NULL,
    form_version        INT                                                                     NOT NULL,
    stage               VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                 ,
    document_id         VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin         NOT NULL,
    draft               CHAR(1)                                     COLLATE utf8mb4_bin         NOT NULL,
    FOREIGN KEY         (data_id)             REFERENCES orbeon_form_data(id)
)   ENGINE = InnoDB;

CREATE TABLE orbeon_i_control_text (
    data_id             INT                                                                     NOT NULL,
    pos                 INT                                                                     NOT NULL,
    control             VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin         NOT NULL,
    val                 MEDIUMTEXT            CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL,
    FOREIGN KEY         (data_id)             REFERENCES orbeon_form_data(id)
)   ENGINE = InnoDB;

CREATE        INDEX orbeon_form_definition_i1     ON orbeon_form_definition        (app, form);
CREATE        INDEX orbeon_form_definition_i2     ON orbeon_form_definition        (app, form, form_version, last_modified_time);
CREATE        INDEX orbeon_form_definition_att_i1 ON orbeon_form_definition_attach (app, form, file_name);
CREATE        INDEX orbeon_from_data_i1           ON orbeon_form_data              (app, form, document_id, draft);
CREATE        INDEX orbeon_form_data_i2           ON orbeon_form_data              (document_id);
CREATE        INDEX orbeon_from_data_attach_i1    ON orbeon_form_data_attach       (app, form, document_id, draft);
CREATE        INDEX orbeon_from_data_attach_i2    ON orbeon_form_data_attach       (document_id);
CREATE UNIQUE INDEX orbeon_i_current_i1           ON orbeon_i_current              (data_id, draft);
CREATE        INDEX orbeon_i_current_i2           ON orbeon_i_current              (app, form, draft);
CREATE        INDEX orbeon_i_current_i3           ON orbeon_i_current              (document_id, draft);
CREATE        INDEX orbeon_i_control_text_i1      ON orbeon_i_control_text         (data_id);
