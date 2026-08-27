package com.chng.powerexdashboardbackend.controller.document;

import com.chng.powerexdashboardbackend.dto.document.DocumentDetailResponse;
import com.chng.powerexdashboardbackend.dto.document.DocumentUploadResponse;
import com.chng.powerexdashboardbackend.entities.document.DocumentFile;
import com.chng.powerexdashboardbackend.services.document.DocumentUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentUploadService documentUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "directory", required = false, defaultValue = "") String directory,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description) {
        DocumentUploadResponse response = documentUploadService.uploadDocument(file, directory, title, description);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public List<DocumentFile> listDocuments(@RequestParam(value = "directory", required = false) String directory) {
        return documentUploadService.listDocuments(directory);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailResponse> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentUploadService.getDocumentDetail(id));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<DocumentDetailResponse> getDocumentPreview(@PathVariable Long id) {
        return ResponseEntity.ok(documentUploadService.getDocumentDetail(id));
    }

    @GetMapping("/directories")
    public List<String> listDirectories() {
        return documentUploadService.listDirectories();
    }

    @GetMapping("/tree")
    public List<com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode> getDocumentTree() {
        return documentUploadService.buildDirectoryTree();
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<DocumentFile> renameDocument(
            @PathVariable Long id,
            @RequestBody com.chng.powerexdashboardbackend.dto.document.DocumentRenameRequest request) {
        return ResponseEntity.ok(documentUploadService.renameDocument(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDocument(@PathVariable Long id) {
        documentUploadService.deleteDocument(id);
        return ResponseEntity.ok("Document deleted successfully.");
    }
}
