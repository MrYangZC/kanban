import os, re
ROOT = r"e:\sevenM\河图\java\kanban\server\src\main\java\com\seven7m\kanban"
CONST = "com.seven7m.kanban.entity.project.ProjectRole"
ANNO = "com.seven7m.kanban.common.security.annotation.ProjectRole"
for dp, _, fns in os.walk(ROOT):
    for fn in fns:
        if not fn.endswith(".java"):
            continue
        fp = os.path.join(dp, fn)
        c = open(fp, encoding="utf-8").read()
        if ("import " + CONST + ";") not in c:
            continue
        # 注解用法：@ProjectRole 单独成行，或 @ProjectRole(
        has_anno = bool(re.search(r"@ProjectRole\s*\(", c)) or bool(re.search(r"^\s*@ProjectRole\s*$", c, re.M))
        # 常量用法：ProjectRole.OWNER / ProjectRole.XXX
        has_const = bool(re.search(r"ProjectRole\.\w+", c))
        print(os.path.relpath(fp, ROOT), "| anno=", has_anno, "| const=", has_const)
