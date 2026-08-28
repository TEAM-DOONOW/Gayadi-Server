package com.gayadi.server.support;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
public class InquiryService {

    private final JdbcClient jdbc;
    private final UserService users;
    private final KeyHelper keyHelper;

    public InquiryService(JdbcClient jdbc, UserService users, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.users = users;
        this.keyHelper = keyHelper;
    }

    @Transactional
    public InquiryReceipt submit(long userId, InquiryRequest request) {
        users.lockActive(userId);
        long id = keyHelper.insert("""
                INSERT INTO support_inquiries
                    (user_id, category, title, message, contact_email)
                VALUES (?, ?, ?, ?, ?)
                """,
                userId, request.parsedCategory().name(), request.title().trim(),
                request.message().trim(), request.contactEmail().trim().toLowerCase(Locale.ROOT));
        Map<String, Object> row = jdbc.sql("""
                SELECT id, category, status, created_at FROM support_inquiries WHERE id = ?
                """).param(id).query().singleRow();
        return new InquiryReceipt(
                RowSupport.longValue(row, "id"),
                RowSupport.strValue(row, "category").toLowerCase(Locale.ROOT),
                RowSupport.strValue(row, "status"),
                AppDateFormat.databaseDateTime(RowSupport.value(row, "created_at")));
    }
}
