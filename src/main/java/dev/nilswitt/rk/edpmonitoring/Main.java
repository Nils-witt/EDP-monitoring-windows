package dev.nilswitt.rk.edpmonitoring;

import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:mariadb://localhost:3306/edp_monitoring");
        String user = System.getenv().getOrDefault("DB_USER", "edp_user");
        String pass = System.getenv().getOrDefault("DB_PASSWORD", "edp_password");

        Runnable helloRunnable = () -> getDBRows(url, user, pass);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(helloRunnable, 0, 3, TimeUnit.SECONDS);

    }


    private static void getDBRows(String url, String user, String pass) {

        String query = "SELECT id,pk,payload,created_at,status,correlation_id FROM webhook_outbox WHERE status = 'NEW'";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            // Print header
            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                header.append(meta.getColumnLabel(i));
                if (i < cols) header.append(" | ");
            }
            System.out.println(header.toString());

            // Print separator
            for (int i = 0; i < Math.max(header.length(), 0); i++) System.out.print('-');
            System.out.println();

            boolean any = false;
            while (rs.next()) {
                any = true;
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    Object val = rs.getObject(i);
                    row.append(val != null ? val.toString() : "NULL");
                    if (i < cols) row.append(" | ");
                }
                System.out.println(row.toString());
            }

            if (!any) System.out.println("[no rows]");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(3);
        }
    }
}
