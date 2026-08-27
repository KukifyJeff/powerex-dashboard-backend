package com.chng.powerexdashboardbackend.services.document;

import com.chng.powerexdashboardbackend.dto.document.DocumentDetailResponse;
import com.chng.powerexdashboardbackend.dto.document.DocumentRenameRequest;
import com.chng.powerexdashboardbackend.dto.document.DocumentUploadResponse;
import com.chng.powerexdashboardbackend.entities.document.DocumentFile;
import com.chng.powerexdashboardbackend.mapper.document.DocumentFileMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentUploadService {

    private final DocumentFileMapper documentFileMapper;

    @Value("${app.document.storage-path:./uploads/docs}")
    private String storagePath;

    public DocumentUploadService(DocumentFileMapper documentFileMapper) {
        this.documentFileMapper = documentFileMapper;
    }

    public DocumentUploadResponse uploadDocument(MultipartFile file,
                                                String directory,
                                                String title,
                                                String description) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a markdown file to upload.");
        }

        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : file.getName();
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".md") && !lowerName.endsWith(".markdown") && !lowerName.endsWith(".mdx")) {
            throw new IllegalArgumentException("Only markdown files are supported (.md, .markdown, .mdx).");
        }

        String normalizedDirectory = normalizeDirectory(directory);
        Path rootDirectory = Paths.get(storagePath).toAbsolutePath().normalize();
        Path targetDirectory = rootDirectory.resolve(normalizedDirectory).normalize();
        if (!targetDirectory.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Invalid document directory.");
        }

        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create document directory.", e);
        }

        String baseName = extractSafeBaseName(originalName);
        String extension = extractExtension(originalName);
        String storedName = baseName + extension;
        Path targetFile = targetDirectory.resolve(storedName);
        int suffix = 1;
        while (Files.exists(targetFile)) {
            storedName = baseName + "_" + suffix + extension;
            targetFile = targetDirectory.resolve(storedName);
            suffix++;
        }

        try {
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save uploaded file.", e);
        }

        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "system";

        DocumentFile document = new DocumentFile();
        document.setTitle(StringUtils.hasText(title) ? title : baseName);
        document.setDescription(StringUtils.hasText(description) ? description : null);
        document.setOriginalName(originalName);
        document.setStoredName(storedName);
        document.setDirectory(normalizedDirectory);
        document.setFilePath(targetFile.toString());
        document.setContentType(file.getContentType());
        document.setSizeBytes(file.getSize());
        document.setUploadedBy(username);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());

        documentFileMapper.insert(document);

        DocumentUploadResponse response = new DocumentUploadResponse();
        response.setSuccess(true);
        response.setMessage("Document uploaded successfully.");
        response.setData(document);
        return response;
    }

    public List<DocumentFile> listDocuments(String directory) {
        List<DocumentFile> documents = documentFileMapper.selectList(null);
        if (StringUtils.hasText(directory)) {
            String normalized = normalizeDirectory(directory);
            documents = documents.stream()
                    .filter(doc -> normalized.equals(doc.getDirectory()))
                    .collect(Collectors.toList());
        }

        documents.sort(Comparator.comparing(DocumentFile::getDirectory, Comparator.nullsFirst(String::compareTo))
                .thenComparing(DocumentFile::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return documents;
    }

    public List<String> listDirectories() {
        return documentFileMapper.selectList(null).stream()
                .map(DocumentFile::getDirectory)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode> buildDirectoryTree() {
        List<DocumentFile> documents = documentFileMapper.selectList(null);
        Map<String, com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode> nodeMap = new LinkedHashMap<>();
        com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode root = new com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode();
        root.setKey("root");
        root.setLabel("Documents");
        root.setType("directory");
        root.setPath("/");
        nodeMap.put("root", root);

        for (DocumentFile doc : documents) {
            String dir = StringUtils.hasText(doc.getDirectory()) ? doc.getDirectory() : "";
            String[] segments = dir.isEmpty() ? new String[0] : dir.split("/");
            com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode current = root;
            String currentPath = "";

            for (String segment : segments) {
                if (!StringUtils.hasText(segment)) {
                    continue;
                }
                currentPath = currentPath.isEmpty() ? segment : currentPath + "/" + segment;
                String key = "dir:" + currentPath;
                com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode child = current.getChildren().stream()
                        .filter(n -> key.equals(n.getKey()))
                        .findFirst()
                        .orElse(null);
                if (child == null) {
                    child = new com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode();
                    child.setKey(key);
                    child.setLabel(segment);
                    child.setType("directory");
                    child.setPath(currentPath);
                    current.getChildren().add(child);
                }
                current = child;
            }

            com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode fileNode = new com.chng.powerexdashboardbackend.dto.document.DocumentTreeNode();
            fileNode.setKey("file:" + doc.getId());
            fileNode.setLabel(doc.getTitle());
            fileNode.setType("file");
            fileNode.setPath(doc.getDirectory() == null || doc.getDirectory().isBlank() ? doc.getStoredName() : doc.getDirectory() + "/" + doc.getStoredName());
            fileNode.setDocumentId(doc.getId());
            current.getChildren().add(fileNode);
        }

        return root.getChildren();
    }

    public DocumentDetailResponse getDocumentDetail(Long id) {
        DocumentFile document = documentFileMapper.selectById(id);
        if (document == null) {
            throw new IllegalArgumentException("Document not found.");
        }

        DocumentDetailResponse response = new DocumentDetailResponse();
        response.setDocument(document);

        try {
            response.setContent(Files.readString(Paths.get(document.getFilePath()), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            response.setContent("");
        }
        return response;
    }

    public DocumentFile renameDocument(Long id, DocumentRenameRequest request) {
        DocumentFile document = documentFileMapper.selectById(id);
        if (document == null) {
            throw new IllegalArgumentException("Document not found.");
        }

        String newDirectory = request != null && StringUtils.hasText(request.getDirectory()) ? normalizeDirectory(request.getDirectory()) : document.getDirectory();
        String newName = request != null && StringUtils.hasText(request.getNewFileName()) ? request.getNewFileName() : document.getStoredName();

        if (!newName.toLowerCase(Locale.ROOT).endsWith(".md")
                && !newName.toLowerCase(Locale.ROOT).endsWith(".markdown")
                && !newName.toLowerCase(Locale.ROOT).endsWith(".mdx")) {
            throw new IllegalArgumentException("Only markdown file names are supported.");
        }

        String safeFileName = extractSafeBaseName(newName) + extractExtension(newName);
        Path oldFile = Paths.get(document.getFilePath());
        Path newDir = Paths.get(storagePath).toAbsolutePath().normalize().resolve(newDirectory).normalize();
        if (!newDir.startsWith(Paths.get(storagePath).toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Invalid target directory.");
        }

        try {
            Files.createDirectories(newDir);
            Path newFile = newDir.resolve(safeFileName);
            Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);

            document.setTitle(StringUtils.hasText(request != null ? request.getTitle() : null) ? request.getTitle() : document.getTitle());
            document.setDirectory(newDirectory);
            document.setOriginalName(safeFileName);
            document.setStoredName(safeFileName);
            document.setFilePath(newFile.toString());
            document.setUpdatedAt(LocalDateTime.now());
            documentFileMapper.updateById(document);
            return document;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to rename document file.", ex);
        }
    }

    public void deleteDocument(Long id) {
        DocumentFile document = documentFileMapper.selectById(id);
        if (document == null) {
            throw new IllegalArgumentException("Document not found.");
        }

        Path filePath = Paths.get(document.getFilePath());
        try {
            Files.deleteIfExists(filePath);
            documentFileMapper.deleteById(id);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete document file.", ex);
        }
    }

    private String normalizeDirectory(String directory) {
        if (!StringUtils.hasText(directory)) {
            return "";
        }

        String normalized = directory.replace('\\', '/').trim();
        normalized = normalized.replaceAll("^/+", "").replaceAll("/+$", "");
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Directory path contains invalid segments.");
        }
        return normalized;
    }

    private String extractSafeBaseName(String originalName) {
        String bareName = Paths.get(originalName).getFileName().toString();
        String nameWithoutExt = bareName.contains(".") ? bareName.substring(0, bareName.lastIndexOf('.')) : bareName;
        return nameWithoutExt.replaceAll("[^a-zA-Z0-9._-]+", "_").trim();
    }

    private String extractExtension(String originalName) {
        String name = Paths.get(originalName).getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(lastDot) : ".md";
    }
}
