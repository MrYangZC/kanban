import os, re

ROOT = r"e:\sevenM\河图\java\kanban\server\src\main\java\com\seven7m\kanban"
CONST = "com.seven7m.kanban.entity.project.ProjectRole"
ANNO = "com.seven7m.kanban.common.security.annotation.ProjectRole"

fixed = []
for dp, _, fns in os.walk(ROOT):
    for fn in fns:
        if not fn.endswith(".java"):
            continue
        fp = os.path.join(dp, fn)
        with open(fp, encoding="utf-8") as f:
            content = f.read()
        if "import " + CONST + ";" not in content:
            continue
        # 判断是否使用 @ProjectRole 注解（方法/参数上的注解，区别于 ProjectRole.OWNER 常量）
        # 注解用法形如 @ProjectRole( 或 @ProjectRole\n 或 @ProjectRole)
        uses_anno = bool(re.search(r"@ProjectRole(\s*\(|\s*$|\s*\n)", content))
        if uses_anno:
            new_content = content.replace("import " + CONST + ";", "import " + ANNO + ";", 1)
            if new_content != content:
                with open(fp, "w", encoding="utf-8") as f:
                    f.write(new_content)
                fixed.append((os.path.relpath(fp, ROOT), "anno import -> " + ANNO))

print("fixed ProjectRole anno imports:", len(fixed))
for f, msg in fixed:
    print("  ", f, "-", msg)
