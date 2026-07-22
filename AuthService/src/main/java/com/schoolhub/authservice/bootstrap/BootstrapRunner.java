package com.schoolhub.authservice.bootstrap;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Runs BEFORE Spring Boot to ensure the database exists.
 * Handles both launcher-first and IntelliJ-first paths.
 *
 * Resolution order:
 *   1. active-db.properties (tries ./ then ../)
 *   2. SCHOOLHUB_DB_NAME env var
 *   3. "schoolhub"
 */
public class BootstrapRunner {

    private static final String DB_HOST = "localhost";
    private static final int    DB_PORT = 5432;
    private static final String DB_USER = "postgres";
    private static String dbPassword = "postgres";

    private static String resolvedDbName;
    private static String projectRoot;

    public static void run(String[] args) {
        projectRoot = findProjectRoot();
        resolvedDbName = resolveDbName();
        System.out.println("[SchoolHub] Active DB: " + resolvedDbName);

        // When running against a remote/cloud database, skip the localhost bootstrap
        // entirely — Spring Boot's application-supabase.properties has the real connection
        // details, and the Manager already handled DB creation + schema via psql.
        boolean cloudMode = false;
        for (String arg : args) {
            if (arg.startsWith("--spring.profiles.active=") && arg.contains("supabase")) {
                cloudMode = true;
                break;
            }
        }
        if (cloudMode) {
            System.out.println("[SchoolHub] Cloud mode (supabase profile) — skipping localhost bootstrap.");
            System.out.println("[SchoolHub] DB must already exist on the cloud server (use Manager option 7).");
            System.out.println("[SchoolHub] Starting Spring Boot...");
            return;
        }

        if (!connectToPostgres()) {
            System.err.println("[SchoolHub] Cannot connect to PostgreSQL at " + DB_HOST + ":" + DB_PORT);
            System.err.println("  Check that PostgreSQL is running and the password is correct.");
            System.exit(1);
        }

        if (databaseExists(resolvedDbName)) {
            if (!schemaApplied(resolvedDbName)) {
                System.err.println("[SchoolHub] Database '" + resolvedDbName + "' exists but schema is NOT applied.");
                System.err.println("  Re-run the launcher (option 7) or apply db/00_platform.sql manually.");
                System.exit(1);
            }
            System.out.println("[SchoolHub] Database ready. Starting Spring Boot...");
            return;
        }

        // DB doesn't exist — interactive menu
        interactiveMenu();
    }

    // ---- DB name resolution ------------------------------------------------

