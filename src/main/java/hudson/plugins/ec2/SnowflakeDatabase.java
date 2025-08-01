package hudson.plugins.ec2;

import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.database.Database;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class SnowflakeDatabase extends Database {
    private static final Logger LOGGER = Logger.getLogger(SnowflakeDatabase.class.getName());
    
    private String url;
    private String user;
    private String password;
    private String warehouse;
    private String database;
    private String schema;
    private String role;
    
    private transient HikariDataSource dataSource;
    private static SnowflakeDatabase instance;
    
    @DataBoundConstructor
    public SnowflakeDatabase() {
        // Initialize default values
        this.schema = "PUBLIC";
    }
    
    public String getUrl() { return url; }
    @DataBoundSetter public void setUrl(String url) { this.url = url; }
    
    public String getUser() { return user; }
    @DataBoundSetter public void setUser(String user) { this.user = user; }
    
    public String getPassword() { return password; }
    @DataBoundSetter public void setPassword(String password) { this.password = password; }
    
    public String getWarehouse() { return warehouse; }
    @DataBoundSetter public void setWarehouse(String warehouse) { this.warehouse = warehouse; }
    
    public String getDatabase() { return database; }
    @DataBoundSetter public void setDatabase(String database) { this.database = database; }
    
    public String getSchema() { return schema; }
    @DataBoundSetter public void setSchema(String schema) { this.schema = schema; }
    
    public String getRole() { return role; }
    @DataBoundSetter public void setRole(String role) { this.role = role; }
    
    public static synchronized SnowflakeDatabase getInstance() {
        if (instance == null) {
            instance = new SnowflakeDatabase();
        }
        return instance;
    }
    
    @Override
    public DataSource getDataSource() throws SQLException {
        if (dataSource == null) {
            initializeDataSource();
        }
        return dataSource;
    }
    
    private void initializeDataSource() throws SQLException {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);
            
            Properties props = new Properties();
            if (warehouse != null) props.put("warehouse", warehouse);
            if (database != null) props.put("db", database);
            if (schema != null) props.put("schema", schema);
            if (role != null) props.put("role", role);
            
            config.setDataSourceProperties(props);
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            // Initialize the table if it doesn't exist
            initializeTable();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Snowflake database connection", e);
            throw new SQLException("Failed to initialize Snowflake database connection", e);
        }
    }
    
    private void initializeTable() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS ec2_run_instances_log (
                id INTEGER AUTOINCREMENT,
                region VARCHAR(50),
                availability_zone VARCHAR(50),
                event_id VARCHAR(100),
                min_count INTEGER,
                max_count INTEGER,
                instance_type VARCHAR(50),
                instances_provisioned INTEGER,
                controller_name VARCHAR(100),
                timestamp TIMESTAMP_NTZ,
                PRIMARY KEY (id)
            )
        """;
        
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(createTableSQL)) {
            stmt.execute();
            LOGGER.info("EC2 run instances log table initialized successfully");
        }
    }
    
    public void logRunInstancesRequest(String region, String availabilityZone, String eventId,
                                       int minCount, int maxCount, String instanceType,
                                       int instancesProvisioned) throws SQLException {
        String insertSQL = """
            INSERT INTO ec2_run_instances_log 
            (region, availability_zone, event_id, min_count, max_count, instance_type, instances_provisioned, controller_name, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        String controllerName = getControllerName();
        
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
            stmt.setString(1, region);
            stmt.setString(2, availabilityZone);
            stmt.setString(3, eventId);
            stmt.setInt(4, minCount);
            stmt.setInt(5, maxCount);
            stmt.setString(6, instanceType);
            stmt.setInt(7, instancesProvisioned);
            stmt.setString(8, controllerName);
            stmt.setTimestamp(9, Timestamp.from(Instant.now()));
            
            stmt.executeUpdate();
            LOGGER.log(Level.INFO, "Logged RunInstances request - Event ID: {0}, Region: {1}, Instance Type: {2}, Count: {3}",
                      new Object[]{eventId, region, instanceType, instancesProvisioned});
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to log RunInstances request to Snowflake", e);
            throw e;
        }
    }
    
    private String getControllerName() {
        try {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null) {
                return jenkins.getDisplayName();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get Jenkins controller name", e);
        }
        return "Unknown";
    }
    
    @Extension
    public static class DescriptorImpl extends Database.DatabaseDescriptor {
        @Override
        public String getDisplayName() {
            return "Snowflake Database";
        }
    }
}