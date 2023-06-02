DROP          INDEX orbeon_from_data_attach_i1    ON orbeon_form_data_attach ;
CREATE        INDEX orbeon_from_data_attach_i1    ON orbeon_form_data_attach       (app, form, document_id, draft);

ALTER  TABLE orbeon_form_definition
MODIFY last_modified_by    VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY app                 VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY form                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY form_metadata       VARCHAR(4000)                                COLLATE utf8mb4_bin                ,
MODIFY deleted             CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
MODIFY xml                 MEDIUMTEXT             CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         ;

ALTER  TABLE orbeon_form_definition_attach
MODIFY last_modified_by    VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY app                 VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY form                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY deleted             CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
MODIFY file_name           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ;

ALTER  TABLE orbeon_form_data
MODIFY last_modified_by    VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY username            VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY groupname           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY app                 VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY form                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY document_id         VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY draft               CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
MODIFY deleted             CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
MODIFY xml                 MEDIUMTEXT             CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         ;

ALTER  TABLE orbeon_form_data_attach
MODIFY last_modified_by    VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY username            VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY groupname           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY app                 VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY form                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY document_id         VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ,
MODIFY draft               CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
MODIFY deleted             CHAR(1)                                      COLLATE utf8mb4_bin        NOT NULL,
MODIFY file_name           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ;

ALTER  TABLE orbeon_form_data_lease
MODIFY document_id         VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin        NOT NULL,
MODIFY username            VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin        NOT NULL,
MODIFY groupname           VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                ;

ALTER  TABLE orbeon_organization
MODIFY name                VARCHAR(255)           CHARACTER SET utf8mb4 COLLATE utf8mb4_bin        NOT NULL;

ALTER  TABLE orbeon_i_current
MODIFY last_modified_by    VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                 ,
MODIFY username            VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                 ,
MODIFY groupname           VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin                 ,
MODIFY app                 VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin         NOT NULL,
MODIFY form                VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin         NOT NULL,
MODIFY document_id         VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin         NOT NULL,
MODIFY draft               CHAR(1)                                     COLLATE utf8mb4_bin         NOT NULL;

ALTER  TABLE orbeon_i_control_text
MODIFY control             VARCHAR(255)          CHARACTER SET utf8mb4 COLLATE utf8mb4_bin         NOT NULL,
MODIFY val                 MEDIUMTEXT            CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL;

CREATE        INDEX orbeon_form_data_i2           ON orbeon_form_data              (document_id);
CREATE        INDEX orbeon_from_data_attach_i2    ON orbeon_form_data_attach       (document_id);
CREATE        INDEX orbeon_i_current_i2           ON orbeon_i_current              (app, form, draft);
