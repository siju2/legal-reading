package com.example.project.legal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:encrypted-persistence;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop"})
class EncryptedPersistenceTest {
    @Autowired NotebookService notebooks;@Autowired NotebookRepository books;@Autowired JdbcTemplate jdbc;
    @Test void rawSqlCannotReadContentAndOwnedSearchStillWorks(){
        var b=notebooks.create("crypto-test","비밀 제목 100%_","주거",null);
        notebooks.addEvent("crypto-test",b.id,java.time.LocalDate.now(),"기타","증빙 검색 단어","비밀 메모");
        String raw=jdbc.queryForObject("select title from legal_notebooks where id=?",String.class,b.id);
        assertThat(raw).startsWith("LRG1:").doesNotContain("비밀");
        assertThat(books.findById(b.id).orElseThrow().title).isEqualTo("비밀 제목 100%_");
        String event=jdbc.queryForObject("select note from legal_timeline where notebook_id=?",String.class,b.id);
        assertThat(event).startsWith("LRG1:").doesNotContain("메모");
    }
}
