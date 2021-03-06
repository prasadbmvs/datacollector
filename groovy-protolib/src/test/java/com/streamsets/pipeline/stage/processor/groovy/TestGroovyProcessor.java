/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
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
package com.streamsets.pipeline.stage.processor.groovy;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Processor;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.sdk.ProcessorRunner;
import com.streamsets.pipeline.stage.processor.scripting.ProcessingMode;
import com.streamsets.pipeline.stage.processor.scripting.ScriptingProcessorTestUtil;
import org.junit.Test;

public class TestGroovyProcessor {

  @Test
  public void testWriteErrorRecord() throws Exception {
    final String script = Resources.toString(Resources.getResource("WriteErrorRecordScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.RECORD, script);

    ProcessorRunner runner = new ProcessorRunner.Builder(GroovyDProcessor.class, processor)
        .addOutputLane("lane")
        .build();

    runner.runInit();

    ScriptingProcessorTestUtil.verifyWriteErrorRecord(GroovyDProcessor.class, processor);

  }

  @Test
  public void testGroovyAndMapArray() throws Exception {
    final String script = Resources.toString(Resources.getResource("MapAndArrayScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.RECORD, script);

    ScriptingProcessorTestUtil.verifyMapAndArray(GroovyDProcessor.class, processor);
  }

  private void testMode(ProcessingMode mode) throws Exception {
    final String script = Resources.toString(Resources.getResource("ModeScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(mode, script);

    ScriptingProcessorTestUtil.verifyMode(GroovyDProcessor.class, processor);
  }

  @Test
  public void testRecordMode() throws Exception {
    testMode(ProcessingMode.RECORD);
  }

  @Test
  public void testBatchMode() throws Exception {
    testMode(ProcessingMode.BATCH);
  }

  private void testRecordModeOnErrorHandling(OnRecordError onRecordError) throws Exception {
    final String script = Resources.toString(Resources.getResource("OnErrorHandlingScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.RECORD, script);

    ScriptingProcessorTestUtil.verifyRecordModeOnErrorHandling(GroovyDProcessor.class, processor, onRecordError);
  }

  @Test
  public void testRecordOnErrorDiscard() throws Exception {
    testRecordModeOnErrorHandling(OnRecordError.DISCARD);
  }

  @Test
  public void testRecordOnErrorToError() throws Exception {
    testRecordModeOnErrorHandling(OnRecordError.TO_ERROR);
  }

  @Test(expected = StageException.class)
  public void testRecordOnErrorStopPipeline() throws Exception {
    testRecordModeOnErrorHandling(OnRecordError.STOP_PIPELINE);
  }

  private void testBatchModeOnErrorHandling(OnRecordError onRecordError) throws Exception {
    final String script = Resources.toString(Resources.getResource("OnErrorHandlingScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.BATCH, script);

    ScriptingProcessorTestUtil.verifyBatchModeOnErrorHandling(GroovyDProcessor.class, processor, onRecordError);
  }

  @Test(expected = StageException.class)
  public void testBatchOnErrorDiscard() throws Exception {
    testBatchModeOnErrorHandling(OnRecordError.DISCARD);
  }

  @Test(expected = StageException.class)
  public void testBatchOnErrorToError() throws Exception {
    testBatchModeOnErrorHandling(OnRecordError.TO_ERROR);
  }

  @Test(expected = StageException.class)
  public void testBatchOnErrorStopPipeline() throws Exception {
    testBatchModeOnErrorHandling(OnRecordError.STOP_PIPELINE);
  }

  @Test
  public void testPrimitiveTypesPassthrough() throws Exception {
    final String script = Resources.toString(Resources.getResource("PrimitiveTypesPassthroughScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.RECORD, script);

    ScriptingProcessorTestUtil.verifyPrimitiveTypesPassthrough(GroovyDProcessor.class, processor);
  }

  @Test
  public void testPrimitiveTypesFromScripting() throws Exception {
    final String script = Resources.toString(
        Resources.getResource("PrimitiveTypesFromScripting.groovy"),
        Charsets.UTF_8
    );
    Processor processor = new GroovyProcessor(ProcessingMode.RECORD, script);

    ScriptingProcessorTestUtil.verifyPrimitiveTypesFromScripting(GroovyDProcessor.class, processor);
  }

  @Test
  public void testStateObject() throws Exception {
    final String script = Resources.toString(Resources.getResource("StateObjectScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.RECORD, script);

    ScriptingProcessorTestUtil.verifyStateObject(GroovyDProcessor.class, processor);
  }

  @Test
  public void testListMap() throws Exception {
    final String script = Resources.toString(Resources.getResource("ListMapScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.RECORD, script);

    ScriptingProcessorTestUtil.verifyListMap(GroovyDProcessor.class, processor);
  }

  @Test
  public void testListMapOrder() throws Exception {
    final String script = Resources.toString(Resources.getResource("ListMapOrderScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.RECORD, script);

    ScriptingProcessorTestUtil.verifyListMapOrder(GroovyDProcessor.class, processor);
  }

  @Test
  public void testTypedNullPassThrough() throws Exception {
    final String script = Resources.toString(Resources.getResource("PrimitiveTypesPassthroughScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.BATCH, script);
    ScriptingProcessorTestUtil.verifyPreserveTypeForNullValue(GroovyProcessor.class, processor);
  }

  @Test
  public void testAssignNullToTypedField() throws Exception {
    final String script = Resources.toString(Resources.getResource("AssignNullToTypedField.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.BATCH, script);
    ScriptingProcessorTestUtil.verifyPreserveTypeForNullValue(GroovyProcessor.class, processor);
  }

  @Test
  public void testNestedMapWithNull() throws Exception {
    final String script = Resources.toString(Resources.getResource("NestedMapWithNull.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.BATCH, script);
    ScriptingProcessorTestUtil.verifyNestedMap(GroovyProcessor.class, processor);
  }

  @Test
  public void testChangeFieldTypeFromScripting() throws Exception {
    final String script = Resources.toString(Resources.getResource("ChangeFieldTypeScript.groovy"), Charsets.UTF_8);
    Processor processor = new GroovyProcessor(ProcessingMode.BATCH,script);
    ScriptingProcessorTestUtil.verifyChangedTypeFromScripting(GroovyProcessor.class, processor);
  }
}
