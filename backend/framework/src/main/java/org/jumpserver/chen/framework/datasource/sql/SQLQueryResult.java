package org.jumpserver.chen.framework.datasource.sql;

import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import org.jumpserver.chen.framework.utils.HexUtils;
import com.github.freva.asciitable.AsciiTable;
import lombok.Data;
import org.jumpserver.chen.framework.datasource.entity.resource.Field;
import org.jumpserver.chen.framework.jms.acl.ACLResult;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Data
public class SQLQueryResult {
    private String sql;
    private int total = -1;
    private boolean paged;
    private int updateCount;
    private boolean hasResultSet = true;
    private List<Field> fields = new ArrayList<>();
    private List<List<Object>> data = new ArrayList<>();

    private Time startTime;
    private Time endTime;
    private Time queryFinishedTime;
    private Time fetchFinishedTime;

    private ACLResult aclResult;

    public long getHeaderSize() {
        long totalBytes = 0;
        for (Field field : fields) {
            if (field != null && field.getName() != null) {
                totalBytes += field.getName().getBytes(StandardCharsets.UTF_8).length;
            }
        }
        return totalBytes;
    }

    public long getDataSize() {
        long totalBytes = 0;
        for (List<Object> row : data) {
            if (row == null) continue;
            for (Object value : row) {
                try {
                    String valueStr = String.valueOf(value);
                    if (value instanceof byte[]) {
                        valueStr = HexUtils.bytesToHex((byte[]) value);
                    }
                    totalBytes += valueStr.getBytes(StandardCharsets.UTF_8).length;
                } catch (Exception e) {
                    log.error("Compute data size failed: ", e);
                    return -1;
                }
            }
        }
        return totalBytes;
    }

    public long getTotalTimeUsed() {
        if (this.hasResultSet) {
            return this.fetchFinishedTime.getTime() - this.startTime.getTime();
        }
        return this.endTime.getTime() - this.startTime.getTime();
    }

    public long getQueryTimeUsed() {
        return this.queryFinishedTime.getTime() - this.startTime.getTime();
    }

    public long getFetchTimeUsed() {
        return this.fetchFinishedTime.getTime() - this.queryFinishedTime.getTime();
    }

    public SQLQueryResult(String sql) {
        this.sql = sql;
    }

    public String getOutput() {
        if (!this.hasResultSet) {
            return String.format("Query OK, %d rows affected", this.updateCount);
        }
        String[] headers = fields.stream().map(Field::getName).toArray(String[]::new);
        Object[][] data = this.data.stream().map(List::toArray).toArray(Object[][]::new);
        return AsciiTable.getTable(headers, data);
    }

}
