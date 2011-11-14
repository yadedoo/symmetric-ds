package org.jumpmind.symmetric.db;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.jumpmind.symmetric.db.platform.derby.DerbyPlatform;
import org.jumpmind.symmetric.db.platform.firebird.FirebirdPlatform;
import org.jumpmind.symmetric.db.platform.greenplum.GreenplumPlatform;
import org.jumpmind.symmetric.db.platform.h2.H2Platform;
import org.jumpmind.symmetric.db.platform.hsqldb.HsqlDbPlatform;
import org.jumpmind.symmetric.db.platform.hsqldb2.HsqlDb2Platform;
import org.jumpmind.symmetric.db.platform.informix.InformixPlatform;
import org.jumpmind.symmetric.db.platform.interbase.InterbasePlatform;
import org.jumpmind.symmetric.db.platform.mssql.MSSqlPlatform;
import org.jumpmind.symmetric.db.platform.mysql.MySqlPlatform;
import org.jumpmind.symmetric.db.platform.oracle.OraclePlatform;
import org.jumpmind.symmetric.db.platform.postgresql.PostgreSqlPlatform;
import org.jumpmind.symmetric.db.platform.sqlite.SqLitePlatform;
import org.jumpmind.symmetric.db.platform.sybase.SybasePlatform;

/*
 * A factory of {@link IDatabasePlatform} instances based on a case
 * insensitive database name. Note that this is a convenience class as the platforms
 * can also simply be created via their constructors.
 */
public class DatabasePlatformFactory {

    /* The database name -> platform map. */
    private static Map<String, Class<? extends IDatabasePlatform>> platforms = null;

    /*
     * Returns the platform map.
     * 
     * @return The platform list
     */
    private static synchronized Map<String, Class<? extends IDatabasePlatform>> getPlatforms() {
        if (platforms == null) {
            // lazy initialization
            platforms = new HashMap<String, Class<? extends IDatabasePlatform>>();
            registerPlatforms();
        }
        return platforms;
    }
    
    /*
     * Creates a new platform for the given (case insensitive) database name or
     * returns null if the database is not recognized.
     * 
     * @param databaseName The name of the database (case is not important)
     * 
     * @return The platform or <code>null</code> if the database is not
     * supported
     */
    public static synchronized IDatabasePlatform createNewPlatformInstance(String databaseName)
            throws DdlUtilsException {
        Class<? extends IDatabasePlatform> platformClass = getPlatforms().get(
                databaseName.toLowerCase());

        try {
            return platformClass != null ? (IDatabasePlatform) platformClass.newInstance() : null;
        } catch (Exception ex) {
            throw new DdlUtilsException("Could not create platform for database " + databaseName,
                    ex);
        }
    }

    /*
     * Creates a new platform for the specified database. This is a shortcut
     * method that uses {@link PlatformUtils#determineDatabaseType(DataSource)}
     * to determine the parameter for {@link
     * #createNewPlatformInstance(String)}. Note that this method sets the data
     * source at the returned platform instance (method {@link
     * Platform#setDataSource(DataSource)}).
     * 
     * @param dataSource The data source for the database
     * 
     * @return The platform or <code>null</code> if the database is not
     * supported
     */
    public static synchronized IDatabasePlatform createNewPlatformInstance(DataSource dataSource)
            throws DdlUtilsException {
        // connects to the database and uses actual metadata info to get db name
        // and version to determine platform
        String nameVersion = determineDatabaseNameVersion(dataSource);
        return createNewPlatformInstance(nameVersion);
    }

