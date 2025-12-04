/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.regionserver;

import static org.apache.phoenix.util.ScanUtil.getPageSizeMsForRegionScanner;
import static org.apache.phoenix.util.ScanUtil.isDummy;

import java.util.List;
import java.util.Map;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.metrics.ServerSideScanMetrics;
import org.apache.phoenix.util.EnvironmentEdgeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ScannerContext has all methods package visible. To properly update the context progress for our
 * scanners we need this helper
 */
public class PhoenixScannerContext extends ScannerContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(PhoenixScannerContext.class);

  private final ScannerContext delegate;
  // Perf optimization to avoid having to call timeLimitReached from each nested scanner
  //TODO is this worth it ?
  private boolean timeLimitCache = false;
  private long pageTimeDeadline = -1;
  // tracks the start time of the rpc on the server for server paging
  private final long startTime;

  @Override
  public boolean isTrackingMetrics() {
    return delegate.isTrackingMetrics();
  }
  
  @Override
  public ServerSideScanMetrics getMetrics() {
    return delegate.getMetrics();
  }

  @Override
  boolean getKeepProgress() {
    return delegate.getKeepProgress();
  }
  
  @Override
  void setKeepProgress(boolean keepProgress) {
    delegate.setKeepProgress(keepProgress);
  }
  
  @Override
  void incrementBatchProgress(int batch) {
    delegate.incrementBatchProgress(batch);
  }
  
  @Override
  void incrementSizeProgress(long dataSize, long heapSize) {
    delegate.incrementSizeProgress(dataSize, heapSize);
  }
  
  @Deprecated
  @Override
  void updateTimeProgress() {
    delegate.updateTimeProgress();
  }

  @Override
  int getBatchProgress() {
    return delegate.getBatchProgress();
  }

  @Override
  long getDataSizeProgress() {
    return delegate.getDataSizeProgress();
  }

  @Override
  long getHeapSizeProgress() {
    return delegate.getHeapSizeProgress();
  }
  
  @Deprecated
  @Override
  long getTimeProgress() {
    return delegate.getTimeProgress();
  }
  
  @Deprecated
  @Override
  void setProgress(int batchProgress, long sizeProgress, long heapSizeProgress, long timeProgress) {
    delegate.setProgress(batchProgress, sizeProgress, heapSizeProgress, timeProgress);
  }

  @Override
  void setProgress(int batchProgress, long sizeProgress, long heapSizeProgress) {
    delegate.setProgress(batchProgress, sizeProgress, heapSizeProgress);
  }

  @Override
  void setSizeProgress(long dataSizeProgress, long heapSizeProgress) {
    delegate.setSizeProgress(dataSizeProgress, heapSizeProgress);
  }

  @Override
  void setBatchProgress(int batchProgress) {
    delegate.setBatchProgress(batchProgress);
  }
  
  /**
   * @deprecated will be removed in 3.0
   */
  @Deprecated
  @Override
  void setTimeProgress(long timeProgress) {
    delegate.setTimeProgress(timeProgress);
  }

  @Override
  void clearProgress() {
    //FIXME clear pageTimeDeadline ?
    delegate.clearProgress();
  }

  @Override
  NextState setScannerState(NextState state) {
    return delegate.setScannerState(state);
  }

  @Override
  boolean mayHaveMoreCellsInRow() {
    return delegate.mayHaveMoreCellsInRow();
  }

  @Override
  boolean hasBatchLimit(LimitScope checkerScope) {
    return delegate.hasBatchLimit(checkerScope);
  }

  @Override
  boolean hasSizeLimit(LimitScope checkerScope) {
    return delegate.hasSizeLimit(checkerScope);
  }

  @Override
  boolean hasTimeLimit(LimitScope checkerScope) {
    // Phoenix effectively always uses LimitScope.BETWEEN_ROWS
    // PagingFilter used to check after each cell, but it did throw away any results it timed out
    // between cells. 
    // Using LimitScope.BETWEEN_ROWS here makes the behaviour less real-time.
    return ( pageTimeDeadline >0 && LimitScope.BETWEEN_ROWS.canEnforceLimitFromScope(checkerScope))
        || delegate.hasTimeLimit(checkerScope);
  }

  @Override
  //Changed order to hopefully improve perf
  boolean hasAnyLimit(LimitScope checkerScope) {
    return hasTimeLimit(checkerScope) || hasBatchLimit(checkerScope) || hasSizeLimit(checkerScope) ;
  }

  @Override
  void setSizeLimitScope(LimitScope scope) {
    delegate.setSizeLimitScope(scope);
  }

  @Override
  void setTimeLimitScope(LimitScope scope) {
    delegate.setTimeLimitScope(scope);
  }

  @Override
  int getBatchLimit() {
    return delegate.getBatchLimit();
  }

  @Override
  long getDataSizeLimit() {
    return delegate.getDataSizeLimit();
  }

  @Override
  long getTimeLimit() {
    //FIXME should we include page time ?
    //Doesn't seem to be called anyway
    return delegate.getTimeLimit();
  }

  @Override
  boolean checkBatchLimit(LimitScope checkerScope) {
    return delegate.checkBatchLimit(checkerScope);
  }

  @Override
  boolean checkSizeLimit(LimitScope checkerScope) {
    return delegate.checkSizeLimit(checkerScope);
  }

  @Override
  boolean checkTimeLimit(LimitScope checkerScope) {
    if (timeLimitCache) {
      return true;
    }
    // Time limit scope is always BETWEEN_ROWS when using PhoenixScannerContext
    timeLimitCache = LimitScope.BETWEEN_ROWS.canEnforceLimitFromScope(checkerScope) && EnvironmentEdgeManager.currentTimeMillis() >= pageTimeDeadline;
    return timeLimitCache || delegate.checkTimeLimit(checkerScope);
  }

  @Override
  boolean checkAnyLimitReached(LimitScope checkerScope) {
    //Reordered to start with timeLimit
    return checkTimeLimit(checkerScope) || checkSizeLimit(checkerScope) || checkBatchLimit(checkerScope);
  }

  public static boolean checkAnyLimitReached(ScannerContext sc) {
    return sc.checkAnyLimitReached(LimitScope.BETWEEN_ROWS);
  }
  
  public static Cell getLastPeekedCell(ScannerContext sc) {
    return sc.getLastPeekedCell();
  }
  
  @Override
  Cell getLastPeekedCell() {
    return delegate.getLastPeekedCell();
  }

  @Override
  void setLastPeekedCell(Cell lastPeekedCell) {
    delegate.setLastPeekedCell(lastPeekedCell);
  }

  @Override
  void returnImmediately() {
    delegate.returnImmediately();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");

    sb.append("pageTimeDeadline:");
    sb.append(pageTimeDeadline);
    
    sb.append("limits:");
    sb.append(delegate.limits);


    
    sb.append(", progress:");
    sb.append(delegate.progress);

    sb.append(", keepProgress:");
    sb.append(delegate.keepProgress);

    sb.append(", state:");
    sb.append(delegate.scannerState);

    sb.append("}");
    return sb.toString();
  }

  private void setPageTimeDeadline(long pageTimeDeadline) {
    this.pageTimeDeadline = pageTimeDeadline;
  }

  //FIXME
