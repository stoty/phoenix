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
package org.apache.phoenix.compat.hbase;

import java.io.IOException;

import org.apache.hadoop.hbase.CellScanner;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.ipc.HBaseRpcController;

import com.google.protobuf.RpcCallback;

//We need to copy the HBase implementation, because we need to have CompatHBaseRpcController
//as ancestor, so we cannot simply subclass the HBase Delegating* class
public class CompatDelegatingHBaseRpcController implements CompatHBaseRpcController {
    private HBaseRpcController delegate;

    public CompatDelegatingHBaseRpcController(CompatHBaseRpcController delegate) {
      this.delegate = delegate;
    }

    @Override
    public CellScanner cellScanner() {
      return delegate.cellScanner();
    }

    @Override
    public void setCellScanner(final CellScanner cellScanner) {
      delegate.setCellScanner(cellScanner);
    }

    @Override
    public void setPriority(int priority) {
      delegate.setPriority(priority);
    }

    @Override
    public void setPriority(final TableName tn) {
      delegate.setPriority(tn);
    }

    @Override
    public int getPriority() {
      return delegate.getPriority();
    }

    @Override
    public int getCallTimeout() {
        return delegate.getCallTimeout();
    }

    @Override
    public void setCallTimeout(int callTimeout) {
        delegate.setCallTimeout(callTimeout);
    }

    @Override
    public boolean hasCallTimeout() {
        return delegate.hasCallTimeout();
    }

    @Override
    public void setFailed(IOException e) {
        delegate.setFailed(e);
    }

    @Override
    public IOException getFailed() {
        return delegate.getFailed();
    }

    @Override
    public void setDone(CellScanner cellScanner) {
        delegate.setDone(cellScanner);
    }

    @Override
    public void notifyOnCancel(RpcCallback<Object> callback) {
        delegate.notifyOnCancel(callback);
    }

    @Override
    public void notifyOnCancel(RpcCallback<Object> callback, CancellationCallback action)
            throws IOException {
        delegate.notifyOnCancel(callback, action);
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public boolean failed() {
        return delegate.failed();
    }

    @Override
    public String errorText() {
        return delegate.errorText();
    }

    @Override
    public void startCancel() {
        delegate.startCancel();
    }

    @Override
    public void setFailed(String reason) {
        delegate.setFailed(reason);
    }

    @Override
    public boolean isCanceled() {
        return delegate.isCanceled();
    }
}
