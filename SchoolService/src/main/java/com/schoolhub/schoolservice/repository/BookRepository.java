package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findAllByOrderByCreatedAtDesc();
    List<Book> findByCategoryOrderByCreatedAtDesc(String category);
    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author);
}
