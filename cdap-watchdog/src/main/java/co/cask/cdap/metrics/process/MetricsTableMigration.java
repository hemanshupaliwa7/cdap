/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.metrics.process;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.table.Row;
import co.cask.cdap.api.dataset.table.Scanner;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.data2.dataset2.lib.table.MetricsTable;
import co.cask.cdap.data2.util.TableId;
import co.cask.cdap.data2.util.hbase.HBaseDDLExecutorFactory;
import co.cask.cdap.data2.util.hbase.HBaseTableUtil;
import co.cask.cdap.metrics.store.MetricDatasetFactory;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.spi.hbase.HBaseDDLExecutor;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * perform table data migration from v2 metrics table to v3.
 */
public class MetricsTableMigration {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsTableMigration.class);

  private final MetricsTable v2MetricsTable;
  private final MetricsTable v3MetricsTable;
  private final Configuration hConf;
  private final CConfiguration cConf;

  public MetricsTableMigration(MetricDatasetFactory metricDatasetFactory, int resolution,
                               CConfiguration cConf, Configuration hConf) {
    this(metricDatasetFactory.getV2MetricsTable(resolution),
         metricDatasetFactory.getV3MetricsTable(resolution), cConf, hConf);
  }

  public MetricsTableMigration(MetricsTable v2MetricsTable, MetricsTable v3MetricsTable,
                               CConfiguration cConf, Configuration hConf) {
    this.v2MetricsTable = v2MetricsTable;
    this.v3MetricsTable = v3MetricsTable;
    this.cConf = cConf;
    this.hConf = hConf;
  }

  /**
   * returns true if old metrics data is still available for transfer
   * @return
   */
  public boolean isOldMetricsDataAvailable() {
    try (Scanner scanner = v2MetricsTable.scan(null, null, null)) {
      Row row = scanner.next();
      return (row != null);
    }
  }

  /**
   * transfers data from v2 metrics table to v3 metrics table, limit on number of records is specified by
   * maxRecordsToScan
   * @param maxRecordsToScan - limit on number of records scanned
   */
  public void transferData(int maxRecordsToScan) {
    try (Scanner scanner = v2MetricsTable.scan(null, null, null)) {
      LOG.trace("Starting scanning for Metrics Data Migration with {} max records", maxRecordsToScan);
      Row row;
      int recordsScanned = 0;
      while ((recordsScanned < maxRecordsToScan) && ((row = scanner.next()) != null)) {
        if (recordsScanned % 10 == 0) {
          LOG.trace("Scanned {} records in Metrics Data Migration", recordsScanned);
        }

        byte[] rowKey = row.getRow();
        Map<byte[], byte[]> columns = row.getColumns();
        //row-map
        SortedMap<byte[], SortedMap<byte[], Long>> rowGauges = new TreeMap<>(Bytes.BYTES_COMPARATOR);
        NavigableMap<byte[], NavigableMap<byte[], Long>> rowIncrements = new TreeMap<>(Bytes.BYTES_COMPARATOR);

        // column-map gauges
        List<byte[]> gaugeDeletes = new ArrayList<>();
        // column-map increments
        NavigableMap<byte[], Long> increments = new TreeMap<>(Bytes.BYTES_COMPARATOR);

        for (Map.Entry<byte[], byte[]> entry : columns.entrySet()) {
          // column is timestamp, do a get on the new table
          byte[] value = v3MetricsTable.get(rowKey, entry.getKey());
          if (value == null) {
            // we perform checkAndPut for the new value, if the value was updated by the new upcoming metrics,
            // if it was a gauge, the processing would have deleted the entry from the old table
            // if it was a incrment, we would perform increment in the next iteration of the migration run
            // so its safe to skip delete on checkAndPut failure
            if (v3MetricsTable.swap(rowKey, entry.getKey(), null, entry.getValue())) {
              gaugeDeletes.add(entry.getKey());
            }
          } else {
            increments.put(entry.getKey(), Bytes.toLong(entry.getValue()));
          }
        }

        byte[][] deletes = getByteArrayFromSets(increments.keySet(), gaugeDeletes);

        // delete entries from old table
        if (deletes.length > 0) {
          v2MetricsTable.delete(rowKey, deletes);
        }

        // increments
        if (!increments.isEmpty()) {
          rowIncrements.put(rowKey, increments);
          v3MetricsTable.increment(rowIncrements);
        }

        // break if we have exceeded max records to scan
        recordsScanned++;
      }
      LOG.debug("Migrated {} records from the metrics table {}", recordsScanned, v2MetricsTable);
    }
  }

  private byte[][] getByteArrayFromSets(Set<byte[]> incrementSet, List<byte[]> gaugeDelets) {
    byte [][] deletes = new byte[incrementSet.size() + gaugeDelets.size()][];
    int index = 0;
    for (byte[] column : incrementSet) {
      deletes[index++] = column;
    }
    for (byte[] column : gaugeDelets) {
      deletes[index++] = column;
    }
    return deletes;
  }

  private TableId getV2MetricsTableName(int resolution) {
    String v2TableName = cConf.get(Constants.Metrics.METRICS_TABLE_PREFIX,
                                   Constants.Metrics.DEFAULT_METRIC_TABLE_PREFIX) + ".ts." + resolution;
    return TableId.from(NamespaceId.SYSTEM.getNamespace(), v2TableName);
  }

  /**
   * check if v2 metrics table exists for the resolution
   * @param tableUtil hbase table util
   * @param resolution resolution of the metrics table to check
   * @return true if table exists; false otherwise
   */
  public boolean v2MetricsTableExists(HBaseTableUtil tableUtil, int resolution) {
    TableId tableId = getV2MetricsTableName(resolution);
    try {
      try (HBaseAdmin admin = new HBaseAdmin(hConf)) {
        TableId hBaseTableId =
          tableUtil.createHTableId(new NamespaceId(tableId.getNamespace()), tableId.getTableName());
        boolean doesExist  = tableUtil.tableExists(admin, hBaseTableId);
        LOG.debug("Table {} exists : {}", hBaseTableId.getTableName(), doesExist);
        return doesExist;
      }
    } catch (IOException e) {
      LOG.warn("Exception while checking table exists", e);
    }
    return false;
  }

  /**
   * delete v2 metrics table for the resolution
   * @param ddlExecutorFactory HBaseDDLExecutorFactory
   * @param tableUtil hbase table util
   * @param resolution resolution of metrics table to delete
   * @return true if deletion is successful; false otherwise
   */
  public boolean deleteV2MetricsTable(HBaseDDLExecutorFactory ddlExecutorFactory,
                                      HBaseTableUtil tableUtil, int resolution) {
    TableId tableId = getV2MetricsTableName(resolution);
    LOG.trace("Looking to delete table {}", tableId);
    try {
      try (HBaseDDLExecutor ddlExecutor = ddlExecutorFactory.get(); HBaseAdmin admin = new HBaseAdmin(hConf)) {
        TableId hBaseTableId =
          tableUtil.createHTableId(new NamespaceId(tableId.getNamespace()), tableId.getTableName());
        if (tableUtil.tableExists(admin, hBaseTableId)) {
          LOG.trace("Found table {}, going to delete", hBaseTableId);
          tableUtil.dropTable(ddlExecutor, hBaseTableId);
          LOG.debug("Deleted table {}", hBaseTableId);
          return true;
        }
      }
    } catch (IOException e) {
      LOG.warn("Exception while deleting table", e);
    }
    return false;
  }
}
