import pandas as pd

path = r"e:\sevenM\河图\java\kanban\data\研发部工作任务管理系统.xlsx"
xl = pd.ExcelFile(path)
print("SHEETS:", xl.sheet_names)
for name in xl.sheet_names:
    df = xl.parse(name)
    # 负责人唯一值
    owner_cols = [c for c in df.columns if '负责人' in str(c)]
    owners = set()
    for c in owner_cols:
        owners.update(df[c].dropna().astype(str).tolist())
    print(f"\n[{name}] 行数={len(df)} 负责人={sorted(owners)}")
