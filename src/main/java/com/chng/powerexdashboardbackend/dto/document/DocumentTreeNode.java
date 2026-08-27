package com.chng.powerexdashboardbackend.dto.document;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentTreeNode {
    private String key;
    private String label;
    private String type; // "directory" or "file"
    private String path;
    private Long documentId;
    private List<DocumentTreeNode> children = new ArrayList<>();
}
