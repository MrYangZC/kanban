import re
# 模拟真实替换，复现 IssueService 损坏
text = "import com.seven7m.kanban.issue.entity.IssueAttachment;"
of = "com.seven7m.kanban.issue.entity.IssueAttachment"
nf = "com.seven7m.kanban.entity.issue.attachment.IssueAttachment"
print("BEFORE:", text)
res = re.sub(r"(?<![\\w.])" + re.escape(of) + r"(?![\\w.])", nf, text)
print("AFTER :", res)

# 关键测试：如果 of 本身含错误（如被 parse_package 污染成 None.x）
# 模拟：如果旧文件被提前删除，parse_package 返回 None
of2 = None  # 这会在循环里报错，说明不是这里
