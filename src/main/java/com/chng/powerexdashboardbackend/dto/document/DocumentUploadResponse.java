package com.chng.powerexdashboardbackend.dto.document;

import com.chng.powerexdashboardbackend.entities.document.DocumentFile;
import lombok.Data;

@Data
public class DocumentUploadResponse {
    private boolean success;
    private String message;
    private DocumentFile data;
}
