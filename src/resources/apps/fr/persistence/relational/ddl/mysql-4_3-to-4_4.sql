alter table orbeon_form_definition
    change last_modified  last_modified_time  timestamp,
    change username       last_modified_by    varchar(255);

alter table orbeon_form_definition_attach
    change last_modified  last_modified_time  timestamp,
    change username       last_modified_by    varchar(255);

alter table orbeon_form_data
    change last_modified  last_modified_time  timestamp,
    change username       last_modified_by    varchar(255),
    add    username                           varchar(255),
    add    groupname                          varchar(255);

alter table orbeon_form_data_attach
    change last_modified  last_modified_time  timestamp,
    change username       last_modified_by    varchar(255),
    add    username                           varchar(255),
    add    groupname                          varchar(255);

alter table orbeon_form_data        add draft char(1) after deleted;
alter table orbeon_form_data_attach add draft char(1) after deleted;
update      orbeon_form_data        set draft = 'N';
update      orbeon_form_data_attach set draft = 'N';
alter table orbeon_form_data        change draft draft char(1) not null;
alter table orbeon_form_data_attach change draft draft char(1) not null;