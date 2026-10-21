package com.seven7m.kanban.service.dev;

import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.dev.ExcelRow;
import com.seven7m.kanban.dto.issue.CreateIssueRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.mapper.dev.ImportMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.workitem.TaskMapper;
import com.seven7m.kanban.dto.project.AddMemberRequest;
import com.seven7m.kanban.dto.project.CreateProjectRequest;
import com.seven7m.kanban.dto.project.ProjectVO;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.workitem.Task;
import com.seven7m.kanban.service.project.ProjectMemberService;
import com.seven7m.kanban.service.project.ProjectService;
import com.seven7m.kanban.service.user.UserService;
import com.seven7m.kanban.service.workitem.TaskService;
import com.seven7m.kanban.dto.user.CreateUserRequest;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次性 Excel 导入编排（dev 专用）。
 * 直接复用现有 service 层，避免 2500+ 次 HTTP 调用与逐条鉴权。
 * 解析阶段用 POI 遍历 23 个 sheet，每个 sheet 建一个项目并导入任务。
 */
@Slf4j
@Service
public class ExcelImportService {

    /** 汇总统计 sheet（非任务列表），需跳过 */
    private static final String STATS_SHEET = "任务统计表";

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final TaskService taskService;
    private final IssueStatusMapper issueStatusMapper;
    private final TaskMapper taskMapper;
    private final UserService userService;
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;
    private final TransactionTemplate transactionTemplate;

