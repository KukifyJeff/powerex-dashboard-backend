package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.request.importdata.ImportConfirmRequest;
import com.chng.powerexdashboardbackend.request.importdata.ImportRollbackRecentRequest;
import com.chng.powerexdashboardbackend.request.importdata.ImportRollbackRequest;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataActionResponse;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataJobResponse;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataRestorePointItem;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataUploadResponse;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataVersionItem;
import com.chng.powerexdashboardbackend.services.importdata.ImportDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/import-data")
@RequiredArgsConstructor
public class ImportDataController {

    private final ImportDataService importDataService;

    @PostMapping(value = "/upload/longterm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportDataUploadResponse uploadLongterm(
            @RequestPart("files") MultipartFile[] files,
            @RequestParam(required = false) String createdBy) {
        try {
            return importDataService.uploadLongtermAndNormalize(files, createdBy);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @PostMapping(value = "/upload/spot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportDataUploadResponse uploadSpot(
            @RequestPart("files") MultipartFile[] files,
            @RequestParam(required = false) String createdBy) {
        try {
            return importDataService.uploadSpotAndNormalize(files, createdBy);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportDataUploadResponse upload(
            @RequestPart("files") MultipartFile[] files,
            @RequestParam(required = false) String createdBy) {
        try {
            return importDataService.uploadAndNormalize(files, createdBy);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ImportDataJobResponse getJob(@PathVariable Long jobId) {
        try {
            return importDataService.getJob(jobId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping("/confirm")
    public ImportDataActionResponse confirm(@Valid @org.springframework.web.bind.annotation.RequestBody ImportConfirmRequest request) {
        try {
            return importDataService.confirmJob(request.getJobId(), request.getAdminPassword(), request.getRemark());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @GetMapping("/versions")
    public List<ImportDataVersionItem> versions() {
        return importDataService.listVersions();
    }

    @GetMapping("/restore-points")
    public List<ImportDataRestorePointItem> restorePoints() {
        return importDataService.listRestorePoints();
    }

    @PostMapping("/rollback")
    public ImportDataActionResponse rollback(@Valid @org.springframework.web.bind.annotation.RequestBody ImportRollbackRequest request) {
        try {
            return importDataService.rollbackToVersion(request.getVersionId(), request.getAdminPassword(), request.getReason());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @PostMapping("/rollback/recent")
    public ImportDataActionResponse rollbackRecent(@Valid @org.springframework.web.bind.annotation.RequestBody ImportRollbackRecentRequest request) {
        try {
            return importDataService.rollbackRecentVersions(request.getSteps(), request.getAdminPassword(), request.getReason());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }
}
