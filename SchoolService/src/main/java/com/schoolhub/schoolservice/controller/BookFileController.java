package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.model.Book;
import com.schoolhub.schoolservice.service.LibraryService;
import com.schoolhub.schoolservice.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/library/books")
public class BookFileController {

    private final LibraryService libraryService;

    @Value("${library.upload.dir:uploads/books}")
    private String uploadDir;

    public BookFileController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @PostMapping("/{bookId}/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadFile(@PathVariable Long bookId,
                                        @RequestParam("file") MultipartFile file,
                                        Authentication auth) {
        assertLibrarian(auth);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is empty"));
        }

        String schema = TenantContext.get();
        if (schema == null) {
            return ResponseEntity.status(400).body(Map.of("message", "Tenant context missing"));
        }

        try {
            Path dir = Paths.get(uploadDir, schema);
            Files.createDirectories(dir);

            String ext = getExtension(file.getOriginalFilename());
            if (!ext.matches("(?i)(pdf|epub)")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Only PDF and EPUB files allowed"));
            }

            Path filePath = dir.resolve(bookId + "." + ext.toLowerCase());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            Book book = libraryService.getBook(bookId);
            book.setFilePath(filePath.toString());
            book.setFileType(ext.toUpperCase());
            book = libraryService.updateBook(bookId, book);

            return ResponseEntity.ok(Map.of(
                "message", "File uploaded successfully",
                "filePath", book.getFilePath(),
                "fileType", book.getFileType()
            ));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to save file: " + e.getMessage()));
        }
    }

    @GetMapping("/{bookId}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long bookId) {
        Book book = libraryService.getBook(bookId);
        if (book.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path filePath = Paths.get(book.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] content = Files.readAllBytes(filePath);
            String contentType = book.getFileType().equalsIgnoreCase("PDF") 
                ? "application/pdf" 
                : "application/epub+zip";

            return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "inline; filename=\"" + bookId + "." + book.getFileType().toLowerCase() + "\"")
                .body(content);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    private void assertLibrarian(Authentication auth) {
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN"))) return;
        Long userId = (Long) auth.getPrincipal();
        if (libraryService.isLibrarian(userId)) return;
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return;
        throw new org.springframework.security.access.AccessDeniedException("Library staff access required");
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1) : "";
    }
}
