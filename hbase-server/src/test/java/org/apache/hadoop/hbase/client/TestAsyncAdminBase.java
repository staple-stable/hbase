/**
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
package org.apache.hadoop.hbase.client;

import static org.apache.hadoop.hbase.client.AsyncProcess.START_LOG_ERRORS_AFTER_COUNT_KEY;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Class to test AsyncAdmin.
 */
public abstract class TestAsyncAdminBase {

  protected static final Log LOG = LogFactory.getLog(TestAsyncAdminBase.class);
  protected final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  protected static final byte[] FAMILY = Bytes.toBytes("testFamily");
  protected static final byte[] FAMILY_0 = Bytes.toBytes("cf0");
  protected static final byte[] FAMILY_1 = Bytes.toBytes("cf1");

  protected static AsyncConnection ASYNC_CONN;
  protected AsyncAdmin admin;

  @Parameter
  public Supplier<AsyncAdmin> getAdmin;

  private static AsyncAdmin getRawAsyncAdmin() {
    return ASYNC_CONN.getAdmin();
  }

  private static AsyncAdmin getAsyncAdmin() {
    return ASYNC_CONN.getAdmin(ForkJoinPool.commonPool());
  }

  @Parameters
  public static List<Object[]> params() {
    return Arrays.asList(new Supplier<?>[] { TestAsyncAdminBase::getRawAsyncAdmin },
      new Supplier<?>[] { TestAsyncAdminBase::getAsyncAdmin });
  }

  @Rule
  public TestName testName = new TestName();
  protected TableName tableName;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TEST_UTIL.getConfiguration().setInt(HConstants.HBASE_RPC_TIMEOUT_KEY, 60000);
    TEST_UTIL.getConfiguration().setInt(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, 120000);
    TEST_UTIL.getConfiguration().setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 2);
    TEST_UTIL.getConfiguration().setInt(START_LOG_ERRORS_AFTER_COUNT_KEY, 0);
    TEST_UTIL.startMiniCluster(2);
    ASYNC_CONN = ConnectionFactory.createAsyncConnection(TEST_UTIL.getConfiguration()).get();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    IOUtils.closeQuietly(ASYNC_CONN);
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void setUp() throws Exception {
    admin = getAdmin.get();
    String methodName = testName.getMethodName();
    tableName = TableName.valueOf(methodName.substring(0, methodName.length() - 3));
  }

  @After
  public void tearDown() throws Exception {
    admin.listTableNames(Optional.of(Pattern.compile(tableName.getNameAsString() + ".*")), false)
        .whenCompleteAsync((tables, err) -> {
          if (tables != null) {
            tables.forEach(table -> {
              try {
                admin.disableTable(table).join();
              } catch (Exception e) {
                LOG.debug("Table: " + tableName + " already disabled, so just deleting it.");
              }
              admin.deleteTable(table).join();
            });
          }
        }, ForkJoinPool.commonPool()).join();
  }

  protected void createTableWithDefaultConf(TableName tableName) {
    createTableWithDefaultConf(tableName, Optional.empty());
  }

  protected void createTableWithDefaultConf(TableName tableName, Optional<byte[][]> splitKeys) {
    createTableWithDefaultConf(tableName, splitKeys, FAMILY);
  }

  protected void createTableWithDefaultConf(TableName tableName, Optional<byte[][]> splitKeys,
      byte[]... families) {
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
    for (byte[] family : families) {
      builder.addColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(family).build());
    }
    admin.createTable(builder.build(), splitKeys).join();
  }
}
