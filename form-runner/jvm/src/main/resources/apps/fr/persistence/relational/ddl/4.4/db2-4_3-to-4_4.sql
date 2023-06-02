alter table orbeon_form_definition        add           form_version   int default 1 not null;
alter table orbeon_form_definition_attach add           form_version   int default 1 not null;
alter table orbeon_form_data              add           form_version   int default 1 not null;
alter table orbeon_form_data_attach       add           form_version   int default 1 not null;
