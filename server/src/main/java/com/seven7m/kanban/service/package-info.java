/**
 * 业务服务层。
 *
 * <p>本包的分层约定（2026-11 代码习惯整改后固化，{@code ArchitectureStyleTest} 会守住其中可静态检查的几条）：
 *
 * <ol>
 *   <li><b>接口 + impl</b>：{@code service/<域>/XxxService} 只放方法签名与契约说明，
 *       实现放 {@code service/<域>/impl/XxxServiceImpl}（带 {@code @Service}）。
 *       controller 只依赖接口，因此实现搬家不影响调用方。</li>
 *
 *   <li><b>MyBatis-Plus 复用范围为「实现类内部」</b>：单实体型服务让 impl
 *       {@code extends ServiceImpl<Mapper, Entity>} 以复用分页/计数/批量等模板方法；
 *       但<b>接口不 extends IService</b>——否则 {@code save/updateById/removeById}
 *       会变成公共 API，任何注入点都能绕过本域的权限与副作用校验
 *       （附件删除要清理磁盘文件、评论删除要校验作者、建项目要初始化看板与 OWNER 授权）。
 *       外部只能调用接口上声明的领域方法。</li>
 *
 *   <li><b>SQL 只出现在 mapper 层</b>：条件构造器上不允许出现
 *       {@code apply/setSql/last/inSql} 这类 SQL 片段。子查询写成相互的
 *       {@code Mapper#selectXxxIds(...)}，服务层只做 {@code in(ids)}；
 *       「取一条」用 {@code selectList(...).stream().findFirst()}；
 *       「置空列」用 {@code UpdateWrappers.clearToNull}。空集合必须先短路
 *       （PostgreSQL 的 {@code IN ()} 是语法错误），不要用 {@code 1 = 0} 绕过。</li>
 *
 *   <li><b>异常只用错误码枚举</b>：{@code throw new ServiceException(ErrorCode.XXX, args...)}，
 *       code 复用 HTTP 语义且不得随意改变（前端按 code 分支）。</li>
 *
 *   <li><b>方法签名不返回 Map</b>：「id → 名称」这类查询结果用
 *       {@link com.seven7m.kanban.common.domain.NameIndex}，其余用 VO/record。</li>
 *
 *   <li><b>魔法值常量化</b>：列名 → {@link com.seven7m.kanban.common.constant.WorkItemColumns}；
 *       可清空字段名 → {@link com.seven7m.kanban.common.constant.ClearFields}；
 *       动态动作码 → {@link com.seven7m.kanban.common.constant.ActivityActions}；
 *       状态/阶段/迭代状态 → {@link com.seven7m.kanban.common.enums} 下的枚举；
 *       分页边界 → {@link com.seven7m.kanban.common.constant.PageLimits}。</li>
 * </ol>
 */
package com.seven7m.kanban.service;