    public ExcelImportService(ProjectService projectService,
                              ProjectMemberService projectMemberService,
                              TaskService taskService,
                              IssueStatusMapper issueStatusMapper,
                              TaskMapper taskMapper,
                              UserService userService,
                              SysUserMapper sysUserMapper,
                              ProjectMapper projectMapper,
                              TransactionTemplate transactionTemplate) {
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.taskService = taskService;
        this.issueStatusMapper = issueStatusMapper;
        this.taskMapper = taskMapper;
        this.userService = userService;
        this.sysUserMapper = sysUserMapper;
        this.projectMapper = projectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public ExcelImportResult run(String filePath) {
        ExcelImportResult result = new ExcelImportResult();
        UUID adminId = UserContext.getUserId();

        try (FileInputStream fis = new FileInputStream(Path.of(filePath).toFile());
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {

            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                String sheetName = sheet.getSheetName();
                try {
                    // 编程式事务：importSheet 由同类直接调用（self-invocation），@Transactional 注解不生效，
                    // 改用 TransactionTemplate 保证每个 sheet 的导入具备整体原子性
                    SheetResult sr = transactionTemplate.execute(tx -> importSheet(sheet, sheetName, adminId));
                    result.addSheet(sr);
                    log.info("[import] sheet='{}' 项目建='{}' 任务={} 失败={}",
                            sheetName, sr.projectCreated(), sr.issueCount(), sr.failCount());
                } catch (Exception e) {
                    log.error("[import] sheet='{}' 导入失败: {}", sheetName, e.getMessage(), e);
                    result.addSheet(SheetResult.failed(sheetName, e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("[import] 读取 Excel 失败: {}", filePath, e);
            result.setError(e.getMessage());
        }
        return result;
    }


    protected SheetResult importSheet(Sheet sheet, String sheetName, UUID adminId) {
        SheetResult sr = new SheetResult(sheetName);

        // 跳过汇总统计 sheet（非任务列表）
        if (STATS_SHEET.equals(sheetName)) {
            log.info("[import] 跳过统计 sheet='{}'", sheetName);
            return sr;
        }

        // 幂等：项目名已存在则视为已导入，跳过本 sheet（避免重复导入）
        long existing = projectMapper.selectCount(
                Wrappers.<Project>lambdaQuery()
                        .eq(Project::getName, sheetName));
        if (existing > 0) {
            log.info("[import] sheet='{}' 项目已存在，跳过", sheetName);
            return sr;
        }

        // 1. 解析行
        List<ExcelRow> rows = parseSheet(sheet);
        if (rows.isEmpty()) {
            return sr;
        }

        // 2. 建项目
        String baseKey = ImportMapper.toProjectKeyBase(sheetName);
        String projectKey = uniqueProjectKey(baseKey);
        ProjectVO project = projectService.create(buildProjectReq(sheetName, projectKey, adminId));
        sr.setProjectCreated(true);
        sr.setProjectKey(projectKey);

        UUID projectId = project.getId();

        // 3. 收集本 sheet 所有负责人，查/建用户并加入成员
        Map<String, UUID> nameToUserId = new HashMap<>();
        for (ExcelRow r : rows) {
            for (String name : ImportMapper.splitAssignees(r.assigneeRaw())) {
                if (!nameToUserId.containsKey(name)) {
                    UUID uid = resolveUser(name);
                    nameToUserId.put(name, uid);
                    addMemberIfAbsent(projectId, uid);
                }
            }
        }
        // 负责人（admin）已是 OWNER 成员（create 自动加），确保导入用户被加入
        sr.setUserCount(nameToUserId.size());

        // 4. 建任务；记录 title/issueNo -> issueId 供父子反查（父任务列存的是标题）
        Map<String, UUID> issueNoToId = new HashMap<>();
        Map<String, UUID> titleToId = new HashMap<>();
        List<IssueBuildCtx> pendingParents = new ArrayList<>();
        int idx = 0;
        for (ExcelRow r : rows) {
            idx++;
            if (r.title() == null || r.title().isBlank()) {
                continue;
            }
            try {
                UUID statusId = resolveStatusId(projectId, r.statusText());
                String[] assignees = ImportMapper.splitAssignees(r.assigneeRaw());
                UUID assigneeId = assignees.length > 0 ? nameToUserId.get(assignees[0]) : null;

                CreateIssueRequest req = new CreateIssueRequest();
                req.setProjectId(projectId);
                req.setStatusId(statusId);
                req.setIssueTypeCode("TASK");
                req.setTitle(r.title());
                req.setPriority(normalizePriority(r.priority()));
                req.setAssigneeId(assigneeId);
//                req.setDueDate(r.dueDate() == null ? null : r.dueDate().atStartOfDay());
                req.setDueDate(r.dueDate() == null ? null : r.dueDate());
                req.setDescription(buildDescription(r));

                IssueVO vo = taskService.create(req);
                sr.incIssue();
                if (r.title() != null && !r.title().isBlank()) {
                    titleToId.put(r.title().trim(), vo.getId());
                }
                if (r.issueNo() != null && !r.issueNo().isBlank()) {
                    issueNoToId.put(r.issueNo().trim(), vo.getId());
                }
                if (r.parentNo() != null && !r.parentNo().isBlank()) {
                    pendingParents.add(new IssueBuildCtx(vo.getId(), r.parentNo().trim()));
                }
            } catch (Exception e) {
                sr.incFail();
                log.warn("[import] sheet='{}' 第{}行任务创建失败 title='{}': {}",
                        sheetName, idx, r.title(), e.getMessage());
            }
        }

        // 5. 回填父子关系：父任务列存的是标题，先按标题反查，再按编号反查
        for (IssueBuildCtx ctx : pendingParents) {
            UUID parentId = titleToId.get(ctx.parentNo());
            if (parentId == null) {
                parentId = issueNoToId.get(ctx.parentNo());
            }
            if (parentId != null && !parentId.equals(ctx.issueId())) {
                Task task = taskMapper.selectById(ctx.issueId());
                if (task != null) {
                    task.setParentId(parentId);
                    taskMapper.updateById(task);
                }
            }
        }
        return sr;
    }

    /** 解析 sheet：表头驱动，按列名定位字段，统一处理所有任务 sheet */
    private List<ExcelRow> parseSheet(Sheet sheet) {
        List<ExcelRow> rows = new ArrayList<>();
        if (sheet.getPhysicalNumberOfRows() == 0) {
            return rows;
        }
        Row header = sheet.getRow(0);
        if (header == null) {
            return rows;
        }
        // 建立「列名 -> 列索引」映射
        Map<Field, Integer> col = new HashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String h = cellStr(header.getCell(c));
            if (h == null) {
                continue;
            }
            Field f = fieldOf(h);
            if (f != null && !col.containsKey(f)) {
                col.put(f, c);
            }
        }
        if (!col.containsKey(Field.TITLE)) {
            return rows; // 无任务列，非任务 sheet
        }
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String title = cellStr(row.getCell(col.get(Field.TITLE)));
            String issueNo = cellAt(row, col, Field.ISSUE_NO);
            String category = cellAt(row, col, Field.CATEGORY);
            String assignee = cellAt(row, col, Field.ASSIGNEE);
            String priority = cellAt(row, col, Field.PRIORITY);
            LocalDate due = dateAt(row, col, Field.DUE_DATE);
            LocalDate actual = dateAt(row, col, Field.ACTUAL_DATE);
            String status = cellAt(row, col, Field.STATUS);
            String remark = cellAt(row, col, Field.REMARK);
            String parentNo = cellAt(row, col, Field.PARENT);
            if (title == null && issueNo == null) {
                continue;
            }
            rows.add(new ExcelRow(title, issueNo, category, assignee, priority, due, actual, status, remark, parentNo));
        }
        return rows;
    }

    /** 列名 -> 字段枚举 */
    private Field fieldOf(String header) {
        return switch (header) {
            case "任务", "任务名称", "验证任务名称" -> Field.TITLE;
            case "编号", "任务编号" -> Field.ISSUE_NO;
            case "类别" -> Field.CATEGORY;
            case "负责人" -> Field.ASSIGNEE;
            case "优先级" -> Field.PRIORITY;
            case "计划完成时间" -> Field.DUE_DATE;
            case "实际完成时间" -> Field.ACTUAL_DATE;
            case "状态" -> Field.STATUS;
            case "备注" -> Field.REMARK;
            default -> header.startsWith("父任务") ? Field.PARENT : null;
        };
    }

    private String cellAt(Row row, Map<Field, Integer> col, Field f) {
        Integer idx = col.get(f);
        return idx == null ? null : cellStr(row.getCell(idx));
    }

    private LocalDate dateAt(Row row, Map<Field, Integer> col, Field f) {
        Integer idx = col.get(f);
        return idx == null ? null : cellDate(row.getCell(idx));
    }

    private enum Field {
        TITLE, ISSUE_NO, PARENT, CATEGORY, ASSIGNEE, PRIORITY, DUE_DATE, ACTUAL_DATE, STATUS, REMARK
    }

    private CreateProjectRequest buildProjectReq(String name, String key, UUID adminId) {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName(name);
        req.setProjectKey(key);
        req.setLeadUserId(adminId);
        return req;
    }

    private String uniqueProjectKey(String base) {
        String key = base;
        int i = 1;
        // 取已有项目 key 集合避免碰撞（简单线性追加序号）
        while (projectService.existsByKey(key)) {
            key = base + "_" + (i++);
        }
        return key;
    }

    /** 按 display_name 查；无则建用户（username=imp_<随机UUID前8位>） */
    private UUID resolveUser(String displayName) {
        SysUser exist = sysUserMapper.selectByDisplayName(displayName);
        if (exist != null) {
            return exist.getId();
        }
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("imp_" + UUID.randomUUID().toString().substring(0, 8));
        req.setDisplayName(displayName);
        // 导入场景不设默认密码：随机密码不可登录，如需启用由管理员另行重置
        req.setPassword(UUID.randomUUID().toString());
        return userService.create(req).getId();
    }

    private void addMemberIfAbsent(UUID projectId, UUID userId) {
        try {
            AddMemberRequest req = new AddMemberRequest();
            req.setUserId(userId);
            projectMemberService.add(projectId, req);
        } catch (com.seven7m.kanban.common.exception.ServiceException e) {
            // 已是成员（如 admin 是 OWNER）则忽略
            log.debug("[import] 成员已存在 projectId={} userId={}: {}", projectId, userId, e.getMessage());
        }
    }

    private UUID resolveStatusId(UUID projectId, String statusText) {
        String cat = ImportMapper.mapStatus(statusText);
        List<IssueStatus> list = issueStatusMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IssueStatus>()
                        .eq("project_id", projectId).eq("category", cat));
        if (list.isEmpty()) {
            // 兜底：取该项目第一个状态
            List<IssueStatus> all = issueStatusMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IssueStatus>()
                            .eq("project_id", projectId));
            if (all.isEmpty()) {
                throw new com.seven7m.kanban.common.exception.ServiceException("项目无可用状态: " + projectId);
            }
            return all.get(0).getId();
        }
        return list.get(0).getId();
    }

    private String normalizePriority(String p) {
        if (p == null || p.isBlank()) {
            return "P2";
        }
        String up = p.trim().toUpperCase();
        // 已是系统合法格式直接返回（前端选项 P0-P3）
        if (up.equals("P0") || up.equals("P1") || up.equals("P2") || up.equals("P3")) {
            return up;
        }
        // 中文语义映射：高→P1，低/底→P3
        return switch (p.trim()) {
            case "高" -> "P1";
            case "低", "底" -> "P3";
            default -> "P2";
        };
    }

    private String buildDescription(ExcelRow r) {
        StringBuilder sb = new StringBuilder();
        if (r.category() != null && !r.category().isBlank()) {
            sb.append("【类别】").append(r.category()).append("\n");
        }
        if (r.actualDate() != null) {
            sb.append("【实际完成】").append(r.actualDate()).append("\n");
        }
        if (r.remark() != null && !r.remark().isBlank()) {
            sb.append("【备注】").append(r.remark());
        }
        return sb.toString().isBlank() ? null : sb.toString().trim();
    }

    // ---------- POI 取值工具 ----------

    private String cellStr(Cell c) {
        if (c == null) {
            return null;
        }
        String s;
        switch (c.getCellType()) {
            case STRING -> s = c.getStringCellValue();
            case NUMERIC -> {
                double d = c.getNumericCellValue();
                if (d == Math.floor(d) && !org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(c)) {
                    s = String.valueOf((long) d);
                } else if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(c)) {
                    return cellDate(c).toString();
                } else {
                    s = String.valueOf(d);
                }
            }
            case BOOLEAN -> s = String.valueOf(c.getBooleanCellValue());
            default -> s = null;
        }
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private LocalDate cellDate(Cell c) {
        if (c == null) {
            return null;
        }
        if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate();
        }
        String s = cellStr(c);
        if (s == null) {
            return null;
        }
        for (DateTimeFormatter fmt : new DateTimeFormatter[]{
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy/M/d"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")}) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // ---------- 结果模型 ----------

