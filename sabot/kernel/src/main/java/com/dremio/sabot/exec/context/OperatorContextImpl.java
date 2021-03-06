/*
 * Copyright (C) 2017 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.sabot.exec.context;

import java.util.concurrent.ExecutorService;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.BufferManager;
import org.apache.arrow.memory.OutOfMemoryException;

import com.dremio.common.AutoCloseables;
import com.dremio.common.config.SabotConfig;
import com.dremio.exec.compile.CodeCompiler;
import com.dremio.exec.expr.ClassProducer;
import com.dremio.exec.expr.ClassProducerImpl;
import com.dremio.exec.expr.fn.FunctionLookupContext;
import com.dremio.exec.physical.base.PhysicalOperator;
import com.dremio.exec.proto.ExecProtos.FragmentHandle;
import com.dremio.exec.server.options.OptionManager;
import com.dremio.exec.testing.ExecutionControls;
import com.dremio.service.namespace.NamespaceService;
import com.google.common.annotations.VisibleForTesting;

import io.netty.buffer.ArrowBuf;

@VisibleForTesting
public class OperatorContextImpl extends OperatorContext implements AutoCloseable {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OperatorContextImpl.class);

  private final SabotConfig config;
  private final FragmentHandle handle;
  private final BufferAllocator allocator;
  private final ExecutionControls executionControls;
  private boolean closed = false;
  private final PhysicalOperator popConfig;
  private final OperatorStats stats;
  private final BufferManager manager;
  private final ExecutorService executor;

  private final ClassProducer producer;
  private final OptionManager optionManager;
  private final int targetBatchSize;
  private final NamespaceService ns;

  public OperatorContextImpl(
      SabotConfig config,
      FragmentHandle handle,
      PhysicalOperator popConfig,
      BufferAllocator allocator,
      CodeCompiler compiler,
      OperatorStats stats,
      ExecutionControls executionControls,
      ExecutorService executor,
      FunctionLookupContext functions,
      ContextInformation contextInformation,
      OptionManager optionManager,
      NamespaceService namespaceService,
      int targetBatchSize) throws OutOfMemoryException {
    this.config = config;
    this.handle = handle;
    this.allocator = allocator;
    this.popConfig = popConfig;
    this.manager = new BufferManagerImpl(allocator);
    this.stats = stats;
    this.executionControls = executionControls;
    this.executor = executor;
    this.optionManager = optionManager;
    this.targetBatchSize = targetBatchSize;
    this.ns = namespaceService;
    this.producer = new ClassProducerImpl(compiler, functions, contextInformation, manager);
  }

  public OperatorContextImpl(
      SabotConfig config,
      BufferAllocator allocator,
      OptionManager optionManager,
      int targetBatchSize
      ) {
    this(config, null, null, allocator, null, null, null, null, null, null, optionManager, null, targetBatchSize);
  }


  @Override
  public SabotConfig getConfig(){
    return config;
  }

  @Override
  public ArrowBuf replace(ArrowBuf old, int newSize) {
    return manager.replace(old, newSize);
  }

  @Override
  public ArrowBuf getManagedBuffer() {
    return manager.getManagedBuffer();
  }

  @Override
  public ArrowBuf getManagedBuffer(int size) {
    return manager.getManagedBuffer(size);
  }

  @Override
  public ExecutionControls getExecutionControls() {
    return executionControls;
  }

  @Override
  public ExecutorService getExecutor() {
    if (executor == null) {
      throw new UnsupportedOperationException("Operator context does not have an executor");
    }
    return executor;
  }

  @Override
  public BufferAllocator getAllocator() {
    if (allocator == null) {
      throw new UnsupportedOperationException("Operator context does not have an allocator");
    }
    return allocator;
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public int getTargetBatchSize() {
    return targetBatchSize;
  }

  @Override
  public void close() throws Exception {
    if (closed) {
      logger.warn("Attempted to close Operator context for {}, but context is already closed", popConfig != null ? popConfig.getClass().getName() : "<unknown>");
      return;
    }

    try{
      AutoCloseables.close(manager, allocator);
    }finally{
      closed = true;
    }
  }

  @Override
  public OperatorStats getStats() {
    return stats;
  }

  @Override
  public OptionManager getOptions() {
    return optionManager;
  }

  @Override
  public FragmentHandle getFragmentHandle() {
    return handle;
  }

  @Override
  public FunctionContext getFunctionContext() {
    return producer.getFunctionContext();
  }

  @Override
  public ClassProducer getClassProducer(){
    return producer;
  }

  @Override
  public NamespaceService getNamespaceService() {
    return ns;
  }
}
