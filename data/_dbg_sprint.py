import psycopg2
conn = psycopg2.connect(host="192.168.2.17", port=5433, dbname="kanban", user="postgres", password="X@bN!7n9m1")
cur = conn.cursor()

print("=== sprint 表结构 ===")
cur.execute("""
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name='sprint'
ORDER BY ordinal_position;
""")
for r in cur.fetchall():
    print(r)

print("\n=== flyway_schema_history ===")
cur.execute("SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;")
for r in cur.fetchall():
    print(r)

print("\n=== 现有 sprint 数据 ===")
cur.execute("SELECT id, project_id, name, status, start_date, end_date FROM sprint ORDER BY created_at DESC LIMIT 10;")
for r in cur.fetchall():
    print(r)

conn.close()