    private static String resolveDbName() {
        // 1. Try active-db.properties at ./ then ../
        for (String path : new String[]{"active-db.properties", "../active-db.properties"}) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    Properties p = new Properties();
                    p.load(new FileReader(f));
                    String name = p.getProperty("schoolhub.db.name");
                    if (name != null && !name.isBlank()) return name.trim();
                } catch (IOException ignored) {}
            }
        }
        // 2. Env var
        String env = System.getenv("SCHOOLHUB_DB_NAME");
        if (env != null && !env.isBlank()) return env.trim();
        // 3. Default
        return "schoolhub";
    }

    // ---- Project root (find db/00_platform.sql) ---------------------------

    private static String findProjectRoot() {
        File dir = new File(".").getAbsoluteFile();
        for (int i = 0; i < 5; i++) {
            if (new File(dir, "db/00_platform.sql").exists()) return dir.getAbsolutePath();
            File up = dir.getParentFile();
            if (up == null || up.equals(dir)) break;
            dir = up;
        }
        return new File(".").getAbsolutePath(); // fallback
    }

    // ---- PostgreSQL connection ---------------------------------------------

    private static Connection newConnection(String db) {
        try {
            String url = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + db;
            return DriverManager.getConnection(url, DB_USER, dbPassword);
        } catch (SQLException e) {
            return null;
        }
    }

    private static boolean connectToPostgres() {
        try (Connection c = newConnection("postgres")) {
            return c != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean databaseExists(String name) {
        try (Connection c = newConnection("postgres");
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean schemaApplied(String db) {
        try (Connection c = newConnection(db);
             PreparedStatement ps = c.prepareStatement(
                 "SELECT count(*) FROM information_schema.tables WHERE table_schema='platform' AND table_name='app_user'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // ---- Interactive menu (DB missing) -------------------------------------

    /**
     * Splits a multi-statement SQL string into individual statements,
     * ignoring semicolons inside {@code --} single-line comments.
     */
    private static List<String> splitSqlStatements(String sql) {
        List<String> stmts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String line : sql.split("\n")) {
            // Strip -- comments (only outside string literals — safe for schema files)
            int commentIdx = line.indexOf("--");
            String codeLine = (commentIdx >= 0) ? line.substring(0, commentIdx) : line;
            buf.append(codeLine).append("\n");
            if (codeLine.trim().endsWith(";")) {
                String stmt = buf.toString().trim();
                // Remove trailing semicolon then whitespace
                if (stmt.endsWith(";")) stmt = stmt.substring(0, stmt.length() - 1).trim();
                if (!stmt.isEmpty()) stmts.add(stmt);
                buf.setLength(0);
            }
        }
        String remainder = buf.toString().trim();
        if (!remainder.isEmpty()) stmts.add(remainder);
        return stmts;
    }

    private static void interactiveMenu() {
        System.out.println();
        System.out.println("==============================================");
        System.out.println("  SchoolHub — Database Setup");
        System.out.println("==============================================");
        System.out.println();
        System.out.println("  Database '" + resolvedDbName + "' does not exist.");
        System.out.println();
        System.out.println("  [1] Create it now (schema + platform owner)");
        System.out.println("  [2] Pick a different database");
        System.out.println("  [3] Exit");
        System.out.println();

        Console console = System.console();
        if (console == null) {
            System.err.println("  No interactive console available. Run the launcher instead.");
            System.err.println("    Double-click: SchoolHub-Manager.cmd → option 7");
            System.exit(1);
        }

        String choice = console.readLine("  Choose [1]: ").trim();
        if (choice.isEmpty()) choice = "1";

        switch (choice) {
            case "1" -> createDatabase(console);
            case "2" -> pickDatabase(console);
            default -> { System.out.println("  Exiting."); System.exit(1); }
        }
    }

    private static void createDatabase(Console console) {
        // Password prompt (only if default fails)
        try (Connection c = newConnection("postgres")) {
            if (c == null) {
                char[] pw = console.readPassword("  Postgres password for '" + DB_USER + "': ");
                dbPassword = new String(pw);
                if (newConnection("postgres") == null) {
                    System.err.println("  Auth failed.");
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("  Connection failed: " + e.getMessage());
            System.exit(1);
        }

        // Create DB
        System.out.println("  Creating database '" + resolvedDbName + "'...");
        try (Connection c = newConnection("postgres");
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE \"" + resolvedDbName + "\"");
            System.out.println("  [OK] Database created.");
        } catch (SQLException e) {
            System.err.println("  [FAIL] " + e.getMessage());
            System.exit(1);
        }

        // Apply schema
        File schemaFile = new File(projectRoot, "db/00_platform.sql");
        if (!schemaFile.exists()) {
            System.err.println("  [FAIL] Schema file not found: " + schemaFile.getAbsolutePath());
            System.exit(1);
        }
        System.out.println("  Applying platform schema...");
        try (Connection c = newConnection(resolvedDbName);
             Statement s = c.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            List<String> statements = splitSqlStatements(Files.readString(schemaFile.toPath()));
            for (String stmt : statements) {
                if (!stmt.isBlank()) s.execute(stmt);
            }
            System.out.println("  [OK] Schema applied.");
        } catch (Exception e) {
            System.err.println("  [FAIL] " + e.getMessage());
            System.exit(1);
        }

        // Bootstrap owner
        String ownerEmail = console.readLine("  Owner email [owner@schoolhub.local]: ").trim();
        if (ownerEmail.isEmpty()) ownerEmail = "owner@schoolhub.local";
        char[] pw1 = console.readPassword("  Owner password [Owner123!]: ");
        String ownerPassword = new String(pw1);
        if (ownerPassword.isEmpty()) ownerPassword = "Owner123!";

        System.out.println("  Creating platform owner...");
        try (Connection c = newConnection(resolvedDbName);
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO platform.app_user (email, password_hash, first_name, last_name, role_id, account_status) " +
                 "VALUES (?, crypt(?, gen_salt('bf', 10)), 'Platform', 'Owner', " +
                 "(SELECT id FROM platform.role WHERE name='PLATFORM_OWNER'), 'active')")) {
            ps.setString(1, ownerEmail);
            ps.setString(2, ownerPassword);
            ps.executeUpdate();
            System.out.println("  [OK] Owner created: " + ownerEmail);
        } catch (SQLException e) {
            System.err.println("  [FAIL] " + e.getMessage());
            System.exit(1);
        }

        // Write active-db.properties if it doesn't exist
        File propsFile = new File(projectRoot, "active-db.properties");
        if (!propsFile.exists()) {
            try {
                Files.writeString(propsFile.toPath(),
                    "# SchoolHub - active database. Last set: " + java.time.Instant.now() + "\n" +
                    "schoolhub.db.name=" + resolvedDbName + "\n");
                System.out.println("  [OK] Wrote active-db.properties");
            } catch (IOException e) {
                System.err.println("  [WARN] Could not write active-db.properties: " + e.getMessage());
            }
        }

        System.out.println();
        System.out.println("  Setup complete. Starting SchoolHub...");
        System.out.println("  Portal: http://localhost:9000");
        System.out.println("  Login:  " + ownerEmail + " / " + ownerPassword);
        System.out.println();
    }

    private static void pickDatabase(Console console) {
        try (Connection c = newConnection("postgres");
             PreparedStatement ps = c.prepareStatement(
                 "SELECT datname FROM pg_database WHERE datistemplate=false AND datname<>'postgres' ORDER BY datname");
             ResultSet rs = ps.executeQuery()) {

            List<String> dbs = new ArrayList<>();
            while (rs.next()) dbs.add(rs.getString(1));

            if (dbs.isEmpty()) {
                System.out.println("  No databases found. Create one first.");
                System.exit(1);
            }

            System.out.println();
            for (int i = 0; i < dbs.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + dbs.get(i));
            }
            String pick = console.readLine("  Choose: ").trim();
            int idx;
            try { idx = Integer.parseInt(pick) - 1; } catch (NumberFormatException e) { System.exit(1); return; }
            if (idx < 0 || idx >= dbs.size()) { System.exit(1); return; }

            resolvedDbName = dbs.get(idx);
            System.out.println("  Using database: " + resolvedDbName);
            if (!schemaApplied(resolvedDbName)) {
                System.err.println("  [WARN] Schema not applied. Run the launcher (option 7) to apply it.");
                System.exit(1);
            }
        } catch (SQLException e) {
            System.err.println("  [FAIL] " + e.getMessage());
            System.exit(1);
        }
    }
}
