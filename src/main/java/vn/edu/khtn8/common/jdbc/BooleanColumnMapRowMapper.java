package vn.edu.khtn8.common.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.jdbc.core.ColumnMapRowMapper;

/** Maps SQL predicates to Java booleans instead of driver-specific numeric values. */
public class BooleanColumnMapRowMapper extends ColumnMapRowMapper {
    private final String[] booleanColumns;

    public BooleanColumnMapRowMapper(String... booleanColumns) {
        this.booleanColumns = booleanColumns.clone();
    }

    @Override
    public Map<String, Object> mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        var row = super.mapRow(resultSet, rowNum);
        for (String column : booleanColumns) row.put(column, resultSet.getBoolean(column));
        return row;
    }
}