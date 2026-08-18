import openpyxl, re
path = r"e:\sevenM\河图\java\kanban\data\研发部工作任务管理系统.xlsx"
wb = openpyxl.load_workbook(path, read_only=True, data_only=True)

# 检查大纲 sheet 是否有缩进（前导空格/制表符）表示层级
for sh in ["禹舜-底座", "部门工作（2026）", "物联数据中心（攀）", "禹舜-工作台（备份）"]:
    ws = wb[sh]
    print(f"\n===== {sh} =====")
    for ri, row in enumerate(ws.iter_rows(min_row=1, max_row=15, values_only=True), start=1):
        v = row[0] if row else None
        if v is None: 
            print(f"  r{ri}: <empty>")
            continue
        s = str(v)
        lead = len(s) - len(s.lstrip())
        marker = ""
        if s.strip().startswith(("一、","二、","1、","1.","（","(一)")):
            marker = " [LIST-MARKER]"
        print(f"  r{ri}: indent={lead}{marker} | {s.strip()[:40]}")
