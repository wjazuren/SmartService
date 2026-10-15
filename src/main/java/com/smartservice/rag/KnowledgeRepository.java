package com.smartservice.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KnowledgeRepository {
    private final JdbcTemplate jdbc;

    public KnowledgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeDocument> findAll() {
        return jdbc.query("select id, title, category, content from knowledge_document order by id",
                (rs, row) -> new KnowledgeDocument(rs.getLong("id"), rs.getString("title"),
                        rs.getString("category"), rs.getString("content")));
    }
}
