package com.seven7m.kanban.issue;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 将 Java String（JSON 文本）映射到 PostgreSQL 的 jsonb 列。
 * 仅通过 @TableField(typeHandler=...) 显式引用，不纳入全局 type-handlers-package 扫描，
 * 避免被误当成所有 String 字段的默认处理器（否则会导致 varchar = jsonb 类型冲突）。
 */
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    private static final String PG_JSONB_TYPE = "jsonb";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType(PG_JSONB_TYPE);
        pgObject.setValue(parameter);
        ps.setObject(i, pgObject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return readJsonb(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return readJsonb(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return readJsonb(cs.getObject(columnIndex));
    }

    private String readJsonb(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof PGobject) {
            return ((PGobject) value).getValue();
        }
        return value.toString();
    }
}
