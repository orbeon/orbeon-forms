CREATE INDEX orbeon_form_data_x
ON orbeon_form_data USING GIN (to_tsvector('simple', xml::text));
