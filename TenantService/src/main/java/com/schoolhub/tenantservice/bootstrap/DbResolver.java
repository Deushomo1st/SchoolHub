package com.schoolhub.tenantservice.bootstrap;

import java.io.*;
import java.sql.*;
import java.util.Properties;

/**
 * Lightweight DB resolver. AuthService handles full bootstrap; this just
 * resolves the DB name and prints a clean error if it doesn't exist.
 */
public class DbResolver {

    public static String resolve(String[] args) {
        // When running against a remote/cloud database, skip the localhost check
        // entirely — Spring Boot's application-supabase.properties has the real connection.
        for (String arg : args) {
            if (arg.startsWith("--spring.profiles.active=") && arg.contains("supabase")) {
                String name = resolveDbName();
                System.out.println("[SchoolHub] DB: " + name + " (cloud) — starting TenantService...");
                return name;
            }
        }
        String name = resolveDbName();
        if (!check(name)) {
            System.err.println();
            System.err.println("==============================================");
            System.err.println("  SchoolHub TenantService — Database missing");
            System.err.println("==============================================");
            System.err.println();
            System.err.println("  Database '" + name + "' does not exist or has no schema.");
            System.err.println();
            System.err.println("  Fix: start AuthService first (it bootstraps the DB),");
            System.err.println("       or run the launcher: SchoolHub-Manager.cmd → option 7");
            System.err.println();
            System.exit(1);
        }
        System.out.println("[SchoolHub] DB: " + name + " — starting TenantService...");
        return name;
    }

    private static String resolveDbName() {
        for (String path : new String[]{"active-db.properties", "../active-db.properties"}) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    Properties p = new Properties();
                    p.load(new FileReader(f));
                    String n = p.getProperty("schoolhub.db.name");
                    if (n != null && !n.isBlank()) return n.trim();
                } catch (IOException ignored) {}
            }
        }
        String env = System.getenv("SCHOOLHUB_DB_NAME");
        return (env != null && !env.isBlank()) ? env.trim() : "schoolhub";
    }

    private static boolean check(String name) {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/" + name, "postgres", "postgres");
             PreparedStatement ps = c.prepareStatement(
                 "SELECT count(*) FROM information_schema.tables WHERE table_schema='platform' AND table_name='app_user'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