//  static {
//    Class scannerContextClazz = ScannerContext.class;
//    Class[] innerClasses = scannerContextClazz.getDeclaredClasses();
//    for(Class innerClass : innerClasses) {
//      if (innerClass.getSimpleName().equals("LimitFields")) {
//        innerClass.
//      }
//    }
//  }
  


  public long getStartTime() {
    return startTime;
  }

  /**
   * The scanner remains open on the server during the course of multiple scan rpc requests. We need
   * a way to determine during the next() call if it is a new scan rpc request on the same scanner.
   * This is needed so that we can reset the start time for server paging. Every scan rpc request
   * creates a new ScannerContext which has the lastPeekedCell set to null in the beginning.
   * Subsequent next() calls will set this field in the ScannerContext.
   */
  public static boolean isNewScanRpcRequest(ScannerContext scannerContext) {
    return scannerContext != null && scannerContext.getLastPeekedCell() == null;
  }

  public PhoenixScannerContext(ScannerContext hbaseContext) {
    // Doesn't matter, everything goes through the delegate.
    super(false, null, false);
    startTime = EnvironmentEdgeManager.currentTimeMillis();
    delegate = hbaseContext;
  }

  public PhoenixScannerContext(ScannerContext hbaseContext, long pageSizeMsDelta) {
    // Doesn't matter, everything goes through the delegate.
    this(hbaseContext);
    pageTimeDeadline = EnvironmentEdgeManager.currentTimeMillis() + pageSizeMsDelta;
  }

  public PhoenixScannerContext(boolean trackMetrics, long pageSizeMsDelta) {
    super(false, null, trackMetrics);
    startTime = EnvironmentEdgeManager.currentTimeMillis();
    delegate = new ScannerContext(false, null, trackMetrics);
    pageTimeDeadline = EnvironmentEdgeManager.currentTimeMillis() + pageSizeMsDelta;
  }

  /**
   * Update the scanner context created by RSRpcServices so that it can act accordingly
   * @param dst    hbase scanner context created on every new scan rpc request
   * @param result list of cells to be returned to the client as scan rpc response
   */
  public void updateScannerContext(ScannerContext dst, List<Cell> result) {
    if (dst == null) {
      return;
    }
    // update last peeked cell
    dst.setLastPeekedCell(getLastPeekedCell());
    // update return immediately
    if (isDummy(result) || checkAnyLimitReached(LimitScope.BETWEEN_ROWS)) {
      // when a dummy row is returned by a lower layer, set returnImmediately
      // on the ScannerContext to force HBase to return a response to the client
      dst.returnImmediately();
    }
    // update metrics
    if (isTrackingMetrics() && dst.isTrackingMetrics()) {
      // getMetricsMap call resets the metrics internally
      for (Map.Entry<String, Long> entry : getMetrics().getMetricsMap().entrySet()) {
        dst.metrics.addToCounter(entry.getKey(), entry.getValue());
      }
    }
    // update progress
    dst.setProgress(getBatchProgress(), getDataSizeProgress(), getHeapSizeProgress());
    // update deadline
    if(dst instanceof PhoenixScannerContext) {
      ((PhoenixScannerContext)dst).setPageTimeDeadline(pageTimeDeadline);
    } else {
      LOGGER.error("Expected PhoenixScannerContext at", new Exception("For stack trace"));
    }
  }

  //FIXME remove or replace
//  public static boolean isTimedOut(ScannerContext context, long pageSizeMs) {
//    if (context == null || !(context instanceof PhoenixScannerContext)) {
//      return false;
//    }
//    PhoenixScannerContext phoenixScannerContext = (PhoenixScannerContext) context;
//    return EnvironmentEdgeManager.currentTimeMillis() - phoenixScannerContext.startTime
//        > pageSizeMs;
//  }

//  /**
//   * Set returnImmediately on the ScannerContext to true, it will have the same behavior as reaching
//   * the time limit. Use this to make RSRpcService.scan return immediately.
//   */
//  public static void setReturnImmediately(ScannerContext context) {
//    if (context == null || !(context instanceof PhoenixScannerContext)) {
//      return;
//    }
//    anyLimitReachedCache = true;
//    PhoenixScannerContext phoenixScannerContext = (PhoenixScannerContext) context;
//    phoenixScannerContext.returnImmediately();
//  }
//
//  public static boolean isReturnImmediately(ScannerContext context) {
//    if (context == null || !(context instanceof PhoenixScannerContext)) {
//      return false;
//    }
//    PhoenixScannerContext phoenixScannerContext = (PhoenixScannerContext) context;
//    return phoenixScannerContext.isReturnImmediately();
//  }
}
