package org.jumpserver.chen.modules.sqlserver;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jumpserver.chen.framework.datasource.Datasource;
import org.jumpserver.chen.framework.datasource.base.BaseConnectionManager;
import org.jumpserver.chen.framework.datasource.entity.DBConnectInfo;
import org.jumpserver.chen.framework.datasource.sql.SQL;
import org.jumpserver.chen.framework.driver.DriverClassLoader;
import org.jumpserver.chen.framework.ssl.JKSGenerator;

import java.lang.reflect.InvocationTargetException;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

@Slf4j
public class SQLServerConnectionManager extends BaseConnectionManager {

    private static final String jdbcUrlTemplate = "jdbc:sqlserver://${host}:${port};DatabaseName=${db};trustServerCertificate=true;";
    private String jdbcUrl;

    private String driverClassloaderName = "mssql-jdbc-12.2.0.jre11.jar";

    public SQLServerConnectionManager(DBConnectInfo connectInfo, Datasource datasource) {
        super(connectInfo, datasource);
        this.sqlActuator = new SQLServerActuator(this);
    }

    @Override
    public String getDriverClassName() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    @Override
    public String getContextKey() {
        return "database";
    }

    @Override
    public Driver getDriver() {
        var driverClassLoaders = this.getDriverClassLoaders();

        if (this.getConnectInfo().getOptions().containsKey("version")) {
            var version = (String) this.getConnectInfo().getOptions().remove("version");
            if (version.equals("<2014")) {
                this.driverClassloaderName = "mssql-jdbc-6.4.0.jre9.jar";
            }
        }
        for (DriverClassLoader classLoader : driverClassLoaders) {
            if (!classLoader.getJarName().equals(this.driverClassloaderName)) {
                continue;
            }
            try {
                log.info("select driver jar: {}", this.driverClassloaderName);
                return (Driver) classLoader.loadClass(this.getDriverClassName()).getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException |
                     InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("driver not load");
    }

    @Override
    public void ping() throws SQLException {
        var url = this.getConnectInfo().toJDBCUrl(jdbcUrlTemplate);
        this.ping(url);
        this.jdbcUrl = url;
    }

    protected void setSSLProps(Properties props) {
        if (this.getConnectInfo().getOptions().get("useSSL") != null
                && (boolean) this.getConnectInfo().getOptions().get("useSSL")) {
            props.setProperty("encrypt", "true");
            props.setProperty("trustServerCertificate", "true");
            var jksGenerator = new JKSGenerator();
            if ((boolean) this.getConnectInfo().getOptions().get("verifyServerCertificate")) {
                props.setProperty("trustServerCertificate", "false");
                jksGenerator.setCaCert((String) this.getConnectInfo().getOptions().get("caCert"));

                var caCertPath = jksGenerator.generateCaJKS();
                props.setProperty("trustCertificateKeyStoreUrl", "file:" + caCertPath);
                props.setProperty("trustCertificateKeyStorePassword", JKSGenerator.JSK_PASS);

            }
            if (StringUtils.isNotBlank((String) this.getConnectInfo().getOptions().get("clientCert"))) {
                jksGenerator.setClientCert((String) this.getConnectInfo().getOptions().get("clientCert"));
                jksGenerator.setClientKey((String) this.getConnectInfo().getOptions().get("clientKey"));
                var clientCertPath = jksGenerator.generateClientJKS();
                props.setProperty("clientKey", "file:" + clientCertPath);
                props.setProperty("clientKeyPassword", JKSGenerator.JSK_PASS);

            }
        }
    }


    private static final String SQL_GET_VERSION = "SELECT @@VERSION";

    @Override
    public String getVersion() throws SQLException {
        var result = this.sqlActuator.execute(SQL.of(SQL_GET_VERSION));
        return (String) result.getData().get(0).get(0);
    }

    @Override
    public String getJDBCUrl() {
        return this.jdbcUrl;
    }


    @Override
    public String getDisplayJDBCUrl() {
        return this.getConnectInfo().toDisplayJDBCUrl(jdbcUrlTemplate);
    }


    @Override
    public String getJDBCUrl(String database) {
        return this.getConnectInfo().toJDBCUrl(jdbcUrlTemplate, database);
    }
}
