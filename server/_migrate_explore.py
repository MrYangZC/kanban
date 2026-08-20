#!/usr/bin/env python3
# 只读探查：扫描全部 .java，生成 旧FQN -> 新FQN 映射，并报告冲突。不改动任何文件。
import os, re, json

ROOT = r"e:\sevenM\河图\java\kanban\server\src\main\java\com\seven7m\kanban"
BASE = r"e:\sevenM\河图\java\kanban\server\src\main\java"

# 业务模块根（在 kanban/ 下，且不属于 common）
BIZ_MODULES = ["issue","risk","project","board","user","workflow","rbac",
               "announcement","dashboard","issuestatus","milestone","sprint","workspace","dev"]

# 层 -> 顶层包
LAYER_MAP = {  # 文件所在子目录名 -> 目标顶层包
    "controller":"controller",
    "service":"service",
    "mapper":"mapper",
    "entity":"entity",
    "dto":"dto",
}

def rel(p):
    return os.path.relpath(p, ROOT).replace("\\","/")

def parse_package(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = re.match(r"\s*package\s+([\w\.]+)\s*;", line)
            if m:
                return m.group(1)
    return None

# 收集所有 .java
files = []
for dirpath, _, fnames in os.walk(ROOT):
    for fn in fnames:
        if fn.endswith(".java"):
            files.append(os.path.join(dirpath, fn))

mapping = {}      # old_fqn -> new_fqn
pathmap = {}      # old_path -> new_path
conflicts = []    # (new_fqn, [old_paths])
skip = []         # (old_path, reason)

for fp in files:
    pkg = parse_package(fp)
    fname = os.path.basename(fp)
    cls = fname[:-5]
    relp = rel(fp)
    parts = relp.split("/")  # 相对 kanban/

    # 跳过 common 与启动类
    if parts[0] == "common":
        skip.append((relp, "common package"))
        continue
    if fname == "PmServerApplication.java":
        skip.append((relp, "root app"))
        continue

    # 期望包前缀
    expect_pkg_prefix = "com.seven7m.kanban." + ".".join(parts[:-1])
    # 校验 package 声明与路径一致
    if pkg != expect_pkg_prefix:
        # 某些文件 package 可能与路径不符，记录但不阻断
        pass

    # 解析 module / 子模块 / layer
    # 结构: module / [submodule/] layer / Class.java
    # 或 module / Class.java (根目录零散类) 
    # 或 module / layer / Class.java
    mod = parts[0]
    sub = None
    layer = None
    if len(parts) == 2:
        # module/Class.java 根目录零散类
        layer = None
    elif len(parts) >= 3:
        if parts[1] in LAYER_MAP:
            layer = parts[1]
        elif parts[1] in BIZ_MODULES and parts[1] != mod:
            # module/submodule/... 可能是 module/submodule/layer
            # 例如 issue/attachment/IssueAttachment.java (layer缺失，直接是类) 
            # 或 issue/attachment/entity/X.java
            if len(parts) >= 4 and parts[2] in LAYER_MAP:
                sub = parts[1]
                layer = parts[2]
            else:
                # issue/attachment/IssueAttachment.java -> layer=entity (推断)
                sub = parts[1]
                layer = "entity"  # 默认，下面按类名调整
        else:
            layer = parts[1] if parts[1] in LAYER_MAP else None

    # 对根目录零散类推断层
    if layer is None:
        lower = cls.lower()
        if cls.endswith("Controller"):
            layer = "controller"
        elif cls.endswith("Service") or cls.endswith("ServiceImpl"):
            layer = "service"
        elif cls.endswith("Mapper"):
            layer = "mapper"
        elif cls.endswith("VO") or cls.endswith("DTO") or cls.endswith("Request") or cls.endswith("Response") or cls.endswith("Row") or "Dto" in cls:
            layer = "dto"
        else:
            layer = "entity"  # 枚举/常量
        # 这些零散类归入对应 module 的子包
        if mod == "dev":
            sub = "dev"  # 这里 mod 即 dev，sub 用于新包名
        # project 根目录零散类: module=project

    # 新包计算
    # 顶层 = layer
    # 业务子包 = module (+ "." + sub if sub)
    if mod == "dev" and layer is not None and "/" not in relp.replace("dev/","",1) and len(parts)==2:
        # dev/ExcelImportController.java -> controller.dev
        new_biz = "dev"
    else:
        new_biz = mod + (("." + sub) if sub else "")

    new_pkg = "com.seven7m.kanban." + layer + "." + new_biz
    # 子模块内如果还有更深层（如 issue/attachment/entity/X），sub 已含 attachment
    # 但新包业务部分应保留完整 submodule 链。sub 只取了一层，需补全：
    if sub is None and len(parts) >= 4 and parts[1] in BIZ_MODULES and parts[1]!=mod:
        pass

    new_fqn = new_pkg + "." + cls
    new_rel = layer + "/" + new_biz.replace(".","/") + "/" + fname
    new_path = os.path.join(ROOT, new_rel)

    # 冲突检测
    if new_fqn in mapping:
        conflicts.append((new_fqn, [mapping[new_fqn], relp]))
    mapping[new_fqn] = relp
    pathmap[fp] = new_path

print("=== SKIP (common/app) ===")
for s in skip:
    print(" ", s[0], "-", s[1])

print("\n=== CONFLICTS (same new FQN, would overwrite) ===")
for c in conflicts:
    print("  NEW:", c[0])
    print("     ->", c[1][0])
    print("     ->", c[1][1])

print("\n=== TOTAL files parsed:", len(files), "mapped:", len(pathmap), "skipped:", len(skip), "conflicts:", len(conflicts))

# 输出 pathmap 供后续使用
out = {
    "pathmap": {k: v for k, v in pathmap.items()},
    "skip": skip,
    "conflicts": conflicts,
}
with open(os.path.join(ROOT, "..", "..", "..", "..", "_migrate_map.json"), "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=2)
print("\nWrote _migrate_map.json")
