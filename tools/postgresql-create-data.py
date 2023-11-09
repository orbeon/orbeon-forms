# Create documents in PostgreSQL to test free text search
# Run with `python3 postgresql-create-data.py` (takes about 20 minutes)

import psycopg2
import random
import string
import textwrap
import nltk
from datetime import datetime

document_count = 15000
document_size  = 100000

db_params = {
    'database' : 'orbeon',
    'user'     : 'orbeon',
    'password' : 'password',
    'host'     : 'localhost',
    'port'     : 5432
}

if not nltk.corpus.words.words():
    nltk.download('words')

def random_string(length):
    word_list = nltk.corpus.words.words()
    generated_string = ''
    while len(generated_string) < length:
        generated_string += random.choice(word_list)
        if len(generated_string) < length:
            generated_string += ' '
    return generated_string

delete_current = textwrap.dedent("""\
    DELETE FROM orbeon_i_current
    WHERE app  = 'postgresql' AND
          form = 'postgresql'
""")
delete_data = textwrap.dedent("""\
    DELETE FROM orbeon_form_data
    WHERE app  = 'postgresql' AND
          form = 'postgresql'
""")
insert_data = textwrap.dedent("""\
    INSERT INTO orbeon_form_data
        (
            created, last_modified_time,
            app, form, form_version,
            document_id,
            deleted, draft,
            xml
        )
    VALUES
        (
            %s, %s,
            'postgresql', 'postgresql', 1,
            %s,
            'N', 'N',
            ('<_>' || %s || '</_>')::xml
        )
    RETURNING id
""")
insert_current = textwrap.dedent("""\
    INSERT INTO orbeon_i_current
        (
            data_id,
            created, last_modified_time,
            app, form, form_version,
            document_id,
            draft
        )
    VALUES
        (
            %s,
            %s, %s,
            'postgresql', 'postgresql', 1,
            %s,
            'N'
        )
""")

try:
    conn = psycopg2.connect(**db_params)
    cur = conn.cursor()
    cur.execute(delete_current)
    cur.execute(delete_data)
    conn.commit()
    for _ in range(document_count):
        now         = datetime.now()
        document_id = ''.join([str(random.randint(0, 9)) for _ in range(16)])
        xml         = random_string(document_size)
        cur.execute(insert_data   , (now, now, document_id, xml))
        data_id     = cur.fetchone()[0]
        cur.execute(insert_current, (data_id, now, now, document_id))
        conn.commit()
except psycopg2.Error as e:
    print(f"An error occurred: {e}")
    conn.rollback()
finally:
    if cur:  cur.close()
    if conn: conn.close()
