-- Add a simple id as primary key

ALTER TABLE orbeon_form_data
ADD         id INT PRIMARY KEY NOT NULL AUTO_INCREMENT;

-- New index tables

CREATE TABLE orbeon_i_current (
    data_id             INT                                                               NOT NULL,
    created             TIMESTAMP(6)                                                      NOT NULL,
    last_modified_time  TIMESTAMP(6)                                                      NOT NULL,
    last_modified_by    VARCHAR(255)          CHARACTER SET utf8 COLLATE utf8_bin                 ,
    username            VARCHAR(255)          CHARACTER SET utf8 COLLATE utf8_bin                 ,
    groupname           VARCHAR(255)          CHARACTER SET utf8 COLLATE utf8_bin                 ,
    app                 VARCHAR(255)          CHARACTER SET utf8 COLLATE utf8_bin         NOT NULL,
    form                VARCHAR(255)          CHARACTER SET utf8 COLLATE utf8_bin         NOT NULL,
    form_version        INT                                                               NOT NULL,
    document_id         VARCHAR(255)          CHARACTER SET utf8 COLLATE utf8_bin         NOT NULL,
    draft               CHAR(1)                                                           NOT NULL,
    FOREIGN KEY         (data_id)             REFERENCES orbeon_form_data(id)
)   ENGINE = InnoDB;

CREATE TABLE orbeon_i_control_text (
    data_id             INT                                                               NOT NULL,
    pos                 INT                                                               NOT NULL,
    control             VARCHAR(255)          CHARACTER SET utf8 COLLATE utf8_bin         NOT NULL,
    val                 MEDIUMTEXT            CHARACTER SET utf8 COLLATE utf8_unicode_ci  NOT NULL,
    FOREIGN KEY         (data_id)             REFERENCES orbeon_form_data(id)
)   ENGINE = InnoDB;
