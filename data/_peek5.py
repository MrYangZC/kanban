import openpyxl
path = r"e:\sevenM\河图\java\kanban\data\研发部工作任务管理系统.xlsx"
wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
ws = wb["禹舜-水库调度"]
print("dims:", ws.max_row, "x", ws.max_column)
for ri, row in enumerate(ws.iter_rows(min_row=1, max_row=4, values_only=True), start=1):
    print(f"--- r{ri} ---")
    for ci, v in enumerate(row):
        if v is not None and str(v).strip() != "":
            print(f"  col{ci}: {repr(v)}")
