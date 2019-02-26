/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.phoenix.iterate;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.PrivateCellUtil;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.snapshot.RestoreSnapshotHelper;
import org.apache.hadoop.hbase.util.FSUtils;
import org.apache.phoenix.mapreduce.util.PhoenixConfigurationUtil;
import org.apache.phoenix.monitoring.ScanMetricsHolder;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.util.ServerUtil;


/**
 * Iterator to scan over a HBase snapshot based on input HBase Scan object.
 * This iterator is generated by Phoenix during the query plan scan generation,
 * hence it will include scan attributes and custom filters.
 * Restores HBase snapshot and determines the valid regions that intersect
 * with the input Scan boundaries. Launches SnapshotScanner for each of them.
 * Deletes the restored snapshot when iterator is closed.
 */
public class TableSnapshotResultIterator implements ResultIterator {

  private static final Log LOG = LogFactory.getLog(TableSnapshotResultIterator.class);

  private final Scan scan;
  private ResultIterator scanIterator;
  private Configuration configuration;
  private final ScanMetricsHolder scanMetricsHolder;
  private Tuple lastTuple = null;
  private static final ResultIterator UNINITIALIZED_SCANNER = ResultIterator.EMPTY_ITERATOR;
  private List<RegionInfo> regions;
  private TableDescriptor htd;
  private String snapshotName;

  private Path restoreDir;
  private Path rootDir;
  private FileSystem fs;
  private int currentRegion;
  private boolean closed = false;

  public TableSnapshotResultIterator(Configuration configuration, Scan scan, ScanMetricsHolder scanMetricsHolder)
      throws IOException {
    this.configuration = configuration;
    this.currentRegion = -1;
    this.scan = scan;
    this.scanMetricsHolder = scanMetricsHolder;
    this.scanIterator = UNINITIALIZED_SCANNER;
    this.restoreDir = new Path(configuration.get(PhoenixConfigurationUtil.RESTORE_DIR_KEY),
        UUID.randomUUID().toString());
    this.snapshotName = configuration.get(
        PhoenixConfigurationUtil.SNAPSHOT_NAME_KEY);
    this.rootDir = FSUtils.getRootDir(configuration);
    this.fs = rootDir.getFileSystem(configuration);
    init();
  }

  private void init() throws IOException {
    RestoreSnapshotHelper.RestoreMetaChanges meta =
        RestoreSnapshotHelper.copySnapshotForScanner(this.configuration, this.fs,
            this.rootDir, this.restoreDir, this.snapshotName);
    List<RegionInfo> restoredRegions = meta.getRegionsToAdd();
    this.htd = meta.getTableDescriptor();
    this.regions = new ArrayList<RegionInfo>(restoredRegions.size());

    for (RegionInfo restoredRegion : restoredRegions) {
      if (isValidRegion(restoredRegion)) {
        this.regions.add(restoredRegion);
      }
    }

    this.regions.sort(RegionInfo.COMPARATOR);
    LOG.info("Initialization complete with " + regions.size() + " valid regions");
  }

  /**
   * Exclude offline split parent regions and
   * regions that don't intersect with provided scan
   */
  private boolean isValidRegion(RegionInfo hri) {
    if (hri.isOffline() && (hri.isSplit() || hri.isSplitParent())) {
      return false;
    }
    return PrivateCellUtil.overlappingKeys(scan.getStartRow(), scan.getStopRow(),
            hri.getStartKey(), hri.getEndKey());
  }

  public boolean initSnapshotScanner() throws SQLException {
    if (closed) {
      return true;
    }
    ResultIterator delegate = this.scanIterator;
    if (delegate == UNINITIALIZED_SCANNER) {
      ++this.currentRegion;
      if (this.currentRegion >= this.regions.size())
        return false;
      try {
        RegionInfo hri = regions.get(this.currentRegion);
        this.scanIterator =
            new ScanningResultIterator(new SnapshotScanner(configuration, fs, restoreDir, htd, hri, scan),
                scan, scanMetricsHolder);
      } catch (Throwable e) {
        throw ServerUtil.parseServerException(e);
      }
    }
    return true;
  }

  @Override
  public Tuple next() throws SQLException {
    while (true) {
      if (!initSnapshotScanner())
        return null;
      try {
        lastTuple = scanIterator.next();
        if (lastTuple != null) {
          ImmutableBytesWritable ptr = new ImmutableBytesWritable();
          lastTuple.getKey(ptr);
          return lastTuple;
        }
      } finally {
        if (lastTuple == null) {
          scanIterator.close();
          scanIterator = UNINITIALIZED_SCANNER;
        }
      }
    }
  }

  @Override
  public void close() throws SQLException {
    closed = true; // ok to say closed even if the below code throws an exception
    try {
      scanIterator.close();
      fs.delete(this.restoreDir, true);
    } catch (IOException e) {
      throw ServerUtil.parseServerException(e);
    } finally {
      scanIterator = UNINITIALIZED_SCANNER;
    }
  }

  @Override
  public void explain(List<String> planSteps) {
    // noop
  }

}