package vn.edu.khtn8.common.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BooleanColumnMapRowMapperTest {
    private ResultSet row(Object completed, Object overdue) throws Exception {
        var resultSet = mock(ResultSet.class);
        var metadata = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(3);
        for (int index = 1; index <= 3; index++) {
            String name = new String[]{"id", "completed", "overdue"}[index - 1];
            when(metadata.getColumnLabel(index)).thenReturn(name);
        }
        when(resultSet.getObject(1)).thenReturn(42L);
        when(resultSet.getObject(2)).thenReturn(completed);
        when(resultSet.getObject(3)).thenReturn(overdue);
        return resultSet;
    }

    @Test void numericPredicatesBecomeBooleansAndOtherColumnsArePreserved() throws Exception {
        var resultSet = row(1, 0);
        when(resultSet.getBoolean("completed")).thenReturn(true);
        when(resultSet.getBoolean("overdue")).thenReturn(false);
        var mapped = new BooleanColumnMapRowMapper("completed", "overdue").mapRow(resultSet, 0);
        assertEquals(42L, mapped.get("id"));
        assertSame(Boolean.TRUE, mapped.get("completed"));
        assertSame(Boolean.FALSE, mapped.get("overdue"));
    }

    @Test void falseAndOverduePredicatesAreMappedIndependently() throws Exception {
        var resultSet = row(0L, 1L);
        when(resultSet.getBoolean("overdue")).thenReturn(true);
        var mapped = new BooleanColumnMapRowMapper("completed", "overdue").mapRow(resultSet, 0);
        assertSame(Boolean.FALSE, mapped.get("completed"));
        assertSame(Boolean.TRUE, mapped.get("overdue"));
    }

    @Test void booleanDriverValuesRemainBooleans() throws Exception {
        var resultSet = row(true, false);
        when(resultSet.getBoolean("completed")).thenReturn(true);
        var mapped = new BooleanColumnMapRowMapper("completed", "overdue").mapRow(resultSet, 0);
        assertSame(Boolean.TRUE, mapped.get("completed"));
        assertSame(Boolean.FALSE, mapped.get("overdue"));
    }

    @Test void nullPredicateFailsClosed() throws Exception {
        var mapped = new BooleanColumnMapRowMapper("completed").mapRow(row(null, 0), 0);
        assertSame(Boolean.FALSE, mapped.get("completed"));
    }
}