    public static String determineDatabaseNameVersion(DataSource dataSource)
            throws DatabaseOperationException {
        Connection connection = null;

        try {
            connection = dataSource.getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            int majorVersion = metaData.getDatabaseMajorVersion();
            /*
             * if the productName is PostgreSQL, it could be either PostgreSQL
             * or Greenplum
             */
            /* query the metadata to determine which one it is */
            if (productName.equalsIgnoreCase(PostgreSqlPlatform.DATABASENAME)) {
                if (isGreenplumDatabase(connection)) {
                    productName = GreenplumPlatform.DATABASE;
                    majorVersion = getGreenplumVersion(connection);
                }
            }
            String productString = productName;
            if (majorVersion > 0) {
                productString += majorVersion;
            }

            return productString;
        } catch (SQLException ex) {
            throw new DatabaseOperationException("Error while reading the database metadata: "
                    + ex.getMessage(), ex);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    // we ignore this one
                }
            }
        }
    }

    private static boolean isGreenplumDatabase(Connection connection) {
        Statement stmt = null;
        ResultSet rs = null;
        String productName = null;
        boolean isGreenplum = false;
        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(GreenplumPlatform.SQL_GET_GREENPLUM_NAME);
            while (rs.next()) {
                productName = rs.getString(1);
            }
            if (productName != null && productName.equalsIgnoreCase(GreenplumPlatform.DATABASE)) {
                isGreenplum = true;
            }
        } catch (SQLException ex) {
            // ignore the exception, if it is caught, then this is most likely
            // not
            // a greenplum database
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException ex) {
            }
        }
        return isGreenplum;
    }

    private static int getGreenplumVersion(Connection connection) {
        Statement stmt = null;
        ResultSet rs = null;
        String versionName = null;
        int productVersion = 0;
        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(GreenplumPlatform.SQL_GET_GREENPLUM_VERSION);
            while (rs.next()) {
                versionName = rs.getString(1);
            }
            // take up to the first "." for version number
            if (versionName.indexOf('.') != -1) {
                versionName = versionName.substring(0, versionName.indexOf('.'));
            }
            try {
                productVersion = Integer.parseInt(versionName);
            } catch (NumberFormatException ex) {
                // if we can't convert this to a version number, leave it 0
            }
        } catch (SQLException ex) {
            // ignore the exception, if it is caught, then this is most likely
            // not
            // a greenplum database
        } finally {
            try {
                rs.close();
                stmt.close();
            } catch (SQLException ex) {
            }
        }
        return productVersion;
    }

    /*
     * Registers a new platform.
     * 
     * @param platformName The platform name
     * 
     * @param platformClass The platform class which must implement the {@link
     * Platform} interface
     */
    public static synchronized void registerPlatform(String platformName,
            Class<? extends IDatabasePlatform> platformClass) {
        addPlatform(getPlatforms(), platformName, platformClass);
    }

    /*
     * Registers the known platforms.
     */
    private static void registerPlatforms() {
        for (String name : H2Platform.DATABASENAMES) {
            addPlatform(platforms, name, H2Platform.class);
        }
        addPlatform(platforms, SqLitePlatform.DATABASENAME, SqLitePlatform.class);
        addPlatform(platforms, InformixPlatform.DATABASENAME, InformixPlatform.class);
        addPlatform(platforms, DerbyPlatform.DATABASENAME, DerbyPlatform.class);
        addPlatform(platforms, FirebirdPlatform.DATABASENAME, FirebirdPlatform.class);
        addPlatform(platforms, GreenplumPlatform.DATABASENAME, GreenplumPlatform.class);
        addPlatform(platforms, HsqlDbPlatform.DATABASENAME, HsqlDbPlatform.class);
        addPlatform(platforms, HsqlDb2Platform.DATABASENAME, HsqlDb2Platform.class);
        addPlatform(platforms, InterbasePlatform.DATABASENAME, InterbasePlatform.class);
        addPlatform(platforms, MSSqlPlatform.DATABASENAME, MSSqlPlatform.class);
        addPlatform(platforms, MySqlPlatform.DATABASENAME, MySqlPlatform.class);
        addPlatform(platforms, OraclePlatform.DATABASENAME, OraclePlatform.class);
        addPlatform(platforms, PostgreSqlPlatform.DATABASENAME, PostgreSqlPlatform.class);
        addPlatform(platforms, SybasePlatform.DATABASENAME, SybasePlatform.class);
    }

    private static synchronized void addPlatform(
            Map<String, Class<? extends IDatabasePlatform>> platformMap, String platformName,
            Class<? extends IDatabasePlatform> platformClass) {
        if (!IDatabasePlatform.class.isAssignableFrom(platformClass)) {
            throw new IllegalArgumentException("Cannot register class " + platformClass.getName()
                    + " because it does not implement the " + IDatabasePlatform.class.getName()
                    + " interface");
        }
        platformMap.put(platformName.toLowerCase(), platformClass);

    }
}
