import re
d = open(r"_compile.txt", encoding="gbk", errors="replace").read().splitlines()
errs = [l.strip() for l in d if "[ERROR]" in l]
with open("_errs.txt", "w", encoding="utf-8") as f:
    for e in errs:
        f.write(e + "\n")
print("total err lines:", len(errs))
