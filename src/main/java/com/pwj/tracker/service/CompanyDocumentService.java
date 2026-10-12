package com.pwj.tracker.service;

import com.pwj.tracker.model.CompanyDocument;
import com.pwj.tracker.repository.CompanyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyDocumentService {

    private final CompanyDocumentRepository repo;

    public List<CompanyDocument> list() {
        return repo.findAllByOrderByUploadedAtDesc();
    }

    @Transactional
    public CompanyDocument add(String title, String category, String fileUrl, String fileName, String uploadedBy) {
        if (title == null || title.isBlank()) throw new RuntimeException("Title is required");
        if (fileUrl == null || fileUrl.isBlank()) throw new RuntimeException("File URL is required");
        return repo.save(CompanyDocument.builder()
                .title(title.trim())
                .category(category != null ? category.trim() : null)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .uploadedBy(uploadedBy)
                .build());
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new RuntimeException("Document not found: " + id);
        repo.deleteById(id);
    }
}
