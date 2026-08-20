import re, os
d = open(r"_compile.txt", encoding="gbk", errors="replace").read().splitlines()
out = []
cur_file = None
for l in d:
    m = re.search(r"/E:([^\[]+?\.java):\[(\d+),(\d+)\]", l)
    if "[ERROR]" in l and m:
        cur_file = m.group(1)
    if "符号" in l or "位置" in l or "不兼容" in l or "程序包" in l:
        if cur_file:
            out.append(f"[{os.path.basename(cur_file)}] {l.strip()}")
# 去重相似
seen = set()
uniq = []
for o in out:
    if o not in seen:
        seen.add(o)
        uniq.append(o)
with open("_analyze_out.txt", "w", encoding="utf-8") as f:
    for o in uniq:
        f.write(o + "\n")
    f.write(f"=== TOTAL symbol/location lines: {len(uniq)}\n")
print("written _analyze_out.txt")
