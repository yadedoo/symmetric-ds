/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.db.platform.oracle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.jumpmind.db.DdlReaderTestConstants;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class OracleDdlReaderTest {

    protected IDatabasePlatform platform;
    protected Pattern oracleIsoDatePattern;
    /* The regular expression pattern for the Oracle conversion of ISO times. */
    protected Pattern oracleIsoTimePattern;
    /*
     * The regular expression pattern for the Oracle conversion of ISO
     * timestamps.
     */
    protected Pattern oracleIsoTimestampPattern;
    ISqlTemplate sqlTemplate;
    ISqlTransaction sqlTransaction;
    protected AbstractJdbcDdlReader abstractJdbcDdlReader;
    ThreadLocalRandom rand = ThreadLocalRandom.current();

    @BeforeEach
    public void setUp() throws Exception {
        platform = mock(OracleDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        oracleIsoDatePattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD'\\)");
        oracleIsoTimePattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'HH24:MI:SS'\\)");
        oracleIsoTimestampPattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD HH24:MI:SS'\\)");
    }

    @Test
    void testOracleDdlReaderConstructor() throws Exception {
        OracleDdlReader testReader = new OracleDdlReader(platform);
        testReader.setDefaultCatalogPattern(null);
        testReader.setDefaultSchemaPattern(null);
        testReader.setDefaultTablePattern("%");

        assertEquals(null, testReader.getDefaultCatalogPattern());
        assertEquals(null, testReader.getDefaultSchemaPattern());
        assertEquals("%", testReader.getDefaultTablePattern());
        assertEquals(true, oracleIsoDatePattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD'\\)"));
        assertEquals(true, oracleIsoTimePattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'HH24:MI:SS'\\)"));
        assertEquals(true, oracleIsoTimestampPattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD HH24:MI:SS'\\)"));

    }

    @Test
    void testIsTableInRecycleBin() throws Exception {
        OracleDdlReader testReader = new OracleDdlReader(platform);
        Connection connection = mock(Connection.class);

        Map<String, Object> values = new HashMap<String, Object>();
        values.put(DdlReaderTestConstants.TABLE_NAME, "bin$testing123");
        Map<String, Object> values2 = new HashMap<String, Object>();
        values2.put("TABLE_NAME2", "testing123");

        boolean testIsTableInRecycleBin = testReader.isTableInRecycleBin(connection, values);
        boolean testIsTableInRecycleBin2 = testReader.isTableInRecycleBin(connection, values2);

        assertTrue(testIsTableInRecycleBin);
        assertFalse(testIsTableInRecycleBin2);

    }

    @Test
    void testGetTableNames() throws Exception {
        OracleDdlReader testReader = new OracleDdlReader(platform);
        OracleJdbcSqlTemplate mockTemplate = mock(OracleJdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(mockTemplate);
        String sql = ArgumentMatchers.anyString();
        List<String> actualNames = new ArrayList<String>();
        @SuppressWarnings("unchecked")
        ISqlRowMapper<String> mapper = (ISqlRowMapper<String>) ArgumentMatchers.any();
        when(mockTemplate.query(sql, mapper, (Object[]) ArgumentMatchers.any())).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocation) {
                actualNames.add("TestName");
                return actualNames;
            }
        });
        List<String> tableNames = testReader.getTableNames(null, null, null);
        List<String> actualNamesBlank = new ArrayList<String>();
        assertNotEquals(actualNamesBlank, tableNames);
        assertEquals(actualNames, tableNames);
    }

    @Test
    void testGetTableNamesWithSchema() throws Exception {
        OracleDdlReader testReader = new OracleDdlReader(platform);
        OracleJdbcSqlTemplate mockTemplate = mock(OracleJdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(mockTemplate);
        String sql = ArgumentMatchers.anyString();
        List<String> actualNames = new ArrayList<String>();
        @SuppressWarnings("unchecked")
        ISqlRowMapper<String> mapper = (ISqlRowMapper<String>) ArgumentMatchers.any();
        when(mockTemplate.query(sql, mapper, (Object[]) ArgumentMatchers.any())).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocation) {
                actualNames.add("TestNameWithSchema");
                return actualNames;
            }
        });
        List<String> tableNames = testReader.getTableNames(null, "test", null);
        List<String> actualNamesBlank = new ArrayList<String>();
        assertNotEquals(actualNamesBlank, tableNames);
        assertEquals(actualNames, tableNames);

    }

    @Test
    void testInsertGetTriggers() throws Exception {

        // Mocked components
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        Statement st = mock(Statement.class);
        PreparedStatement ps2 = mock(PreparedStatement.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        Connection connection = mock(Connection.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);

        // "Real" components
        OracleDdlReader testReader = new OracleDdlReader(platform);
        List<Trigger> actualTriggers = new ArrayList<Trigger>();
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);

        // Spied components
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);

        when(platform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement(spyTemplate.getSettings().getResultSetType(), ResultSet.CONCUR_READ_ONLY)).thenReturn(st);
        when(connection.prepareStatement(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(ps)
                .thenReturn(ps2);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn("TRIGGER_NAME");
        when(rs.getObject(1)).thenReturn("testTrigger");
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.OWNER);
        when(rs.getObject(2)).thenReturn("testSchema");
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getObject(3)).thenReturn("testTableName");
        when(rsMetaData.getColumnLabel(4)).thenReturn("STATUS");
        when(rs.getObject(4)).thenReturn("ACTIVE");
        when(rsMetaData.getColumnLabel(5)).thenReturn("TRIGGERING_EVENT");
        when(rs.getObject(5)).thenReturn("INSERT");
        when(ps2.executeQuery()).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(1);
        when(rsMetaData2.getColumnLabel(1)).thenReturn("TEXT");
        when(rs2.getObject(1)).thenReturn("testText");

        Row triggerMetaData = new Row(5);
        triggerMetaData.put("TRIGGER_NAME", "testTrigger");
        triggerMetaData.put(DdlReaderTestConstants.OWNER, "testSchema");
        triggerMetaData.put(DdlReaderTestConstants.TABLE_NAME, "testTableName");
        triggerMetaData.put("STATUS", "ACTIVE");
        triggerMetaData.put("TRIGGERING_EVENT", "INSERT");

        Trigger trigger = new Trigger();
        trigger.setName("testTrigger");
        trigger.setSchemaName("testSchema");
        trigger.setTableName("testTableName");
        trigger.setEnabled(false);
        trigger.setSource("create ");
        trigger.setMetaData(triggerMetaData);
        String triggerType = "INSERT";
        trigger.setTriggerType(TriggerType.valueOf(triggerType));
        actualTriggers.add(trigger);

        List<Trigger> triggers = testReader.getTriggers("test", "test", "test");
        Trigger testTrigger = triggers.get(0);
        assertEquals("testTrigger", testTrigger.getName());
        assertEquals("testSchema", testTrigger.getSchemaName());
        assertEquals("testTableName", testTrigger.getTableName());
        assertEquals(false, testTrigger.isEnabled());
        assertEquals("create \ntestText", testTrigger.getSource());
        assertEquals(true, testTrigger.getTriggerType().toString().equals("INSERT"));

    }

    @Test
    void testUpdateGetTriggers() throws Exception {
        // Mocked components
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        Statement st = mock(Statement.class);
        PreparedStatement ps2 = mock(PreparedStatement.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        Connection connection = mock(Connection.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);

        // "Real" components
        OracleDdlReader testReader = new OracleDdlReader(platform);
        List<Trigger> actualTriggers = new ArrayList<Trigger>();
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);

        // Spied components
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);

        when(platform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement(spyTemplate.getSettings().getResultSetType(), ResultSet.CONCUR_READ_ONLY)).thenReturn(st);
        when(connection.prepareStatement(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(ps)
                .thenReturn(ps2);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn("TRIGGER_NAME");
        when(rs.getObject(1)).thenReturn("testTrigger");
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.OWNER);
        when(rs.getObject(2)).thenReturn("testSchema");
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getObject(3)).thenReturn("testTableName");
        when(rsMetaData.getColumnLabel(4)).thenReturn("STATUS");
        when(rs.getObject(4)).thenReturn("ACTIVE");
        when(rsMetaData.getColumnLabel(5)).thenReturn("TRIGGERING_EVENT");
        when(rs.getObject(5)).thenReturn("UPDATE");
        when(ps2.executeQuery()).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(1);
        when(rsMetaData2.getColumnLabel(1)).thenReturn("TEXT");
        when(rs2.getObject(1)).thenReturn("testText");

        Row triggerMetaData = new Row(5);
        triggerMetaData.put("TRIGGER_NAME", "testTrigger");
        triggerMetaData.put(DdlReaderTestConstants.OWNER, "testSchema");
        triggerMetaData.put(DdlReaderTestConstants.TABLE_NAME, "testTableName");
        triggerMetaData.put("STATUS", "ACTIVE");
        triggerMetaData.put("TRIGGERING_EVENT", "UPDATE");

        Trigger trigger = new Trigger();
        trigger.setName("testTrigger");
        trigger.setSchemaName("testSchema");
        trigger.setTableName("testTableName");
        trigger.setEnabled(false);
        trigger.setSource("create ");
        trigger.setMetaData(triggerMetaData);
        String triggerType = "UPDATE";
        trigger.setTriggerType(TriggerType.valueOf(triggerType));
        actualTriggers.add(trigger);

        List<Trigger> triggers = testReader.getTriggers("test", "test", "test");
        Trigger testTrigger = triggers.get(0);
        assertEquals("testTrigger", testTrigger.getName());
        assertEquals("testSchema", testTrigger.getSchemaName());
        assertEquals("testTableName", testTrigger.getTableName());
        assertEquals(false, testTrigger.isEnabled());
        assertEquals("create \ntestText", testTrigger.getSource());
        assertEquals(true, testTrigger.getTriggerType().toString().equals("UPDATE"));

    }

    @Test
    void testDeleteGetTriggers() throws Exception {
        // Mocked components
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        Statement st = mock(Statement.class);
        PreparedStatement ps2 = mock(PreparedStatement.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        Connection connection = mock(Connection.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);

        // "Real" components
        OracleDdlReader testReader = new OracleDdlReader(platform);
        List<Trigger> actualTriggers = new ArrayList<Trigger>();
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);

        // Spied components
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);

        when(platform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement(spyTemplate.getSettings().getResultSetType(), ResultSet.CONCUR_READ_ONLY)).thenReturn(st);
        when(connection.prepareStatement(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(ps)
                .thenReturn(ps2);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn("TRIGGER_NAME");
        when(rs.getObject(1)).thenReturn("testTrigger");
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.OWNER);
        when(rs.getObject(2)).thenReturn("testSchema");
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getObject(3)).thenReturn("testTableName");
        when(rsMetaData.getColumnLabel(4)).thenReturn("STATUS");
        when(rs.getObject(4)).thenReturn("ACTIVE");
        when(rsMetaData.getColumnLabel(5)).thenReturn("TRIGGERING_EVENT");
        when(rs.getObject(5)).thenReturn("DELETE");
        when(ps2.executeQuery()).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(1);
        when(rsMetaData2.getColumnLabel(1)).thenReturn("TEXT");
        when(rs2.getObject(1)).thenReturn("testText");

        Row triggerMetaData = new Row(5);
        triggerMetaData.put("TRIGGER_NAME", "testTrigger");
        triggerMetaData.put(DdlReaderTestConstants.OWNER, "testSchema");
        triggerMetaData.put(DdlReaderTestConstants.TABLE_NAME, "testTableName");
        triggerMetaData.put("STATUS", "ACTIVE");
        triggerMetaData.put("TRIGGERING_EVENT", "DELETE");

        Trigger trigger = new Trigger();
        trigger.setName("testTrigger");
        trigger.setSchemaName("testSchema");
        trigger.setTableName("testTableName");
        trigger.setEnabled(false);
        trigger.setSource("create ");
        trigger.setMetaData(triggerMetaData);
        String triggerType = "DELETE";
        trigger.setTriggerType(TriggerType.valueOf(triggerType));
        actualTriggers.add(trigger);

        List<Trigger> triggers = testReader.getTriggers("test", "test", "test");
        Trigger testTrigger = triggers.get(0);
        assertEquals("testTrigger", testTrigger.getName());
        assertEquals("testSchema", testTrigger.getSchemaName());
        assertEquals("testTableName", testTrigger.getTableName());
        assertEquals(false, testTrigger.isEnabled());
        assertEquals("create \ntestText", testTrigger.getSource());
        assertEquals(true, testTrigger.getTriggerType().toString().equals("DELETE"));

    }

    @Test
    void testReadTable() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(7);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.VARCHAR);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        
        //Creation of the table we would expect to be created
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        testColumn.setJdbcTypeCode(Types.VARCHAR);
        testColumn.setAutoIncrement(true);
        testColumn.setMappedType("VARCHAR");
        testColumn.setMappedTypeCode(12);
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        testColumn.setSize("254");

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableWithFloat126() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(8);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("BINARY_FLOAT");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.FLOAT);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn("126");

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("BINARY_FLOAT");
        testColumn.setSize("126");
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(Types.DOUBLE);
        testColumn.setMappedType("DOUBLE");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableWithFloat63() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(8);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("BINARY_FLOAT");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.FLOAT);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn("63");

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("BINARY_FLOAT");
        testColumn.setSize("63");
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(Types.REAL);
        testColumn.setMappedType("REAL");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableWithDecimal() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(9);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn("1.1234321");
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn("1.1234321");
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("DECIMAL");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.DECIMAL);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn("126");
        when(rsMetaData2.getColumnLabel(9)).thenReturn("DECIMAL_DIGITS");
        when(rsMetaData2.getColumnName(9)).thenReturn("DECIMAL_DIGITS");
        when(rs2.getInt(9)).thenReturn(10);

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue("1.1234321");
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("DECIMAL");
        testColumn.setSize("126,10");
        testColumn.setScale(10);
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(Types.DECIMAL);
        testColumn.setMappedTypeCode(Types.NUMERIC);
        testColumn.setMappedType("NUMERIC");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableWithLargeDecimal() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(9);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn("1.1234321");
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn("1.1234321");
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("DECIMAL");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.DECIMAL);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn("126");
        when(rsMetaData2.getColumnLabel(9)).thenReturn("DECIMAL_DIGITS");
        when(rsMetaData2.getColumnName(9)).thenReturn("DECIMAL_DIGITS");
        when(rs2.getInt(9)).thenReturn(288);

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue("1.1234321");
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("DECIMAL");
        testColumn.setSize("126,288");
        testColumn.setScale(288);
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(Types.DECIMAL);
        testColumn.setMappedTypeCode(Types.DOUBLE);
        testColumn.setMappedType("DOUBLE");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableWithLargeDecimalNoSize() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(9);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn("1.1234321");
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn("1.1234321");
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("DECIMAL");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.DECIMAL);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn("0");
        when(rsMetaData2.getColumnLabel(9)).thenReturn("DECIMAL_DIGITS");
        when(rsMetaData2.getColumnName(9)).thenReturn("DECIMAL_DIGITS");
        when(rs2.getInt(9)).thenReturn(288);

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        doReturn(1).when(spyTemplate).queryForInt(ArgumentMatchers.anyString(), (Object) ArgumentMatchers.any());

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue("1.1234321");
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("DECIMAL");
        testColumn.setSize("126,288");
        testColumn.setScale(288);
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(Types.DECIMAL);
        testColumn.setMappedTypeCode(Types.BIGINT);
        testColumn.setMappedType("BIGINT");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableWithDate() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(7);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn("2008-11-11");
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("DATE");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.DATE);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue("2008-11-11");
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("DATE");
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(Types.DATE);
        testColumn.setMappedType("DATE");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableWithTimestamp() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(7);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn("2008-11-11 12:10:30");
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("TIMESTAMP");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.TIMESTAMP);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue("2008-11-11 12:10:30");
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("TIMESTAMP");
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(Types.TIMESTAMP);
        testColumn.setMappedType("TIMESTAMP");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableWithBinary() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(8);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn("'1001001'");
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn("'1001001'");
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("BINARY");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.BINARY);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn("100");

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        doReturn(1).when(spyTemplate).queryForInt(ArgumentMatchers.anyString(), (Object) ArgumentMatchers.any());

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setSize("100");
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeName("BINARY");
        testColumn.setJdbcTypeCode(Types.BINARY);
        testColumn.setDefaultValue("1001001");
        testColumn.setMappedTypeCode(Types.BINARY);
        testColumn.setMappedType("BINARY");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        testColumn.addPlatformColumn(platformColumn);

        expectedTable.addColumn(testColumn);
        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableNormalType() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(7);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.VARCHAR);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("NORMAL");
        when(stmtrs2.getString(3)).thenReturn("true");
        when(stmtrs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(stmtrs2.getShort(5)).thenReturn((short) 5321);
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        testColumn.setJdbcTypeCode(Types.VARCHAR);
        testColumn.setAutoIncrement(true);
        testColumn.setMappedType("VARCHAR");
        testColumn.setMappedTypeCode(12);
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        testColumn.setSize("254");

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        NonUniqueIndex index = new NonUniqueIndex();

        IndexColumn indexColumn = new IndexColumn();
        indexColumn.setName("testColumnName");
        indexColumn.setOrdinalPosition(5321);
        index.addColumn(indexColumn);
        index.setName("NOTRIGHTNAME");
        testColumn.addPlatformColumn(platformColumn);
        expectedTable.addColumn(testColumn);
        expectedTable.addIndex(index);

        assertEquals(expectedTable, testTable);

    }

    @Test
    void testReadTableFunctionBasedNormalType() throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        // "Real" Components
        OracleJdbcSqlTemplate testTemplate = new OracleJdbcSqlTemplate(dataSource, settings,
                new OracleLobHandler(settings.getJdbcLobHandling()), databaseInfo);
        OracleDatabasePlatform platform = new OracleDatabasePlatform(dataSource, settings);

        // Spied Components
        OracleDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        OracleJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        OracleDdlReader testReader = new OracleDdlReader(spyPlatform);
        OracleDdlReader spyReader = Mockito.spy(testReader);

        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);

        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);

        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(7);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.VARCHAR);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");

        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);

        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
                .thenReturn(stmt4);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);

        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("FUNCTION-BASED NORMAL");
        when(stmtrs2.getString(3)).thenReturn("true");
        when(stmtrs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(stmtrs2.getShort(5)).thenReturn((short) 5321);
        when(stmtrs2.getString(6)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);

        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);

        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);

        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        testColumn.setJdbcTypeCode(Types.VARCHAR);
        testColumn.setAutoIncrement(true);
        testColumn.setMappedType("VARCHAR");
        testColumn.setMappedTypeCode(12);
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        testColumn.setSize("254");

        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("oracle");
        platformColumn.setSize(-1);
        platformColumn.setType(DdlReaderTestConstants.TYPE_NAME_TEST_VALUE);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("oracle", platformColumn);

        NonUniqueIndex index = new NonUniqueIndex();

        IndexColumn indexColumn = new IndexColumn();
        indexColumn.setName("(testColumnName)");
        indexColumn.setOrdinalPosition(5321);
        index.addColumn(indexColumn);
        index.setName("NOTRIGHTNAME");
        testColumn.addPlatformColumn(platformColumn);
        expectedTable.addColumn(testColumn);
        expectedTable.addIndex(index);

        assertEquals(expectedTable, testTable);

    }

    protected String getResultSetSchemaName() {
        return DdlReaderTestConstants.TABLE_SCHEM;
    }

    public IDatabasePlatform getPlatform() {
        return platform;
    }
}