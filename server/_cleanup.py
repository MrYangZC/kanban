import os, shutil
ROOT = r"src\main\java\com\seven7m\kanban"
KEEP = {"common", "controller", "service", "mapper", "entity", "dto"}
removed = []
for d, _f, fs in list(os.walk(ROOT))[::-1]:
    if _f or fs:
        continue
    rel = os.path.relpath(d, ROOT)
    depth = 0 if rel == "." else len(rel.split(os.sep))
    # 只删 ROOT 直接子层(深度1)中不在 KEEP 的，以及更深层(深度>1)的所有空目录
    if depth <= 1 and os.path.basename(d) in KEEP:
        continue
    if os.path.abspath(d) == os.path.abspath(ROOT):
        continue
    shutil.rmtree(d)
    removed.append(rel)
print("removed empty dirs:", len(removed))
for r in removed:
    print("  ", r)