    public record IssueBuildCtx(UUID issueId, String parentNo) {
    }

    @lombok.Getter
    public static class SheetResult {
        final String sheetName;
        boolean projectCreated;
        String projectKey;
        int issueCount;
        int failCount;
        int userCount;
        boolean ok = true;
        String error;

        SheetResult(String sheetName) {
            this.sheetName = sheetName;
        }

        static SheetResult failed(String sheetName, String error) {
            SheetResult r = new SheetResult(sheetName);
            r.ok = false;
            r.error = error;
            return r;
        }

        void incIssue() {
            issueCount++;
        }

        void incFail() {
            failCount++;
        }

        boolean projectCreated() {
            return projectCreated;
        }

        void setProjectCreated(boolean v) {
            projectCreated = v;
        }

        String projectKey() {
            return projectKey;
        }

        void setProjectKey(String v) {
            projectKey = v;
        }

        int issueCount() {
            return issueCount;
        }

        int failCount() {
            return failCount;
        }

        int userCount() {
            return userCount;
        }

        void setUserCount(int v) {
            userCount = v;
        }
    }

    public static class ExcelImportResult {
        final List<SheetResult> sheets = new ArrayList<>();
        String error;
        int totalProjects;
        int totalIssues;
        int totalFails;

        void addSheet(SheetResult sr) {
            sheets.add(sr);
            if (sr.projectCreated) {
                totalProjects++;
            }
            totalIssues += sr.issueCount;
            totalFails += sr.failCount;
        }

        void setError(String e) {
            error = e;
        }

        public List<SheetResult> getSheets() {
            return sheets;
        }

        public String getError() {
            return error;
        }

        public int getTotalProjects() {
            return totalProjects;
        }

        public int getTotalIssues() {
            return totalIssues;
        }

        public int getTotalFails() {
            return totalFails;
        }
    }
}
