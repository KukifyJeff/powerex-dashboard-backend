package com.chng.powerexdashboardbackend.dto.document;

import com.chng.powerexdashboardbackend.entities.document.DocumentFile;
import lombok.Data;

@Data
public class DocumentDetailResponse {
    private DocumentFile document;
    private String content;
}
