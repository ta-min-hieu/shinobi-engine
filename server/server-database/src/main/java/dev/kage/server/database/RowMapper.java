package dev.kage.server.database;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Ánh xạ 1 dòng {@link ResultSet} hiện tại (con trỏ đã ở đúng dòng) thành 1 object domain. */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet resultSet) throws SQLException;
}
