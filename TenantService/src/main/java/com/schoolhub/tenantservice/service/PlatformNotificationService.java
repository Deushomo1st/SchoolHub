package com.schoolhub.tenantservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** Thin JDBC wrapper — inserts notifications directly into platform.notification
 *  so TenantService can fire alerts without an HTTP call to SchoolService. */
@Service
public class PlatformNotificationService {

    private final JdbcTemplate jdbc;

    public PlatformNotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Send the same notification to every platform owner and moderator. */
    public void broadcastToPlatformTeam(String type, String title, String body, String linkType, Long linkId) {
        List<Long> userIds = jdbc.queryForList(
            "SELECT id FROM platform.app_user WHERE role_id IN (SELECT id FROM platform.role WHERE name IN ('PLATFORM_OWNER','MODERATOR'))",
            Long.class);
        for (Long uid : userIds) {
            jdbc.update(
                "INSERT INTO platform.notification (recipient_user_id, tenant_id, type, title, body, link_type, link_id, created_at) VALUES (?,NULL,?,?,?,?,?,NOW())",
                uid, type, title, body, linkType, linkId);
        }
    }
}
