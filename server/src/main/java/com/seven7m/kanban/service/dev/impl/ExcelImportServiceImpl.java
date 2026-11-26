package com.seven7m.kanban.service.dev.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.constant.IssueDefaults;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.dev.ExcelRow;
import com.seven7m.kanban.dto.issue.CreateIssueRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.project.AddMemberRequest;
import com.seven7m.kanban.dto.project.CreateProjectRequest;
import com.seven7m.kanban.dto.project.ProjectVO;
import com.seven7m.kanban.dto.user.CreateUserRequest;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.entity.workitem.Task;
import com.seven7m.kanban.mapper.dev.ImportMapper;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.mapper.workitem.TaskMapper;
import com.seven7m.kanban.service.dev.ExcelImportService;
import com.seven7m.kanban.service.project.ProjectMemberService;
import com.seven7m.kanban.service.project.ProjectService;
import com.seven7m.kanban.service.user.UserService;
import com.seven7m.kanban.service.workflow.WorkflowService;
import com.seven7m.kanban.service.workitem.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 一次性 Excel 导入编排（dev 专用）。
 * 直接复用现有 service 层，避免 2500+ 次 HTTP 调用与逐条鉴权。
 * 解析阶段用 POI 遍历 23 个 sheet，每个 sheet 建一个项目并导入任务。
 */
@Slf4j
@Service
// 仅 dev profile 注册：否则生产环境会白白加载 POI 依赖与这个只服务于一次性数据导入的 Bean
@Profile("dev")
public class ExcelImportServiceImpl implements ExcelImportService {

    /** 汇总统计 sheet（非任务列表），需跳过 */
    private static final String STATS_SHEET = "任务统计表";

    /** 导入统一按「任务」建工作项：Excel 里没有类型列，业务确认过全部是任务 */
    private static final String IMPORT_ISSUE_TYPE_CODE = ItemCategory.TASK.name();

    /** 查不到用户时新建占位账号的账号前缀（口令随机，实际不可登录） */
    private static final String IMPORT_USERNAME_PREFIX = "imp_";

    /** 占位账号取随机 UUID 前若干位，够同批导入内不撞号 */
    private static final int IMPORT_USERNAME_RANDOM_LENGTH = 8;

    /** 系统优先级档位（前端筛选面板同为 P0-P3）；已是该格式时原样保留 */
    private static final List<String> PRIORITY_LEVELS = List.of("P0", "P1", "P2", "P3");

    /** Excel 中文优先级「高」映射到的系统档位 */
    private static final String PRIORITY_HIGH = "P1";

    /** Excel 中文优先级「低/底」映射到的系统档位 */
    private static final String PRIORITY_LOW = "P3";

    /** Excel 里实际出现的日期写法，按这一顺序依次尝试；第三个与 ISO 同形，保留历史顺序便于对照日志 */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    /** 表头固定在第 1 行，数据从第 2 行开始 */
    private static final int HEADER_ROW_INDEX = 0;

    /** 描述里回填「类别」列的标签 */
    private static final String DESC_CATEGORY = "【类别】";

    /** 描述里回填「实际完成」列的标签 */
    private static final String DESC_ACTUAL_DATE = "【实际完成】";

    /** 描述里回填「备注」列的标签 */
    private static final String DESC_REMARK = "【备注】";

    /** 描述内的换行符 */
    private static final String DESC_LINE_BREAK = "\n";

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final TaskService taskService;
    private final IssueStatusMapper issueStatusMapper;
    private final TaskMapper taskMapper;
    private final UserService userService;
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;
    private final TransactionTemplate transactionTemplate;
    private final WorkflowService workflowService;

    public ExcelImportServiceImpl(ProjectService projectService,
                                  ProjectMemberService projectMemberService,
                                  TaskService taskService,
                                  IssueStatusMapper issueStatusMapper,
                                  TaskMapper taskMapper,
                                  UserService userService,
                                  SysUserMapper sysUserMapper,
                                  ProjectMapper projectMapper,
                                  TransactionTemplate transactionTemplate,
                                  WorkflowService workflowService) {
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.taskService = taskService;
        this.issueStatusMapper = issueStatusMapper;
        this.taskMapper = taskMapper;
        this.userService = userService;
        this.sysUserMapper = sysUserMapper;
        this.projectMapper = projectMapper;
        this.transactionTemplate = transactionTemplate;
        this.workflowService = workflowService;
    }

