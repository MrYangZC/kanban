#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
后端目录按层纵向重构迁移脚本
旧: com.seven7m.kanban.<module>.<layer>.<...>
新: com.seven7m.kanban.<layer>.<module>[.<submodule>...]

用法:
  python _migrate.py --root "<ROOT>"            # dry-run, 打印映射与冲突
  python _migrate.py --root "<ROOT>" --apply    # 真实执行
ROOT 形如 e:\sevenM\河图\java\kanban\server\src\main\java\com\seven7m\kanban
"""
import os, re, sys, json, shutil

ROOT = None  # 由参数传入
BASE = None  # .../com/seven7m/kanban 的上级 = src/main/java

# 业务模块根目录（kanban 下，排除 common）
BIZ_MODULES = ["issue","risk","project","board","user","workflow","rbac",
               "announcement","dashboard","issuestatus","milestone","sprint","workspace","dev"]
LAYER_DIRS = {"controller","service","mapper","entity","dto"}

# issue 主层旧版 4 组同名类（删旧留新）-> 将被删除，不迁移
DELETE_OLD_ISSUE = {
    "issue/entity/IssueAttachment.java",
    "issue/entity/IssueComment.java",
    "issue/entity/IssueLink.java",
    "issue/entity/IssueTag.java",
    "issue/controller/IssueAttachmentController.java",
    "issue/controller/IssueCommentController.java",
    "issue/controller/IssueLinkController.java",
    "issue/controller/IssueTagController.java",
    "issue/service/IssueAttachmentService.java",
    "issue/service/IssueCommentService.java",
    "issue/service/IssueLinkService.java",
    "issue/service/IssueTagService.java",
    "issue/mapper/IssueAttachmentMapper.java",
    "issue/mapper/IssueCommentMapper.java",
    "issue/mapper/IssueLinkMapper.java",
    "issue/mapper/IssueTagMapper.java",
}

def parse_package(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = re.match(r"\s*package\s+([\w\.]+)\s*;", line)
            if m:
                return m.group(1)
    return None

def module_of(pkg_parts):
    # pkg_parts 不含 'com.seven7m.kanban' 前缀
    return pkg_parts[0]

def compute_new(pkg):
    """根据旧 package 计算新 package。返回 (new_pkg, new_biz_relpath) 或 None(跳过)。"""
    # 去除前缀 com.seven7m.kanban
    rest = pkg[len("com.seven7m.kanban."):]
    parts = rest.split(".")
    mod = parts[0]
    if mod == "common":
        return None  # 跳过
    # 结构: module [submodule ...] layer [sub...]  Class
    # 我们需要找到 'layer' 的位置：它是 LAYER_DIRS 中第一个出现的词
    # 但 issue.attachment.IssueAttachment 没有 layer 词（直接是类）
    # 规则：从 parts[1:] 起，找第一个属于 LAYER_DIRS 的 => 那是 layer
    # 若找不到(如 issue.attachment.X)，则整串除最后类名外，前半是 biz，layer 推断
    # 实际上 package 不含类名，parts 全是包路径
    # 例: issue.attachment -> parts=['issue','attachment'] 无 layer
    #     issue.attachment.entity -> ['issue','attachment','entity'] layer=entity biz=issue.attachment
    #     risk.controller -> ['risk','controller'] layer=controller biz=risk
    layer = None
    layer_idx = None
    for i, p in enumerate(parts[1:], start=1):
        if p in LAYER_DIRS:
            layer = p
            layer_idx = i
            break
    if layer is None:
        # 无 layer 词，例如 issue.attachment / issue.attachment.entity? 后者已被上面捕获
        # 纯 biz 包（类直接放在 module.submodule）
        biz = ".".join(parts)          # issue.attachment
        # 推断 layer: 需要看文件名；这里无法判断，调用方按文件名补
        return ("__INFER__", biz)
    biz = ".".join(parts[:layer_idx])  # module[.submodule...]
    new_pkg = f"com.seven7m.kanban.{layer}.{biz}"
    return (new_pkg, biz)

def infer_layer_from_class(cls):
    if cls.endswith("Controller"):
        return "controller"
    if cls.endswith("Service") or cls.endswith("ServiceImpl"):
        return "service"
    if cls.endswith("Mapper"):
        return "mapper"
    if (cls.endswith("VO") or cls.endswith("DTO") or cls.endswith("Request")
            or cls.endswith("Response") or cls.endswith("Row") or cls.endswith("Body")
            or "Dto" in cls or cls == "ExcelRow"):
        return "dto"
    return "entity"

def build_old2new(mapping, to_delete, deleted_old2new_extra):
    """构建 旧FQN -> 新FQN 字典，用于全局 import 替换。"""
    old2new = {v[0]: v[1] for v in mapping.values()}
    # 类名 -> 新版FQN（新版已迁移到 issue.<sub> 子包）
    cls_to_newfqn = {}
    for new_fqn, (of, nf, fp, np, npkg) in mapping.items():
        c = new_fqn.split(".")[-1]
        cls_to_newfqn.setdefault(c, new_fqn)
    for fp, relp in to_delete:
        if relp.count("/") < 2:
            # JsonbTypeHandler 等 2 段路径：引用映射已在 deleted_old2new_extra 处理
            continue
        pkg = parse_package(fp)
        if not pkg:
            continue
        cls = os.path.basename(fp)[:-5]
        of = pkg + "." + cls
        # 旧版 issue/<layer>/X 的引用指向对应新版 issue.<sub>/<layer>/X
        nf = cls_to_newfqn.get(cls)
        if nf is None:
            print(f"  WARN: 旧版 {relp} 找不到对应新版映射，跳过引用重写")
            continue
        old2new[of] = nf
    old2new.update(deleted_old2new_extra)
    return old2new

# JsonbTypeHandler 特殊：归入 common.config.handler (非 entity.issue)
JSONB_SPECIAL = "issue/JsonbTypeHandler.java"  # 相对 ROOT

def main():
    global ROOT, BASE
    args = sys.argv[1:]
    apply = False
    root = None
    for i, a in enumerate(args):
        if a == "--apply":
            apply = True
        elif a == "--root":
            root = args[i+1]
    if not root:
        print("ERROR: --root required")
        sys.exit(1)
    ROOT = root
    BASE = os.path.dirname(os.path.dirname(ROOT))  # .../src/main  (ROOT=.../src/main/java/com/seven7m/kanban)
    # 实际上 BASE 仅用于放日志/plan，用 ROOT 的父目录更稳
    BASE = os.path.dirname(ROOT)  # src/main/java

    files = []
    for dp, _, fns in os.walk(ROOT):
        for fn in fns:
            if fn.endswith(".java"):
                files.append(os.path.join(dp, fn))

    mapping = {}       # old_fqn -> (new_fqn, old_path, new_path, new_pkg)
    to_delete = []     # 要删除的旧文件路径
    conflicts = []
    skip = []
    fqn_to_path_new = {}  # new_fqn -> old_path (for conflict)
    deleted_old2new_extra = {}  # issue.JsonbTypeHandler -> common 版 等特例

    for fp in files:
        relp = os.path.relpath(fp, ROOT).replace("\\", "/")
        cls = os.path.basename(fp)[:-5]
        if relp == "PmServerApplication.java":
            skip.append((relp, "root app"))
            continue
        if relp in DELETE_OLD_ISSUE:
            to_delete.append((fp, relp))
            continue
        if relp.startswith("common/"):
            skip.append((relp, "common"))
            continue
        pkg = parse_package(fp)
        if not pkg or not pkg.startswith("com.seven7m.kanban."):
            skip.append((relp, "no-pkg"))
            continue
        old_fqn = pkg + "." + cls
        # JsonbTypeHandler 特殊处理：common 包已存在同名版，issue 版为重复副本。
        # 删除 issue 版，引用统一指向 common.config.handler.JsonbTypeHandler。
        if relp == JSONB_SPECIAL:
            to_delete.append((fp, relp))
            # 记录引用映射，供全局替换使用
            deleted_old2new_extra[old_fqn] = "com.seven7m.kanban.common.config.handler." + cls
            continue
        res = compute_new(pkg)
        if res is None:
            skip.append((relp, "common/skip"))
            continue
        new_pkg, biz = res
        if new_pkg == "__INFER__":
            # 无 layer 词 -> 用类名推断
            layer = infer_layer_from_class(cls)
            new_pkg = f"com.seven7m.kanban.{layer}.{biz}"
        new_fqn = new_pkg + "." + cls
        new_rel = new_pkg[len("com.seven7m.kanban."):].replace(".", "/") + "/" + os.path.basename(fp)
        new_path = os.path.join(ROOT, new_rel)
        if new_fqn in fqn_to_path_new:
            conflicts.append((new_fqn, fqn_to_path_new[new_fqn], relp))
        fqn_to_path_new[new_fqn] = relp
        mapping[new_fqn] = (old_fqn, new_fqn, fp, new_path, new_pkg)

    # 输出报告
    print(f"解析文件: {len(files)}")
    print(f"迁移: {len(mapping)} | 删除(旧版): {len(to_delete)} | 跳过: {len(skip)} | 冲突: {len(conflicts)}")
    print("\n--- 待删除(旧版 issue 同名类) ---")
    for fp, relp in to_delete:
        print("  DEL", relp)
    print("\n--- 冲突(同 new FQN) ---")
    for c in conflicts:
        print("  CONFLICT", c[0], "<-", c[1], "&&", c[2])
    print("\n--- 跳过 ---")
    for s in skip:
        print("  SKIP", s[0], "-", s[1])

    if conflicts:
        print("\n存在冲突，dry-run 中止（不执行）。请先解决冲突。")
        return

    if "--dump" in args:
        old2new = build_old2new(mapping, to_delete, deleted_old2new_extra)
        subset = {k: v for k, v in old2new.items() if "IssueAttachment" in k or "IssueAttachment" in v}
        print("=== old2new IssueAttachment entries ===")
        for k, v in sorted(subset.items()):
            print(f"  {k}  ->  {v}")
        print("=== total entries:", len(old2new), "===")
        return

    if not apply:
        print("\n[DRY-RUN] 未执行任何改动。加 --apply 执行。")
        out = {k: {"old_fqn": v[0], "new_fqn": v[1], "old": os.path.relpath(v[2], ROOT),
                   "new": os.path.relpath(v[3], ROOT), "new_pkg": v[4]} for k, v in mapping.items()}
        with open(os.path.join(BASE, "_migrate_plan.json"), "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print("已写出 _migrate_plan.json")
        return

    # ---- 真实执行 ----
    log_path = os.path.join(BASE, "migration.log")
    old2new = build_old2new(mapping, to_delete, deleted_old2new_extra)

    with open(log_path, "w", encoding="utf-8") as log:
        # 1) 删除旧版
        for fp, relp in to_delete:
            try:
                os.remove(fp)
                log.write(f"DELETE {relp}\n")
            except Exception as e:
                log.write(f"DELETE-FAIL {relp}: {e}\n")
        # 2) 移动 + 改 package
        for fqn, (of, nf, fp, np, npkg) in mapping.items():
            os.makedirs(os.path.dirname(np), exist_ok=True)
            with open(fp, encoding="utf-8") as f:
                content = f.read()
            content = re.sub(r"^\s*package\s+[\w\.]+\s*;",
                             f"package {npkg};", content, count=1, flags=re.MULTILINE)
            with open(np, "w", encoding="utf-8") as f:
                f.write(content)
            log.write(f"MOVE {os.path.relpath(fp,ROOT)} -> {os.path.relpath(np,ROOT)} | pkg {npkg}\n")
            if os.path.abspath(fp) != os.path.abspath(np):
                try:
                    os.remove(fp)
                except Exception as e:
                    log.write(f"  OLD-DEL-FAIL {fp}: {e}\n")
        # 3) 全局 import / 全限定名替换
        #    按 FQN 长度降序替换，避免短前缀误匹配
        items = sorted(old2new.items(), key=lambda kv: len(kv[0]), reverse=True)
        all_files = []
        for dp, _, fns in os.walk(ROOT):
            for fn in fns:
                if fn.endswith(".java"):
                    all_files.append(os.path.join(dp, fn))
        for fp in all_files:
            with open(fp, encoding="utf-8") as f:
                content = f.read()
            original = content
            for of, nf in items:
                # 仅整词边界替换（避免 a.b.C 误伤 a.b.CX）
                content = re.sub(r"(?<![\\w.])" + re.escape(of) + r"(?![\\w.])", nf, content)
            if content != original:
                with open(fp, "w", encoding="utf-8") as f:
                    f.write(content)
                log.write(f"REWRITE-IMPORTS {os.path.relpath(fp, ROOT)}\n")
        log.write("DONE\n")
    print("APPLY 完成。详见 migration.log")

if __name__ == "__main__":
    main()
