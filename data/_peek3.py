import openpyxl, re
path = r"e:\sevenM\河图\java\kanban\data\研发部工作任务管理系统.xlsx"
wb = openpyxl.load_workbook(path, read_only=True, data_only=True)

kw = ["负责人", "状态", "优先级", "计划完成", "实际完成", "编号", "类别", "父任务", "备注", "工期", "基数", "任务名称"]

for sh in wb.sheetnames:
    ws = wb[sh]
    maxc = ws.max_column
    allvals = []
    has_kw_hit = set()
    # 扫描所有单元格文本，统计是否出现过关键词（作为表头或值）
    for row in ws.iter_rows(values_only=True):
        for v in row:
            if v is None: continue
            s = str(v)
            for k in kw:
                if k in s:
                    has_kw_hit.add(k)
    # 表头行（第1行）内容
    header = [v for v in next(ws.iter_rows(min_row=1, max_row=1, values_only=True))[:12] if v is not None]
    print(f"{sh[:22]:<24} cols={maxc:<3} header1={header} kwHit={sorted(has_kw_hit)}")
