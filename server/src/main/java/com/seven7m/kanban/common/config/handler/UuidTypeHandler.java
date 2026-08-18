package com.seven7m.kanban.common.config.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 将 java.util.UUID 映射到 PostgreSQL 的 uuid 列类型。
 * 解决 MyBatis-Plus 默认未注册 UUID 类型处理器导致的
 * "Type handler was null on parameter mapping for property 'id'" 问题。
 */
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    private static final String PG_UUID_TYPE = "uuid";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType(PG_UUID_TYPE);
        pgObject.setValue(parameter.toString());
        ps.setObject(i, pgObject);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuid(rs.getObject(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuid(rs.getObject(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuid(cs.getObject(columnIndex));
    }

    private UUID toUuid(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID) {
            return (UUID) value;
        }
        if (value instanceof PGobject) {
            return UUID.fromString(((PGobject) value).getValue());
        }
        return UUID.fromString(value.toString());
    }
}
