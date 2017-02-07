alter database character set utf8 collate utf8_general_ci;

alter table orbeon_form_definition
    change last_modified_by  last_modified_by  varchar(255) collate utf8_bin,
    change app               app               varchar(255) collate utf8_bin,
    change form              form              varchar(255) collate utf8_bin,
    change deleted           deleted           char(1)      collate utf8_bin not null,
    change xml               xml               mediumtext   collate utf8_unicode_ci;

alter table orbeon_form_definition_attach
    change last_modified_by  last_modified_by  varchar(255) collate utf8_bin,
    change app               app               varchar(255) collate utf8_bin,
    change form              form              varchar(255) collate utf8_bin,
    change deleted           deleted           char(1)      collate utf8_bin not null,
    change file_name         file_name         varchar(255) collate utf8_bin;

alter table orbeon_form_data
    change last_modified_by  last_modified_by  varchar(255) collate utf8_bin,
    change username          username          varchar(255) collate utf8_bin,
    change groupname         groupname         varchar(255) collate utf8_bin,
    change app               app               varchar(255) collate utf8_bin,
    change form              form              varchar(255) collate utf8_bin,
    change document_id       document_id       varchar(255) collate utf8_bin,
    change draft             draft             char(1)      collate utf8_bin not null,
    change deleted           deleted           char(1)      collate utf8_bin not null,
    change xml               xml               mediumtext   collate utf8_unicode_ci;

alter table orbeon_form_data_attach
    change last_modified_by  last_modified_by  varchar(255) collate utf8_bin,
    change username          username          varchar(255) collate utf8_bin,
    change groupname         groupname         varchar(255) collate utf8_bin,
    change app               app               varchar(255) collate utf8_bin,
    change form              form              varchar(255) collate utf8_bin,
    change document_id       document_id       varchar(255) collate utf8_bin,
    change draft             draft             char(1)      collate utf8_bin not null,
    change deleted           deleted           char(1)      collate utf8_bin not null,
    change file_name         file_name         varchar(255) collate utf8_bin;
