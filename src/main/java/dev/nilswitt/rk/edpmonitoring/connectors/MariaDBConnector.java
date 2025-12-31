package dev.nilswitt.rk.edpmonitoring.connectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;

public class MariaDBConnector {

    private static final Logger LOGGER = LogManager.getLogger(MariaDBConnector.class);

    String url;
    String user;
    String pass;


    public MariaDBConnector(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    public boolean testConnection() {
        LOGGER.info("Connecting to MariaDB at " + url + " with user " + user);
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            if (conn != null) {
                LOGGER.info("Connection to MariaDB established successfully.");
                conn.close();
                return true;
            } else {
                LOGGER.error("Failed to establish connection to MariaDB.");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.error("Connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }

    public ArrayList<WorkerOutbox> getWorkerOutbox() {

        String query = "SELECT id,pk,payload,created_at,status,correlation_id FROM webhook_outbox WHERE status = 'NEW'";
        ArrayList<WorkerOutbox> outbox = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    Object val = rs.getObject(i);
                    row.append(val != null ? val.toString() : "NULL");
                    if (i < cols) row.append(" | ");
                }
                WorkerOutbox wo = new WorkerOutbox(
                        rs.getInt("id"),
                        rs.getString("pk"),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at"),
                        rs.getString("status"),
                        rs.getString("correlation_id")
                );
                outbox.add(wo);
            }


        } catch (SQLException e) {
            LOGGER.error("Database error: {}", e.getMessage(), e);
            System.exit(2);
        } catch (Exception e) {
            LOGGER.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(3);
        }
        return outbox;
    }

    public class WorkerOutbox {
        public int id;
        public String pk;
        public String payload;
        public Timestamp createdAt;
        public String status;
        public String correlationId;

        public WorkerOutbox(int id, String pk, String payload, Timestamp createdAt, String status, String correlationId) {
            this.id = id;
            this.pk = pk;
            this.payload = payload;
            this.createdAt = createdAt;
            this.status = status;
            this.correlationId = correlationId;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public Timestamp getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Timestamp createdAt) {
            this.createdAt = createdAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        @Override
        public String toString() {
            return "WorkerOutbox{" +
                    "id=" + id +
                    ", pk='" + pk + '\'' +
                    ", payload='" + payload + '\'' +
                    ", createdAt=" + createdAt +
                    ", status='" + status + '\'' +
                    ", correlationId='" + correlationId + '\'' +
                    '}';
        }
    }
}
