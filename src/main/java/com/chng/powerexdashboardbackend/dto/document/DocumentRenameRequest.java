package com.chng.powerexdashboardbackend.dto.document;

import lombok.Data;

@Data
public class DocumentRenameRequest {
    private String title;
    private String directory;
    private String newFileName;
}
