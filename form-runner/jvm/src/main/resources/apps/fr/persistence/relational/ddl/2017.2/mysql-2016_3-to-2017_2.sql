CREATE TABLE orbeon_form_data_lease (
    document_id         VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin        NOT NULL PRIMARY KEY,
    username            VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin        NOT NULL            ,
    groupname           VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin                            ,
    expiration          TIMESTAMP(6)                                                      NOT NULL
);
