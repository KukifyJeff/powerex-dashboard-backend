package com.chng.powerexdashboardbackend.responses.importdata;

import lombok.Data;

@Data
public class ImportDataRestorePointItem {
    private Long id;
    private String eventType;
    private String triggerAction;
    private Long referenceJobId;
    private Long referenceVersionId;
    private Long fromVersionId;
    private Long toVersionId;
    private String binlogFile;
    private Long binlogPosition;
    private String gtidSet;
    private String operator;
    private String note;
    private String createdAt;
}