    @Override
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
                req.setIssueTypeCode(IMPORT_ISSUE_TYPE_CODE);
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
        Row header = sheet.getRow(HEADER_ROW_INDEX);
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
        for (int r = HEADER_ROW_INDEX + 1; r <= sheet.getLastRowNum(); r++) {
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

    /** 列名 -> 字段枚举（列名是 Excel 自己的词汇，保留字面量以免再加一层转换） */
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
        // 不用 selectOne(...).last("LIMIT 1")：取列表后取第一条，避免在 Wrapper 上拼 SQL
        SysUser exist = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getDisplayName, displayName))
                .stream()
                .findFirst()
                .orElse(null);
        if (exist != null) {
            return exist.getId();
        }
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername(IMPORT_USERNAME_PREFIX
                + UUID.randomUUID().toString().substring(0, IMPORT_USERNAME_RANDOM_LENGTH));
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
        } catch (ServiceException e) {
            // 已是成员（如 admin 是 OWNER）则忽略
            log.debug("[import] 成员已存在 projectId={} userId={}: {}", projectId, userId, e.getMessage());
        }
    }

    /**
     * 状态文本 → 状态 id。
     *
     * <p>状态表 {@code issue_status} 是**全局共享**的（没有 project_id 列，按 {@code workflow_status}
     * 关联到各工作流）。此前这里按 {@code project_id} 过滤，列根本不存在，运行期直接抛
     * {@code PSQLException: column "project_id" does not exist}，每一行都被 catch 记为失败，
     * 表现为「导入 0 条、失败 N 条」。
     *
     * <p>导入统一创建工作项类型 {@code TASK}，因此走 TASK 工作流的状态集合，再按 category 取第一个：
     * 待办→TASK_TODO、进行中→TASK_IN_PROGRESS、已完成→TASK_DONE。
     */
    private UUID resolveStatusId(UUID projectId, String statusText) {
        String cat = ImportMapper.mapStatus(statusText);
        List<IssueStatus> list = workflowService.statusesOf(projectId, ItemCategory.TASK).stream()
                .filter(s -> cat.equals(s.getCategory()))
                .toList();
        if (list.isEmpty()) {
            log.warn("[import] TASK 工作流缺少 category={} 的状态，无法导入 projectId={}", cat, projectId);
            throw new ServiceException(ErrorCode.WORKFLOW_STATUS_MISSING, cat);
        }
        return list.get(0).getId();
    }

    /** 优先级归一：已是 P0-P3 原样返回，中文「高/低」按语义映射，其余落缺省档 */
    private String normalizePriority(String p) {
        if (p == null || p.isBlank()) {
            return IssueDefaults.PRIORITY;
        }
        String up = p.trim().toUpperCase(Locale.ROOT);
        // 已是系统合法格式直接返回（前端选项 P0-P3）
        if (PRIORITY_LEVELS.contains(up)) {
            return up;
        }
        // 中文语义映射：高→P1，低/底→P3
        return switch (p.trim()) {
            case "高" -> PRIORITY_HIGH;
            case "低", "底" -> PRIORITY_LOW;
            default -> IssueDefaults.PRIORITY;
        };
    }

    private String buildDescription(ExcelRow r) {
        StringBuilder sb = new StringBuilder();
        if (r.category() != null && !r.category().isBlank()) {
            sb.append(DESC_CATEGORY).append(r.category()).append(DESC_LINE_BREAK);
        }
        if (r.actualDate() != null) {
            sb.append(DESC_ACTUAL_DATE).append(r.actualDate()).append(DESC_LINE_BREAK);
        }
        if (r.remark() != null && !r.remark().isBlank()) {
            sb.append(DESC_REMARK).append(r.remark());
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
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (Exception e) {
                // 依次尝试多种日期格式，失败属预期分支，但保留 trace 便于排查"某行日期被丢弃"的原因
                log.debug("日期解析失败，尝试下一种格式: value={} pattern={}", s, fmt);
            }
        }
        return null;
    }

    /** 单行待回填的父子关系：任务已建好，父任务列存的是标题或编号 */
    private record IssueBuildCtx(UUID issueId, String parentNo) {
    }
}
