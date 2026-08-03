package com.tzp.zjzx.agent.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.regex.Pattern;

@Repository
public class ProductVectorDocumentRepository {

    private static final Pattern SQL_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final String qualifiedTableName;

    public ProductVectorDocumentRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}")
            String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:" +
                    "product_knowledge_vector}")
            String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.qualifiedTableName = requireIdentifier(schemaName)
                + "." + requireIdentifier(tableName);
    }

    public List<String> findDocumentIdsByProductId(Long productId) {
        return jdbcTemplate.queryForList(
                "select id from " + qualifiedTableName
                        + " where metadata->>'documentType' = ?"
                        + " and metadata->>'productId' = ?",
                String.class,
                "product",
                productId.toString()
        );
    }

    private String requireIdentifier(String value) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid PGVector SQL identifier"
            );
        }
        return value;
    }
}
