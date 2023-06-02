alter table orbeon_form_definition
    change last_modified  last_modified_time  timestamp(6),
    change username       last_modified_by    varchar(255),
    add    form_version                       int            after form;

alter table orbeon_form_definition_attach
    change last_modified  last_modified_time  timestamp(6),
    change username       last_modified_by    varchar(255),
    add    form_version                       int            after form;

alter table orbeon_form_data
    change last_modified  last_modified_time  timestamp(6),
    change username       last_modified_by    varchar(255),
    add    username                           varchar(255)   after last_modified_by,
    add    groupname                          varchar(255)   after username,
    add    form_version                       int            after form,
    add    draft                              char(1)        after document_id;

alter table orbeon_form_data_attach
    change last_modified  last_modified_time  timestamp(6),
    change username       last_modified_by    varchar(255),
    add    username                           varchar(255)   after last_modified_by,
    add    groupname                          varchar(255)   after username,
    add    form_version                       int            after form,
    add    draft                              char(1)        after document_id;

update      orbeon_form_definition        set form_version = 1;
update      orbeon_form_definition_attach set form_version = 1;
update      orbeon_form_data              set form_version = 1, draft = 'N';
update      orbeon_form_data_attach       set form_version = 1, draft = 'N';

alter table orbeon_form_definition
    change form_version   form_version        int not null;

alter table orbeon_form_definition_attach
    change form_version   form_version        int not null;

alter table orbeon_form_data
    change form_version   form_version        int not null,
    change draft          draft               char(1) not null;

alter table orbeon_form_data_attach
    change form_version   form_version        int not null,
    change draft          draft               char(1) not null;
