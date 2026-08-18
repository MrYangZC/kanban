import pandas as pd
import openpyxl

path = r"e:\sevenM\河图\java\kanban\data\研发部工作任务管理系统.xlsx"
wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
print("SHEETS:", wb.sheetnames)
print("=" * 60)

# 找状态列的真实值分布（针对几个有代表性的 sheet）
for sh in ["禹舜-工作台（备份）", "禹舜-水文模型", "禹舜-简化模型", "部门工作（2026）", "物联数据中心（攀）"]:
    if sh not in wb.sheetnames:
        continue
    ws = wb[sh]
    # 读表头
    rows = list(ws.iter_rows(min_row=1, max_row=6, values_only=True))
    header = rows[0]
    print(f"\n[{sh}] 表头:")
    for i, h in enumerate(header):
        print(f"   col{i}: {h}")
    # 找含"状态"的列
    status_cols = [i for i, h in enumerate(header) if h and "状态" in str(h)]
    for sc in status_cols:
        vals = set()
        for r in ws.iter_rows(min_row=2, values_only=True):
            v = r[sc] if sc < len(r) else None
            if v is not None and str(v).strip() != "":
                vals.add(str(v).strip())
        print(f"   状态列 col{sc} 取值分布: {sorted(vals)}")

# 找负责人真实值样例
print("\n" + "=" * 60)
for sh in ["禹舜-工作台（备份）", "部门工作（2026）"]:
    ws = wb[sh]
    rows = list(ws.iter_rows(min_row=1, max_row=8, values_only=True))
    header = rows[0]
    lead_cols = [i for i, h in enumerate(header) if h and ("负责" in str(h) or "姓名" in str(h))]
    print(f"\n[{sh}] 负责人类列: {[(i, header[i]) for i in lead_cols]}")
    for r in rows[1:4]:
        print("   样例:", {header[i]: r[i] for i in lead_cols if i < len(r)})
