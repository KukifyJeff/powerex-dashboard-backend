package com.chng.powerexdashboardbackend.services.importdata;

import com.chng.powerexdashboardbackend.enums.GenTypeEnum;
import com.chng.powerexdashboardbackend.enums.TransactionPeriodEnum;
import com.chng.powerexdashboardbackend.enums.TransactionTypeEnum;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataActionResponse;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataFileItem;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataJobResponse;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataRestorePointItem;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataUploadResponse;
import com.chng.powerexdashboardbackend.responses.importdata.ImportDataVersionItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImportDataService {

    private static final String JOB_STATUS_PROCESSING = "PROCESSING";
    private static final String JOB_STATUS_NORMALIZED = "NORMALIZED";
    private static final String JOB_STATUS_FAILED = "FAILED";
    private static final String JOB_STATUS_CONFIRMED = "CONFIRMED";
    private static final String VERSION_STATUS_ACTIVE = "ACTIVE";
    private static final String VERSION_STATUS_INACTIVE = "INACTIVE";
    private static final String ACTION_CONFIRM = "CONFIRM";
    private static final String ACTION_ROLLBACK = "ROLLBACK";
    private static final String EVENT_BEFORE_CONFIRM = "BEFORE_CONFIRM";
    private static final String EVENT_AFTER_CONFIRM = "AFTER_CONFIRM";
    private static final String EVENT_BEFORE_ROLLBACK = "BEFORE_ROLLBACK";
    private static final String EVENT_AFTER_ROLLBACK = "AFTER_ROLLBACK";
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");
    private static final Pattern PERIOD_PATTERN = Pattern.compile("(\\d{1,2})\\s*[-至到]\\s*(\\d{1,2})");
    private static final int MAX_REASON_ITEMS = 5;
    private static final int MAX_ERROR_MESSAGE_LEN = 1000;
    private static final int MAX_NOTE_LEN = 500;
    private static final int MAX_OPERATOR_LEN = 64;
    private static final String ADMIN_PASSWORD = "ChngPowerEx_2026";

    private final JdbcTemplate jdbcTemplate;
    private final TaskExecutor taskExecutor;
    private final DataFormatter dataFormatter = createDataFormatter();

    private static DataFormatter createDataFormatter() {
        DataFormatter formatter = new DataFormatter(Locale.SIMPLIFIED_CHINESE);
        formatter.setUseCachedValuesForFormulaCells(true);
        return formatter;
    }

    public ImportDataUploadResponse uploadAndNormalize(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeInternal(files, createdBy, UploadMode.MIXED);
    }

    public ImportDataUploadResponse uploadAndNormalizeAsync(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeAsyncInternal(files, createdBy, UploadMode.MIXED);
    }

    public ImportDataUploadResponse uploadLongtermAndNormalize(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeInternal(files, createdBy, UploadMode.LONGTERM_ONLY);
    }

    public ImportDataUploadResponse uploadLongtermAndNormalizeAsync(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeAsyncInternal(files, createdBy, UploadMode.LONGTERM_ONLY);
    }

    public ImportDataUploadResponse uploadSpotAndNormalize(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeInternal(files, createdBy, UploadMode.SPOT_ONLY);
    }

    public ImportDataUploadResponse uploadSpotAndNormalizeAsync(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeAsyncInternal(files, createdBy, UploadMode.SPOT_ONLY);
    }

    private ImportDataUploadResponse uploadAndNormalizeInternal(MultipartFile[] files, String createdBy, UploadMode mode) {
        long startedAt = System.currentTimeMillis();
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("files is required");
        }

        long jobId = createJob(files.length, createdBy);
        log.info("Import job {} started: mode={}, fileCount={}, createdBy={}", jobId, mode, files.length, createdBy);
        LookupMaps lookupMaps = loadLookupMaps();
        List<ImportDataFileItem> fileItems = new ArrayList<>();
        List<ParsedFile> parsedFiles = new ArrayList<>();
        List<NormalizedLongtermRow> allLongtermRows = new ArrayList<>();
        List<NormalizedSpotRow> allSpotRows = new ArrayList<>();
        LongtermDedupContext longtermDedupContext = mode == UploadMode.SPOT_ONLY ? new LongtermDedupContext() : loadExistingLongtermDedupContext();
        SpotDedupContext spotDedupContext = mode == UploadMode.LONGTERM_ONLY ? new SpotDedupContext() : loadExistingSpotDedupContext();
        log.info("Import job {} baseline loaded: longtermKeys={}, spotKeys={}", jobId, longtermDedupContext.baselineByKey.size(), spotDedupContext.baselineByKey.size());
        int failedFileCount = 0;

        for (MultipartFile file : files) {
            ParsedFile parsed = parseFile(file, lookupMaps, mode);
            parsedFiles.add(parsed);
            if ("FAILED".equals(parsed.status)) {
                failedFileCount++;
                parsed.newRows = 0;
                insertFileRecord(jobId, parsed);
                fileItems.add(parsed.toItem());
                log.warn("Import job {} file failed: file={}, type={}, totalRows={}, reason={}",
                        jobId, parsed.fileName, parsed.dataType, parsed.totalRows, parsed.errorMessage);
                continue;
            }
            deduplicateAndCollect(parsed, allLongtermRows, allSpotRows, longtermDedupContext, spotDedupContext);
            recalculateParsedStatus(parsed);
            insertFileRecord(jobId, parsed);
            fileItems.add(parsed.toItem());
            log.info("Import job {} file normalized: file={}, type={}, status={}, totalRows={}, normalizedRows={}, duplicateRows={}, newRows={}, updatedRows={}, skippedRows={}, errorCount={}",
                    jobId, parsed.fileName, parsed.dataType, parsed.status, parsed.totalRows, parsed.normalizedRows,
                    parsed.duplicateRows, parsed.newRows, parsed.updatedRows, parsed.skippedRows, parsed.errorCount);
        }

        long stagingInsertStartedAt = System.currentTimeMillis();
        insertLongtermStagingRows(jobId, allLongtermRows);
        insertSpotStagingRows(jobId, allSpotRows);
        log.info("Import job {} staging insert completed in {} ms", jobId, System.currentTimeMillis() - stagingInsertStartedAt);

        String finalStatus = allLongtermRows.isEmpty() && allSpotRows.isEmpty() ? JOB_STATUS_FAILED : JOB_STATUS_NORMALIZED;
        String errorMessage = JOB_STATUS_FAILED.equals(finalStatus) ? buildJobFailureMessage(parsedFiles) : null;
        updateJobAfterNormalize(jobId, finalStatus, allLongtermRows.size(), allSpotRows.size(), failedFileCount, errorMessage);
        log.info("Import job {} completed: status={}, longtermRows={}, spotRows={}, failedFiles={}, error={}",
                jobId, finalStatus, allLongtermRows.size(), allSpotRows.size(), failedFileCount, errorMessage);
        log.info("Import job {} total elapsed={} ms", jobId, System.currentTimeMillis() - startedAt);

        ImportDataUploadResponse response = new ImportDataUploadResponse();
        response.setSuccess(JOB_STATUS_NORMALIZED.equals(finalStatus));
        response.setMessage(JOB_STATUS_NORMALIZED.equals(finalStatus) ? "Normalization completed" : "Normalization failed");
        response.setData(getJob(jobId));
        response.getData().setFiles(fileItems);
        return response;
    }

    private ImportDataUploadResponse uploadAndNormalizeAsyncInternal(MultipartFile[] files, String createdBy, UploadMode mode) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("files is required");
        }
        List<BufferedUploadFile> bufferedFiles = bufferUploadFiles(files);
        long jobId = createJob(files.length, createdBy);
        log.info("Import job {} queued async: mode={}, fileCount={}, createdBy={}", jobId, mode, files.length, createdBy);
        taskExecutor.execute(() -> processBufferedFiles(jobId, bufferedFiles, mode));

        ImportDataUploadResponse response = new ImportDataUploadResponse();
        response.setSuccess(true);
        response.setMessage("Import started");
        response.setData(getJob(jobId));
        return response;
    }

    private List<BufferedUploadFile> bufferUploadFiles(MultipartFile[] files) {
        List<BufferedUploadFile> bufferedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null) {
                bufferedFiles.add(new BufferedUploadFile(null, new byte[0]));
                continue;
            }
            try {
                bufferedFiles.add(new BufferedUploadFile(file.getOriginalFilename(), file.getBytes()));
            } catch (IOException ex) {
                throw new IllegalArgumentException("Failed to read upload file: " + file.getOriginalFilename(), ex);
            }
        }
        return bufferedFiles;
    }

    private void processBufferedFiles(long jobId, List<BufferedUploadFile> files, UploadMode mode) {
        long startedAt = System.currentTimeMillis();
        LookupMaps lookupMaps = loadLookupMaps();
        List<ParsedFile> parsedFiles = new ArrayList<>();
        List<NormalizedLongtermRow> allLongtermRows = new ArrayList<>();
        List<NormalizedSpotRow> allSpotRows = new ArrayList<>();
        LongtermDedupContext longtermDedupContext = mode == UploadMode.SPOT_ONLY ? new LongtermDedupContext() : loadExistingLongtermDedupContext();
        SpotDedupContext spotDedupContext = mode == UploadMode.LONGTERM_ONLY ? new SpotDedupContext() : loadExistingSpotDedupContext();
        int failedFileCount = 0;
        try {
            for (BufferedUploadFile file : files) {
                ParsedFile parsed = parseFile(file, lookupMaps, mode);
                parsedFiles.add(parsed);
                if ("FAILED".equals(parsed.status)) {
                    failedFileCount++;
                    parsed.newRows = 0;
                    insertFileRecord(jobId, parsed);
                    log.warn("Import job {} file failed: file={}, type={}, totalRows={}, reason={}",
                            jobId, parsed.fileName, parsed.dataType, parsed.totalRows, parsed.errorMessage);
                } else {
                    deduplicateAndCollect(parsed, allLongtermRows, allSpotRows, longtermDedupContext, spotDedupContext);
                    recalculateParsedStatus(parsed);
                    insertFileRecord(jobId, parsed);
                    log.info("Import job {} file normalized: file={}, type={}, status={}, totalRows={}, normalizedRows={}, duplicateRows={}, newRows={}, updatedRows={}, skippedRows={}, errorCount={}",
                            jobId, parsed.fileName, parsed.dataType, parsed.status, parsed.totalRows, parsed.normalizedRows,
                            parsed.duplicateRows, parsed.newRows, parsed.updatedRows, parsed.skippedRows, parsed.errorCount);
                }
                updateJobProgress(jobId, allLongtermRows.size(), allSpotRows.size(), failedFileCount);
            }

            long stagingInsertStartedAt = System.currentTimeMillis();
            insertLongtermStagingRows(jobId, allLongtermRows);
            insertSpotStagingRows(jobId, allSpotRows);
            log.info("Import job {} staging insert completed in {} ms", jobId, System.currentTimeMillis() - stagingInsertStartedAt);

            String finalStatus = allLongtermRows.isEmpty() && allSpotRows.isEmpty() ? JOB_STATUS_FAILED : JOB_STATUS_NORMALIZED;
            String errorMessage = JOB_STATUS_FAILED.equals(finalStatus) ? buildJobFailureMessage(parsedFiles) : null;
            updateJobAfterNormalize(jobId, finalStatus, allLongtermRows.size(), allSpotRows.size(), failedFileCount, errorMessage);
            log.info("Import job {} completed: status={}, longtermRows={}, spotRows={}, failedFiles={}, error={}",
                    jobId, finalStatus, allLongtermRows.size(), allSpotRows.size(), failedFileCount, errorMessage);
            log.info("Import job {} total elapsed={} ms", jobId, System.currentTimeMillis() - startedAt);
        } catch (Exception ex) {
            log.error("Import job {} async processing failed: {}", jobId, ex.getMessage(), ex);
            updateJobAfterNormalize(
                    jobId,
                    JOB_STATUS_FAILED,
                    allLongtermRows.size(),
                    allSpotRows.size(),
                    failedFileCount + 1,
                    cap("Normalization failed: " + ex.getMessage(), MAX_ERROR_MESSAGE_LEN)
            );
        }
    }

    public ImportDataJobResponse getJob(Long jobId) {
        ImportDataJobResponse job = jdbcTemplate.query("""
                SELECT
                    j.id, j.status, j.uploaded_file_count, j.longterm_row_count, j.spot_row_count, j.failed_file_count,
                    (
                        SELECT COUNT(1)
                        FROM import_job_files f
                        WHERE f.job_id = j.id
                    ) AS processed_file_count,
                    error_message, created_at, normalized_at, confirmed_at
                FROM import_jobs j
                WHERE j.id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            ImportDataJobResponse item = new ImportDataJobResponse();
            item.setJobId(rs.getLong("id"));
            item.setStatus(rs.getString("status"));
            item.setUploadedFileCount(rs.getInt("uploaded_file_count"));
            item.setProcessedFileCount(rs.getInt("processed_file_count"));
            item.setLongtermRowCount(rs.getInt("longterm_row_count"));
            item.setSpotRowCount(rs.getInt("spot_row_count"));
            item.setFailedFileCount(rs.getInt("failed_file_count"));
            item.setErrorMessage(rs.getString("error_message"));
            item.setCreatedAt(formatTs(rs.getTimestamp("created_at")));
            item.setNormalizedAt(formatTs(rs.getTimestamp("normalized_at")));
            item.setConfirmedAt(formatTs(rs.getTimestamp("confirmed_at")));
            return item;
        }, jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }

        List<ImportDataFileItem> files = jdbcTemplate.query("""
                SELECT
                    file_name, data_type, status, total_rows, normalized_rows, duplicate_rows, new_rows, updated_rows, skipped_rows, error_count, error_message
                FROM import_job_files
                WHERE job_id = ?
                ORDER BY id
                """, (rs, rowNum) -> {
            ImportDataFileItem item = new ImportDataFileItem();
            item.setFileName(rs.getString("file_name"));
            item.setDataType(rs.getString("data_type"));
            item.setStatus(rs.getString("status"));
            item.setTotalRows(rs.getInt("total_rows"));
            item.setNormalizedRows(rs.getInt("normalized_rows"));
            item.setDuplicateRows(rs.getInt("duplicate_rows"));
            item.setNewRows(rs.getInt("new_rows"));
            item.setUpdatedRows(rs.getInt("updated_rows"));
            item.setSkippedRows(rs.getInt("skipped_rows"));
            item.setErrorCount(rs.getInt("error_count"));
            item.setErrorMessage(rs.getString("error_message"));
            return item;
        }, jobId);
        job.setFiles(files);
        return job;
    }

    @Transactional
    public ImportDataActionResponse confirmJob(Long jobId, String adminPassword, String remark) {
        log.info("Confirm job requested: jobId={}, remark={}", jobId, remark);
        assertAdminPassword(adminPassword);
        ImportDataJobResponse job = getJob(jobId);
        if (!JOB_STATUS_NORMALIZED.equals(job.getStatus())) {
            throw new IllegalStateException("Job status must be NORMALIZED");
        }

        Long previousActiveVersionId = getActiveVersionId();
        if (previousActiveVersionId == null) {
            log.warn("No active version found. Creating baseline version before confirming job {}", jobId);
            previousActiveVersionId = createBaselineVersion(jobId);
        }
        long versionId = createVersion(jobId, job.getLongtermRowCount(), job.getSpotRowCount(), remark);
        String versionCode = getVersionCode(versionId);
        MasterStatus beforeStatus = getMasterStatus();
        saveRestorePoint(
                EVENT_BEFORE_CONFIRM,
                ACTION_CONFIRM,
                jobId,
                versionId,
                previousActiveVersionId,
                versionId,
                beforeStatus,
                "before confirm import version",
                getJobCreator(jobId)
        );

        jdbcTemplate.update("UPDATE import_versions SET status = ? WHERE status = ? AND id <> ?", VERSION_STATUS_INACTIVE, VERSION_STATUS_ACTIVE, versionId);
        snapshotLongtermForVersion(versionId, jobId, job.getLongtermRowCount());
        snapshotSpotForVersion(versionId, jobId, job.getSpotRowCount());
        int versionLongtermRows = countLongtermSnapshotRows(versionId);
        int versionSpotRows = countSpotSnapshotRows(versionId);
        updateVersionRowCounts(
                versionId,
                versionLongtermRows,
                versionSpotRows
        );

        replaceLiveDataByVersion(versionId);

        jdbcTemplate.update("""
                UPDATE import_versions
                SET status = ?, activated_at = NOW(), rolled_back_at = NULL
                WHERE id = ?
                """, VERSION_STATUS_ACTIVE, versionId);
        jdbcTemplate.update("""
                UPDATE import_jobs
                SET status = ?, confirmed_at = NOW()
                WHERE id = ?
                """, JOB_STATUS_CONFIRMED, jobId);
        MasterStatus afterStatus = getMasterStatus();
        saveRestorePoint(
                EVENT_AFTER_CONFIRM,
                ACTION_CONFIRM,
                jobId,
                versionId,
                previousActiveVersionId,
                versionId,
                afterStatus,
                "after confirm import version",
                getJobCreator(jobId)
        );

        ImportDataActionResponse response = new ImportDataActionResponse();
        response.setSuccess(true);
        response.setMessage("Import confirmed and activated");
        response.setJobId(jobId);
        response.setVersionId(versionId);
        response.setVersionCode(versionCode);
        log.info("Confirm job completed: jobId={}, versionId={}, versionCode={}, longtermRows={}, spotRows={}",
                jobId, versionId, versionCode, versionLongtermRows, versionSpotRows);
        return response;
    }

    @Transactional
    public ImportDataActionResponse rollbackToVersion(Long versionId, String adminPassword, String reason) {
        log.info("Rollback requested: targetVersionId={}, reason={}", versionId, reason);
        assertAdminPassword(adminPassword);
        return rollbackToVersionInternal(versionId, reason, "admin");
    }

    @Transactional
    public ImportDataActionResponse rollbackRecentVersions(Integer steps, String adminPassword, String reason) {
        log.info("Rollback recent requested: steps={}, reason={}", steps, reason);
        assertAdminPassword(adminPassword);
        if (steps == null || steps < 1) {
            throw new IllegalArgumentException("steps must be >= 1");
        }
        Long targetVersionId = jdbcTemplate.query("""
                SELECT id
                FROM import_versions
                WHERE activated_at IS NOT NULL
                ORDER BY activated_at DESC
                LIMIT ?, 1
                """, rs -> rs.next() ? rs.getLong("id") : null, steps);
        if (targetVersionId == null) {
            throw new IllegalArgumentException("Cannot rollback " + steps + " steps, history is insufficient");
        }
        return rollbackToVersionInternal(targetVersionId, reason, "admin");
    }

    public List<ImportDataVersionItem> listVersions() {
        return jdbcTemplate.query("""
                SELECT
                    id, version_code, source_job_id, status, longterm_row_count, spot_row_count,
                    created_at, activated_at, rolled_back_at, remark
                FROM import_versions
                ORDER BY id DESC
                LIMIT 50
                """, (rs, rowNum) -> {
            ImportDataVersionItem item = new ImportDataVersionItem();
            item.setId(rs.getLong("id"));
            item.setVersionCode(rs.getString("version_code"));
            item.setSourceJobId(rs.getLong("source_job_id"));
            item.setStatus(rs.getString("status"));
            item.setLongtermRowCount(rs.getInt("longterm_row_count"));
            item.setSpotRowCount(rs.getInt("spot_row_count"));
            item.setCreatedAt(formatTs(rs.getTimestamp("created_at")));
            item.setActivatedAt(formatTs(rs.getTimestamp("activated_at")));
            item.setRolledBackAt(formatTs(rs.getTimestamp("rolled_back_at")));
            item.setRemark(rs.getString("remark"));
            return item;
        });
    }

    public List<ImportDataRestorePointItem> listRestorePoints() {
        return jdbcTemplate.query("""
                SELECT
                    id, event_type, trigger_action, reference_job_id, reference_version_id,
                    from_version_id, to_version_id, binlog_file, binlog_position, gtid_set,
                    operator_name, note, created_at
                FROM import_restore_points
                ORDER BY id DESC
                LIMIT 100
                """, (rs, rowNum) -> {
            ImportDataRestorePointItem item = new ImportDataRestorePointItem();
            item.setId(rs.getLong("id"));
            item.setEventType(rs.getString("event_type"));
            item.setTriggerAction(rs.getString("trigger_action"));
            item.setReferenceJobId(rs.getObject("reference_job_id", Long.class));
            item.setReferenceVersionId(rs.getObject("reference_version_id", Long.class));
            item.setFromVersionId(rs.getObject("from_version_id", Long.class));
            item.setToVersionId(rs.getObject("to_version_id", Long.class));
            item.setBinlogFile(rs.getString("binlog_file"));
            item.setBinlogPosition(rs.getLong("binlog_position"));
            item.setGtidSet(rs.getString("gtid_set"));
            item.setOperator(rs.getString("operator_name"));
            item.setNote(rs.getString("note"));
            item.setCreatedAt(formatTs(rs.getTimestamp("created_at")));
            return item;
        });
    }

    private void replaceLiveDataByVersion(Long versionId) {
        log.info("Replacing live data by version {}", versionId);
        jdbcTemplate.update("DELETE FROM longterm_transactions");
        jdbcTemplate.update("DELETE FROM spot_transactions");

        jdbcTemplate.update("""
                INSERT INTO longterm_transactions (
                    transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                    outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                    contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                    market_participation_capacity, market_avg_price, chng_participation_capacity,
                    chng_transaction_amount, chng_avg_price, env_premium, data_source, note, import_version_id, created_at
                )
                SELECT
                    transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                    outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                    contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                    market_participation_capacity, market_avg_price, chng_participation_capacity,
                    chng_transaction_amount, chng_avg_price, env_premium, data_source, note, ?, NOW()
                FROM import_version_longterm_snapshot
                WHERE version_id = ?
                """, versionId, versionId);

        jdbcTemplate.update("""
                INSERT INTO spot_transactions (
                    company_id, date, gen_type_id, gen_amount, longterm_amount, longterm_price,
                    longterm_percent, spot_price, chng_spot_price, data_source, note, import_version_id, created_at
                )
                SELECT
                    company_id, date, gen_type_id, gen_amount, longterm_amount, longterm_price,
                    longterm_percent, spot_price, chng_spot_price, data_source, note, ?, NOW()
                FROM import_version_spot_snapshot
                WHERE version_id = ?
                """, versionId, versionId);
    }

    private void snapshotLongtermForVersion(Long versionId, Long jobId, int jobLongtermRows) {
        if (jobLongtermRows > 0) {
            jdbcTemplate.update("""
                    INSERT INTO import_version_longterm_snapshot (
                        version_id, transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                        outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                        contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                        market_participation_capacity, market_avg_price, chng_participation_capacity,
                        chng_transaction_amount, chng_avg_price, env_premium, data_source, note
                    )
                    SELECT
                        ?, transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                        outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                        contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                        market_participation_capacity, market_avg_price, chng_participation_capacity,
                        chng_transaction_amount, chng_avg_price, env_premium, data_source, note
                    FROM import_job_longterm_rows
                    WHERE job_id = ?
                    """, versionId, jobId);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO import_version_longterm_snapshot (
                    version_id, transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                    outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                    contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                    market_participation_capacity, market_avg_price, chng_participation_capacity,
                    chng_transaction_amount, chng_avg_price, env_premium, data_source, note
                )
                SELECT
                    ?, transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                    outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                    contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                    market_participation_capacity, market_avg_price, chng_participation_capacity,
                    chng_transaction_amount, chng_avg_price, env_premium, data_source, note
                FROM longterm_transactions
                """, versionId);
    }

    private void snapshotSpotForVersion(Long versionId, Long jobId, int jobSpotRows) {
        if (jobSpotRows > 0) {
            jdbcTemplate.update("""
                    INSERT INTO import_version_spot_snapshot (
                        version_id, company_id, date, gen_type_id, gen_amount, longterm_amount, longterm_price,
                        longterm_percent, spot_price, chng_spot_price, data_source, note
                    )
                    SELECT
                        ?, s.company_id, s.date, s.gen_type_id, s.gen_amount, s.longterm_amount, s.longterm_price,
                        s.longterm_percent, s.spot_price, s.chng_spot_price, s.data_source, s.note
                    FROM spot_transactions s
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM import_job_spot_rows j
                        WHERE j.job_id = ?
                          AND j.company_id = s.company_id
                          AND j.date = s.date
                          AND j.gen_type_id = s.gen_type_id
                    )
                    """, versionId, jobId);
            jdbcTemplate.update("""
                    INSERT INTO import_version_spot_snapshot (
                        version_id, company_id, date, gen_type_id, gen_amount, longterm_amount, longterm_price,
                        longterm_percent, spot_price, chng_spot_price, data_source, note
                    )
                    SELECT
                        ?, company_id, date, gen_type_id, gen_amount, longterm_amount, longterm_price,
                        longterm_percent, spot_price, chng_spot_price, data_source, note
                    FROM import_job_spot_rows
                    WHERE job_id = ?
                    """, versionId, jobId);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO import_version_spot_snapshot (
                    version_id, company_id, date, gen_type_id, gen_amount, longterm_amount, longterm_price,
                    longterm_percent, spot_price, chng_spot_price, data_source, note
                )
                SELECT
                    ?, company_id, date, gen_type_id, gen_amount, longterm_amount, longterm_price,
                    longterm_percent, spot_price, chng_spot_price, data_source, note
                FROM spot_transactions
                """, versionId);
    }

    private int countLongtermSnapshotRows(Long versionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM import_version_longterm_snapshot WHERE version_id = ?",
                Integer.class,
                versionId
        );
        return count == null ? 0 : count;
    }

    private int countSpotSnapshotRows(Long versionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM import_version_spot_snapshot WHERE version_id = ?",
                Integer.class,
                versionId
        );
        return count == null ? 0 : count;
    }

    private void updateVersionRowCounts(Long versionId, int longtermRows, int spotRows) {
        jdbcTemplate.update("""
                UPDATE import_versions
                SET longterm_row_count = ?, spot_row_count = ?
                WHERE id = ?
                """, longtermRows, spotRows, versionId);
    }

    private Long createBaselineVersion(Long jobId) {
        long baselineVersionId = createVersion(jobId, 0, 0, "AUTO-BASELINE before first confirm");
        snapshotLongtermForVersion(baselineVersionId, jobId, 0);
        snapshotSpotForVersion(baselineVersionId, jobId, 0);
        updateVersionRowCounts(
                baselineVersionId,
                countLongtermSnapshotRows(baselineVersionId),
                countSpotSnapshotRows(baselineVersionId)
        );
        jdbcTemplate.update("""
                UPDATE import_versions
                SET status = ?, activated_at = NOW(), rolled_back_at = NULL
                WHERE id = ?
                """, VERSION_STATUS_ACTIVE, baselineVersionId);
        log.info("Baseline version created: versionId={}, sourceJobId={}", baselineVersionId, jobId);
        return baselineVersionId;
    }

    private ImportDataActionResponse rollbackToVersionInternal(Long versionId, String reason, String operator) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM import_versions WHERE id = ?",
                Integer.class,
                versionId
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Version not found: " + versionId);
        }
        Long previousActiveVersionId = getActiveVersionId();
        MasterStatus beforeStatus = getMasterStatus();
        saveRestorePoint(
                EVENT_BEFORE_ROLLBACK,
                ACTION_ROLLBACK,
                null,
                versionId,
                previousActiveVersionId,
                versionId,
                beforeStatus,
                reason,
                operator
        );
        replaceLiveDataByVersion(versionId);
        jdbcTemplate.update("UPDATE import_versions SET status = ? WHERE status = ?", VERSION_STATUS_INACTIVE, VERSION_STATUS_ACTIVE);
        jdbcTemplate.update("""
                UPDATE import_versions
                SET status = ?, activated_at = NOW(), rolled_back_at = NOW(), remark = COALESCE(?, remark)
                WHERE id = ?
                """, VERSION_STATUS_ACTIVE, cap(reason, MAX_NOTE_LEN), versionId);
        MasterStatus afterStatus = getMasterStatus();
        saveRestorePoint(
                EVENT_AFTER_ROLLBACK,
                ACTION_ROLLBACK,
                null,
                versionId,
                previousActiveVersionId,
                versionId,
                afterStatus,
                reason,
                operator
        );

        ImportDataActionResponse response = new ImportDataActionResponse();
        response.setSuccess(true);
        response.setMessage("Rollback completed");
        response.setVersionId(versionId);
        response.setVersionCode(getVersionCode(versionId));
        log.info("Rollback completed: targetVersionId={}, previousActiveVersionId={}", versionId, previousActiveVersionId);
        return response;
    }

    private void assertAdminPassword(String adminPassword) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new SecurityException("Admin password is required");
        }
        if (!ADMIN_PASSWORD.equals(adminPassword)) {
            throw new SecurityException("Invalid admin password");
        }
    }

    private long createJob(int fileCount, String createdBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO import_jobs (status, uploaded_file_count, created_by)
                    VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, JOB_STATUS_PROCESSING);
            ps.setInt(2, fileCount);
            ps.setString(3, createdBy);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to create import job");
        }
        return key.longValue();
    }

    private long createVersion(long jobId, int longtermRows, int spotRows, String remark) {
        String versionCode = "IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO import_versions (
                        version_code, source_job_id, status, longterm_row_count, spot_row_count, remark
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, versionCode);
            ps.setLong(2, jobId);
            ps.setString(3, VERSION_STATUS_ACTIVE);
            ps.setInt(4, longtermRows);
            ps.setInt(5, spotRows);
            ps.setString(6, cap(remark, MAX_NOTE_LEN));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to create version");
        }
        return key.longValue();
    }

    private String getVersionCode(Long versionId) {
        return jdbcTemplate.queryForObject("SELECT version_code FROM import_versions WHERE id = ?", String.class, versionId);
    }

    private Long getActiveVersionId() {
        return jdbcTemplate.query("""
                SELECT id
                FROM import_versions
                WHERE status = ?
                ORDER BY activated_at DESC, id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong("id") : null, VERSION_STATUS_ACTIVE);
    }

    private String getJobCreator(Long jobId) {
        return jdbcTemplate.query("""
                SELECT created_by
                FROM import_jobs
                WHERE id = ?
                """, rs -> rs.next() ? rs.getString("created_by") : null, jobId);
    }

    private MasterStatus getMasterStatus() {
        try {
            MasterStatus status = jdbcTemplate.query("SHOW MASTER STATUS", rs -> {
                if (!rs.next()) {
                    return null;
                }
                String file = rs.getString("File");
                Long position = rs.getLong("Position");
                String gtidSet = null;
                try {
                    gtidSet = rs.getString("Executed_Gtid_Set");
                } catch (Exception ignored) {
                    // Executed_Gtid_Set is not present when GTID is disabled.
                }
                return new MasterStatus(file, position, gtidSet);
            });
            if (status == null || status.binlogFile() == null || status.binlogFile().isBlank()) {
                log.debug("SHOW MASTER STATUS returned empty; restore-point binlog metadata will be skipped");
                return null;
            }
            return status;
        } catch (Exception ignored) {
            // Some environments do not expose SHOW MASTER STATUS permission.
            log.debug("SHOW MASTER STATUS unavailable; restore-point binlog metadata will be skipped");
            return null;
        }
    }

    private void saveRestorePoint(String eventType,
                                  String triggerAction,
                                  Long referenceJobId,
                                  Long referenceVersionId,
                                  Long fromVersionId,
                                  Long toVersionId,
                                  MasterStatus masterStatus,
                                  String note,
                                  String operator) {
        if (masterStatus == null) {
            log.debug("Skip restore point {} for action {} due to missing master status", eventType, triggerAction);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO import_restore_points (
                    event_type, trigger_action, reference_job_id, reference_version_id,
                    from_version_id, to_version_id, binlog_file, binlog_position, gtid_set,
                    operator_name, note
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventType,
                triggerAction,
                referenceJobId,
                referenceVersionId,
                fromVersionId,
                toVersionId,
                masterStatus.binlogFile(),
                masterStatus.binlogPosition(),
                masterStatus.gtidSet(),
                cap(operator, MAX_OPERATOR_LEN),
                cap(note, MAX_NOTE_LEN)
        );
    }

    private void updateJobAfterNormalize(long jobId,
                                         String status,
                                         int longtermRows,
                                         int spotRows,
                                         int failedFileCount,
                                         String errorMessage) {
        jdbcTemplate.update("""
                UPDATE import_jobs
                SET status = ?,
                    longterm_row_count = ?,
                    spot_row_count = ?,
                    failed_file_count = ?,
                    error_message = ?,
                    normalized_at = NOW()
                WHERE id = ?
                """, status, longtermRows, spotRows, failedFileCount, cap(errorMessage, MAX_ERROR_MESSAGE_LEN), jobId);
    }

    private void updateJobProgress(long jobId, int longtermRows, int spotRows, int failedFileCount) {
        jdbcTemplate.update("""
                UPDATE import_jobs
                SET status = ?,
                    longterm_row_count = ?,
                    spot_row_count = ?,
                    failed_file_count = ?
                WHERE id = ?
                """, JOB_STATUS_PROCESSING, longtermRows, spotRows, failedFileCount, jobId);
    }

    private void insertFileRecord(long jobId, ParsedFile file) {
        jdbcTemplate.update("""
                INSERT INTO import_job_files (
                    job_id, file_name, data_type, status, total_rows, normalized_rows, duplicate_rows, new_rows, updated_rows, skipped_rows, error_count, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, jobId, file.fileName, file.dataType, file.status, file.totalRows, file.normalizedRows, file.duplicateRows, file.newRows, file.updatedRows, file.skippedRows, file.errorCount, cap(file.errorMessage, MAX_ERROR_MESSAGE_LEN));
    }

    private void insertLongtermStagingRows(long jobId, List<NormalizedLongtermRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO import_job_longterm_rows (
                            job_id, file_name, transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                            outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                            contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                            market_participation_capacity, market_avg_price, chng_participation_capacity,
                            chng_transaction_amount, chng_avg_price, env_premium, data_source, note
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                rows,
                500,
                (ps, row) -> {
                    ps.setLong(1, jobId);
                    ps.setString(2, row.fileName);
                    ps.setObject(3, row.transactionId);
                    ps.setObject(4, row.companyId);
                    ps.setString(5, row.place);
                    ps.setDate(6, toDate(row.transactionDate));
                    ps.setString(7, row.transactionName);
                    ps.setObject(8, row.transactionTypeId);
                    ps.setString(9, row.outsendProvince);
                    ps.setObject(10, row.genTypeId);
                    ps.setObject(11, row.transactionPeriodId);
                    ps.setObject(12, row.transactionStartYear);
                    ps.setObject(13, row.transactionEndYear);
                    ps.setDate(14, toDate(row.contractStartDate));
                    ps.setDate(15, toDate(row.contractEndDate));
                    ps.setObject(16, row.isGreen);
                    ps.setObject(17, row.isCheap);
                    ps.setBigDecimal(18, row.basePrice);
                    ps.setBigDecimal(19, row.marketSize);
                    ps.setBigDecimal(20, row.marketParticipationCapacity);
                    ps.setBigDecimal(21, row.marketAvgPrice);
                    ps.setBigDecimal(22, row.chngParticipationCapacity);
                    ps.setBigDecimal(23, row.chngTransactionAmount);
                    ps.setBigDecimal(24, row.chngAvgPrice);
                    ps.setBigDecimal(25, row.envPremium);
                    ps.setString(26, row.dataSource);
                    ps.setString(27, row.note);
                }
        );
    }

    private void insertSpotStagingRows(long jobId, List<NormalizedSpotRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO import_job_spot_rows (
                            job_id, file_name, company_id, date, gen_type_id, gen_amount, longterm_amount,
                            longterm_price, longterm_percent, spot_price, chng_spot_price, data_source, note
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                rows,
                500,
                (ps, row) -> {
                    ps.setLong(1, jobId);
                    ps.setString(2, row.fileName);
                    ps.setObject(3, row.companyId);
                    ps.setDate(4, toDate(row.date));
                    ps.setObject(5, row.genTypeId);
                    ps.setBigDecimal(6, row.genAmount);
                    ps.setBigDecimal(7, row.longtermAmount);
                    ps.setBigDecimal(8, row.longtermPrice);
                    ps.setBigDecimal(9, row.longtermPercent);
                    ps.setBigDecimal(10, row.spotPrice);
                    ps.setBigDecimal(11, row.chngSpotPrice);
                    ps.setString(12, row.dataSource);
                    ps.setString(13, row.note);
                }
        );
    }

    private void deduplicateAndCollect(ParsedFile parsed,
                                       List<NormalizedLongtermRow> allLongtermRows,
                                       List<NormalizedSpotRow> allSpotRows,
                                       LongtermDedupContext longtermDedupContext,
                                       SpotDedupContext spotDedupContext) {
        if (parsed.longtermRows != null) {
            for (NormalizedLongtermRow row : parsed.longtermRows) {
                String longtermKey = buildLongtermKey(row);
                String longtermFingerprint = buildLongtermValueFingerprint(row);
                String existingLongtermFingerprint = longtermDedupContext.baselineByKey.get(longtermKey);
                if (existingLongtermFingerprint != null && existingLongtermFingerprint.equals(longtermFingerprint)) {
                    parsed.duplicateRows++;
                    parsed.skippedRows++;
                    parsed.addReason("Duplicate row");
                    continue;
                }
                if (!longtermDedupContext.currentUploadKeys.add(longtermKey)) {
                    parsed.duplicateRows++;
                    parsed.skippedRows++;
                    parsed.addReason("Duplicate row");
                    continue;
                }
                longtermDedupContext.baselineByKey.put(longtermKey, longtermFingerprint);
                if (existingLongtermFingerprint == null) {
                    parsed.newRows++;
                } else {
                    parsed.updatedRows++;
                }
                allLongtermRows.add(row);
            }
        }
        if (parsed.spotRows != null) {
            for (NormalizedSpotRow row : parsed.spotRows) {
                String spotKey = buildSpotKey(row);
                String valueFingerprint = buildSpotValueFingerprint(row);

                String existingFingerprint = spotDedupContext.baselineByKey.get(spotKey);
                if (existingFingerprint != null && existingFingerprint.equals(valueFingerprint)) {
                    parsed.duplicateRows++;
                    parsed.skippedRows++;
                    parsed.addReason("Duplicate row");
                    continue;
                }

                if (!spotDedupContext.currentUploadKeys.add(spotKey)) {
                    parsed.duplicateRows++;
                    parsed.skippedRows++;
                    parsed.addReason("Duplicate row");
                    continue;
                }

                spotDedupContext.baselineByKey.put(spotKey, valueFingerprint);
                if (existingFingerprint == null) {
                    parsed.newRows++;
                } else {
                    parsed.updatedRows++;
                }
                allSpotRows.add(row);
            }
        }
        log.debug("File dedup result: file={}, type={}, normalizedRows={}, duplicateRows={}, newRows={}, updatedRows={}",
                parsed.fileName, parsed.dataType, parsed.normalizedRows, parsed.duplicateRows, parsed.newRows, parsed.updatedRows);
    }

    private LongtermDedupContext loadExistingLongtermDedupContext() {
        LongtermDedupContext context = new LongtermDedupContext();
        jdbcTemplate.query("""
                SELECT
                    transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                    outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                    contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                    market_participation_capacity, market_avg_price, chng_participation_capacity,
                    chng_transaction_amount, chng_avg_price, env_premium, note
                FROM longterm_transactions
                """, rs -> {
            NormalizedLongtermRow row = new NormalizedLongtermRow();
            row.transactionId = rs.getObject("transaction_id", Integer.class);
            row.companyId = rs.getObject("company_id", Long.class);
            row.place = rs.getString("place");
            Date transactionDate = rs.getDate("transaction_date");
            row.transactionDate = transactionDate == null ? null : transactionDate.toLocalDate();
            row.transactionName = rs.getString("transaction_name");
            row.transactionTypeId = rs.getObject("transaction_type_id", Integer.class);
            row.outsendProvince = rs.getString("outsend_province");
            row.genTypeId = rs.getObject("gen_type_id", Integer.class);
            row.transactionPeriodId = rs.getObject("transaction_period_id", Integer.class);
            row.transactionStartYear = rs.getObject("transaction_start_year", Integer.class);
            row.transactionEndYear = rs.getObject("transaction_end_year", Integer.class);
            Date contractStartDate = rs.getDate("contract_start_date");
            row.contractStartDate = contractStartDate == null ? null : contractStartDate.toLocalDate();
            Date contractEndDate = rs.getDate("contract_end_date");
            row.contractEndDate = contractEndDate == null ? null : contractEndDate.toLocalDate();
            row.isGreen = rs.getObject("is_green", Boolean.class);
            row.isCheap = rs.getObject("is_cheap", Boolean.class);
            row.basePrice = rs.getBigDecimal("base_price");
            row.marketSize = rs.getBigDecimal("market_size");
            row.marketParticipationCapacity = rs.getBigDecimal("market_participation_capacity");
            row.marketAvgPrice = rs.getBigDecimal("market_avg_price");
            row.chngParticipationCapacity = rs.getBigDecimal("chng_participation_capacity");
            row.chngTransactionAmount = rs.getBigDecimal("chng_transaction_amount");
            row.chngAvgPrice = rs.getBigDecimal("chng_avg_price");
            row.envPremium = rs.getBigDecimal("env_premium");
            row.note = rs.getString("note");
            context.baselineByKey.put(buildLongtermKey(row), buildLongtermValueFingerprint(row));
        });
        log.info("Loaded longterm baseline keys: {}", context.baselineByKey.size());
        return context;
    }

    private SpotDedupContext loadExistingSpotDedupContext() {
        SpotDedupContext context = new SpotDedupContext();
        jdbcTemplate.query("""
                SELECT
                    company_id, date, gen_type_id, gen_amount, longterm_amount, longterm_price,
                    longterm_percent, spot_price, chng_spot_price, note
                FROM spot_transactions
                """, rs -> {
            NormalizedSpotRow row = new NormalizedSpotRow();
            row.companyId = rs.getObject("company_id", Long.class);
            Date date = rs.getDate("date");
            row.date = date == null ? null : date.toLocalDate();
            row.genTypeId = rs.getObject("gen_type_id", Integer.class);
            row.genAmount = rs.getBigDecimal("gen_amount");
            row.longtermAmount = rs.getBigDecimal("longterm_amount");
            row.longtermPrice = rs.getBigDecimal("longterm_price");
            row.longtermPercent = rs.getBigDecimal("longterm_percent");
            row.spotPrice = rs.getBigDecimal("spot_price");
            row.chngSpotPrice = rs.getBigDecimal("chng_spot_price");
            row.note = rs.getString("note");
            context.baselineByKey.put(buildSpotKey(row), buildSpotValueFingerprint(row));
        });
        log.info("Loaded spot baseline keys: {}", context.baselineByKey.size());
        return context;
    }

    private void recalculateParsedStatus(ParsedFile parsed) {
        int effectiveRows = parsed.newRows + parsed.updatedRows;
        if (effectiveRows == 0) {
            parsed.status = "FAILED";
            parsed.errorMessage = buildReasonMessage(parsed, true);
        } else if (parsed.errorCount > 0 || parsed.skippedRows > 0) {
            parsed.status = "PARTIAL";
            parsed.errorMessage = buildReasonMessage(parsed, false);
        } else {
            parsed.status = "SUCCESS";
            parsed.errorMessage = null;
        }
    }

    private ParsedFile parseFile(MultipartFile file, LookupMaps lookupMaps, UploadMode mode) {
        ParsedFile parsed = new ParsedFile();
        parsed.fileName = file == null ? null : file.getOriginalFilename();
        parsed.dataType = "UNKNOWN";
        parsed.status = "FAILED";
        parsed.longtermRows = new ArrayList<>();
        parsed.spotRows = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            parsed.errorMessage = "Empty file";
            parsed.errorCount = 1;
            log.warn("Skip empty file during import");
            return parsed;
        }

        String lower = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
            parsed.errorMessage = "Only .xlsx/.xls is supported";
            parsed.errorCount = 1;
            log.warn("Unsupported file extension: file={}", parsed.fileName);
            return parsed;
        }
        log.info("Parsing file: file={}, mode={}", parsed.fileName, mode);

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            ParsedSheet parsedSheet = readSheet(sheet, evaluator);

            parsed.totalRows = parsedSheet.rows.size();
            if (parsedSheet.type == FileType.LONGTERM) {
                parsed.dataType = "LONGTERM";
                if (mode == UploadMode.SPOT_ONLY) {
                    parsed.errorCount = 1;
                    parsed.errorMessage = "This endpoint only accepts spot files";
                    return parsed;
                }
                parseLongtermRows(parsed, parsedSheet.rows, lookupMaps, file.getOriginalFilename());
            } else if (parsedSheet.type == FileType.SPOT) {
                parsed.dataType = "SPOT";
                if (mode == UploadMode.LONGTERM_ONLY) {
                    parsed.errorCount = 1;
                    parsed.errorMessage = "This endpoint only accepts longterm files";
                    return parsed;
                }
                parseSpotRows(parsed, parsedSheet.rows, lookupMaps, file.getOriginalFilename());
            } else {
                parsed.errorCount = 1;
                parsed.errorMessage = "Cannot detect file type";
                return parsed;
            }

            if (parsed.normalizedRows == 0) {
                parsed.status = "FAILED";
                if (parsed.errorMessage == null) {
                    parsed.errorMessage = buildReasonMessage(parsed, true);
                }
            } else if (parsed.errorCount > 0 || parsed.skippedRows > 0) {
                parsed.status = "PARTIAL";
                if (parsed.errorMessage == null) {
                    parsed.errorMessage = buildReasonMessage(parsed, false);
                }
            } else {
                parsed.status = "SUCCESS";
            }
            log.info("Parse finished: file={}, detectedType={}, status={}, totalRows={}, normalizedRows={}, skippedRows={}, errorCount={}",
                    parsed.fileName, parsed.dataType, parsed.status, parsed.totalRows, parsed.normalizedRows, parsed.skippedRows, parsed.errorCount);
            return parsed;
        } catch (Exception ex) {
            parsed.errorCount = 1;
            parsed.errorMessage = "Parse failed: " + ex.getMessage();
            log.error("Parse exception: file={}, message={}", parsed.fileName, ex.getMessage(), ex);
            return parsed;
        }
    }

    private ParsedFile parseFile(BufferedUploadFile file, LookupMaps lookupMaps, UploadMode mode) {
        ParsedFile parsed = new ParsedFile();
        parsed.fileName = file == null ? null : file.fileName;
        parsed.dataType = "UNKNOWN";
        parsed.status = "FAILED";
        parsed.longtermRows = new ArrayList<>();
        parsed.spotRows = new ArrayList<>();

        if (file == null || file.bytes == null || file.bytes.length == 0) {
            parsed.errorMessage = "Empty file";
            parsed.errorCount = 1;
            log.warn("Skip empty file during import");
            return parsed;
        }

        String lower = Optional.ofNullable(file.fileName).orElse("").toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
            parsed.errorMessage = "Only .xlsx/.xls is supported";
            parsed.errorCount = 1;
            log.warn("Unsupported file extension: file={}", parsed.fileName);
            return parsed;
        }
        log.info("Parsing file: file={}, mode={}", parsed.fileName, mode);

        try (InputStream is = new ByteArrayInputStream(file.bytes); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            ParsedSheet parsedSheet = readSheet(sheet, evaluator);

            parsed.totalRows = parsedSheet.rows.size();
            if (parsedSheet.type == FileType.LONGTERM) {
                parsed.dataType = "LONGTERM";
                if (mode == UploadMode.SPOT_ONLY) {
                    parsed.errorCount = 1;
                    parsed.errorMessage = "This endpoint only accepts spot files";
                    return parsed;
                }
                parseLongtermRows(parsed, parsedSheet.rows, lookupMaps, file.fileName);
            } else if (parsedSheet.type == FileType.SPOT) {
                parsed.dataType = "SPOT";
                if (mode == UploadMode.LONGTERM_ONLY) {
                    parsed.errorCount = 1;
                    parsed.errorMessage = "This endpoint only accepts longterm files";
                    return parsed;
                }
                parseSpotRows(parsed, parsedSheet.rows, lookupMaps, file.fileName);
            } else {
                parsed.errorCount = 1;
                parsed.errorMessage = "Cannot detect file type";
                return parsed;
            }

            if (parsed.normalizedRows == 0) {
                parsed.status = "FAILED";
                if (parsed.errorMessage == null) {
                    parsed.errorMessage = buildReasonMessage(parsed, true);
                }
            } else if (parsed.errorCount > 0 || parsed.skippedRows > 0) {
                parsed.status = "PARTIAL";
                if (parsed.errorMessage == null) {
                    parsed.errorMessage = buildReasonMessage(parsed, false);
                }
            } else {
                parsed.status = "SUCCESS";
            }
            log.info("Parse finished: file={}, detectedType={}, status={}, totalRows={}, normalizedRows={}, skippedRows={}, errorCount={}",
                    parsed.fileName, parsed.dataType, parsed.status, parsed.totalRows, parsed.normalizedRows, parsed.skippedRows, parsed.errorCount);
            return parsed;
        } catch (Exception ex) {
            parsed.errorCount = 1;
            parsed.errorMessage = "Parse failed: " + ex.getMessage();
            log.error("Parse exception: file={}, message={}", parsed.fileName, ex.getMessage(), ex);
            return parsed;
        }
    }

    private void parseLongtermRows(ParsedFile parsed,
                                   List<Map<String, String>> rows,
                                   LookupMaps maps,
                                   String fileName) {
        int defaultYear = detectYearFromFileName(fileName);
        for (Map<String, String> row : rows) {
            String companyName = getFirst(row, "所属公司", "公司");
            if (blank(companyName) || "所属公司".equals(clean(companyName))) {
                parsed.skippedRows++;
                parsed.addReason("Header/blank company row");
                continue;
            }
            Long companyId = maps.companyNameToId.get(normalizeKey(companyName));
            if (companyId == null) {
                parsed.skippedRows++;
                parsed.errorCount++;
                parsed.addReason("Unknown company: " + companyName);
                continue;
            }

            String genTypeRaw = getFirst(row, "发电类型");
            Integer genTypeId = resolveGenTypeId(genTypeRaw, maps.genTypeNameToId);
            if (genTypeId == null) {
                parsed.skippedRows++;
                parsed.errorCount++;
                parsed.addReason("Unknown gen type: " + clean(String.valueOf(genTypeRaw)));
                continue;
            }

            Integer typeId = resolveTransactionTypeId(getFirst(row, "交易类型"), maps.transactionTypeNameToId);
            Integer periodId = resolveTransactionPeriodId(getFirst(row, "交易周期"), maps.transactionPeriodNameToId);
            PeriodParseResult period = parseContractPeriod(getFirst(row, "合同执行周期"), defaultYear);
            LocalDate transactionDate = parseTransactionDate(getFirst(row, "交易日期"), defaultYear);

            NormalizedLongtermRow normalized = new NormalizedLongtermRow();
            normalized.fileName = fileName;
            normalized.transactionId = parseInteger(getFirst(row, "序号"));
            normalized.companyId = companyId;
            normalized.place = getFirst(row, "区域", "交易区域");
            normalized.transactionDate = transactionDate;
            normalized.transactionName = getFirst(row, "交易名称");
            normalized.transactionTypeId = typeId;
            normalized.outsendProvince = getFirst(row, "受端省份");
            normalized.genTypeId = genTypeId;
            normalized.transactionPeriodId = periodId;
            normalized.transactionStartYear = period.year;
            normalized.transactionEndYear = period.year;
            normalized.contractStartDate = period.startDate;
            normalized.contractEndDate = period.endDate;
            normalized.isGreen = parseBooleanYN(getFirst(row, "是否为绿电交易"));
            normalized.isCheap = parseBooleanYN(getFirst(row, "是否为平价项目"));
            normalized.basePrice = parseDecimal(getFirst(row,
                    "基准价（不含超净）元/千千瓦时", "基准价", "基准价(元/千千瓦时)", "基准价（元/千千瓦时）"));
            normalized.marketSize = parseDecimal(getFirst(row, "市场规模/亿千瓦时", "市场规模"));
            normalized.marketParticipationCapacity = parseDecimal(getFirst(row, "市场参与装机容量/万千瓦", "市场参与装机容量"));
            normalized.marketAvgPrice = parseDecimal(getFirst(row,
                    "市场交易均价", "市场交易均价（元/千千瓦时）", "市场交易均价(元/千千瓦时)", "市场交易均价/元/千千瓦时"));
            normalized.chngParticipationCapacity = parseDecimal(getFirst(row, "华能参与装机/万千瓦", "华能参与装机"));
            normalized.chngTransactionAmount = parseDecimal(getFirst(row, "华能成交电量/亿千瓦时", "华能成交电量"));
            normalized.chngAvgPrice = parseDecimal(getFirst(row,
                    "华能成交价格 元/千千瓦时", "华能成交价格", "华能成交价格（元/千千瓦时）", "华能成交价格(元/千千瓦时)", "华能成交价格元/千千瓦时"));
            normalized.envPremium = parseDecimal(getFirst(row, "环境溢价元/千千瓦时", "环境溢价"));
            normalized.dataSource = fileName;
            normalized.note = getFirst(row, "备注");

            parsed.longtermRows.add(normalized);
            parsed.normalizedRows++;
        }
    }

    private void parseSpotRows(ParsedFile parsed,
                               List<Map<String, String>> rows,
                               LookupMaps maps,
                               String fileName) {
        Integer fileLevelGenTypeId = detectSpotGenTypeFromFileName(fileName, maps.genTypeNameToId);
        for (Map<String, String> row : rows) {
            String companyName = getFirst(row, "公司", "所属公司");
            if (isInstructionText(companyName)) {
                continue;
            }
            if (blank(companyName)) {
                if (isFootnoteRow(row)) {
                    continue;
                }
                parsed.skippedRows++;
                parsed.addReason("Blank company row");
                continue;
            }
            Long companyId = maps.companyNameToId.get(normalizeKey(companyName));
            if (companyId == null) {
                parsed.skippedRows++;
                parsed.errorCount++;
                parsed.addReason("Unknown company: " + companyName);
                continue;
            }

            LocalDate date = parseDate(getFirst(row, "日期"), null);
            if (date == null) {
                parsed.skippedRows++;
                parsed.errorCount++;
                parsed.addReason("Invalid date: " + clean(String.valueOf(getFirst(row, "日期"))));
                continue;
            }

            Integer genTypeId = resolveGenTypeId(getFirst(row, "发电类型", "类型"), maps.genTypeNameToId);
            if (genTypeId == null) {
                genTypeId = fileLevelGenTypeId;
            }
            if (genTypeId == null) {
                parsed.skippedRows++;
                parsed.errorCount++;
                parsed.addReason("Unknown gen type in spot row");
                continue;
            }

            NormalizedSpotRow normalized = new NormalizedSpotRow();
            normalized.fileName = fileName;
            normalized.companyId = companyId;
            normalized.date = date;
            normalized.genTypeId = genTypeId;
            normalized.genAmount = parseDecimal(getFirst(row, "上网电量"));
            normalized.longtermAmount = parseDecimal(getFirst(row, "中长期合约电量"));
            normalized.longtermPrice = parseDecimal(getFirst(row, "中长期合约电价"));
            normalized.longtermPercent = parsePercent(getFirst(row, "中长期持仓率"));
            normalized.spotPrice = parseDecimal(getFirst(row, "统一结算点实时均价"));
            normalized.chngSpotPrice = parseDecimal(getFirst(row, "日清分电价"));
            normalized.dataSource = fileName;
            normalized.note = getFirst(row, "备注");

            parsed.spotRows.add(normalized);
            parsed.normalizedRows++;
        }
    }

    private boolean isFootnoteRow(Map<String, String> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        List<String> values = row.values().stream()
                .map(this::clean)
                .filter(v -> !v.isEmpty())
                .toList();
        if (values.size() != 1) {
            return false;
        }
        String value = values.get(0);
        return value.startsWith("备注")
                || value.startsWith("注：")
                || value.startsWith("注:")
                || value.startsWith("说明");
    }

    private boolean isInstructionText(String text) {
        if (blank(text)) {
            return false;
        }
        String value = clean(text);
        return value.contains("填写说明")
                || value.contains("单位统一用")
                || value.contains("只需统计")
                || value.contains("请根据")
                || value.startsWith("说明")
                || value.startsWith("注：")
                || value.startsWith("注:");
    }

    private ParsedSheet readSheet(Sheet sheet, FormulaEvaluator evaluator) {
        int headerRowIndex = -1;
        FileType type = FileType.UNKNOWN;
        int maxProbe = Math.min(sheet.getLastRowNum(), 20);
        for (int i = 0; i <= maxProbe; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Set<String> headerSet = new HashSet<>();
            short minCell = row.getFirstCellNum();
            short maxCell = row.getLastCellNum();
            if (minCell < 0 || maxCell < 0) {
                continue;
            }
            for (int c = minCell; c < maxCell; c++) {
                String value = clean(cellValue(row.getCell(c), evaluator));
                if (!value.isEmpty()) {
                    headerSet.add(value);
                }
            }
            if (headerSet.contains("序号") && (headerSet.contains("所属公司") || headerSet.contains("交易类型"))) {
                headerRowIndex = i;
                type = FileType.LONGTERM;
                break;
            }
            if (headerSet.contains("日期") && (headerSet.contains("公司") || headerSet.contains("所属公司"))) {
                headerRowIndex = i;
                type = FileType.SPOT;
                break;
            }
        }
        if (headerRowIndex < 0) {
            return new ParsedSheet(FileType.UNKNOWN, List.of());
        }

        Row headerRow = sheet.getRow(headerRowIndex);
        List<String> headers = buildHeaders(headerRow, evaluator);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            LinkedHashMap<String, String> line = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int c = 0; c < headers.size(); c++) {
                String key = headers.get(c);
                if (key == null || key.isBlank()) {
                    continue;
                }
                String value = clean(cellValue(row.getCell(c), evaluator));
                if (!value.isEmpty()) {
                    hasValue = true;
                }
                line.put(key, value);
            }
            if (hasValue) {
                rows.add(line);
            }
        }
        return new ParsedSheet(type, rows);
    }

    private List<String> buildHeaders(Row row, FormulaEvaluator evaluator) {
        List<String> headers = new ArrayList<>();
        if (row == null) {
            return headers;
        }
        Map<String, Integer> seen = new HashMap<>();
        int max = row.getLastCellNum();
        for (int i = 0; i < max; i++) {
            String raw = clean(cellValue(row.getCell(i), evaluator));
            String key = raw;
            int count = seen.getOrDefault(raw, 0);
            if (!raw.isEmpty() && count > 0) {
                key = raw + "_" + count;
            }
            if (!raw.isEmpty()) {
                seen.put(raw, count + 1);
            }
            headers.add(key);
        }
        return headers;
    }

    private String cellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        try {
            return dataFormatter.formatCellValue(cell, evaluator);
        } catch (RuntimeException ex) {
            String cellAddress = cell.getAddress() == null ? "unknown" : cell.getAddress().formatAsString();
            log.debug("Formula evaluate failed at {}, fallback to cached/display value: {}", cellAddress, ex.getMessage());
            try {
                return dataFormatter.formatCellValue(cell);
            } catch (RuntimeException ignored) {
                return "";
            }
        }
    }

    private LookupMaps loadLookupMaps() {
        LookupMaps maps = new LookupMaps();
        maps.companyNameToId = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM companies", rs -> {
            maps.companyNameToId.put(normalizeKey(rs.getString("name")), rs.getLong("id"));
        });
        maps.genTypeNameToId = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM gen_types", rs -> {
            maps.genTypeNameToId.put(normalizeKey(rs.getString("name")), rs.getInt("id"));
        });
        maps.transactionTypeNameToId = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM transaction_types", rs -> {
            maps.transactionTypeNameToId.put(normalizeKey(rs.getString("name")), rs.getInt("id"));
        });
        maps.transactionPeriodNameToId = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM transaction_periods", rs -> {
            maps.transactionPeriodNameToId.put(normalizeKey(rs.getString("name")), rs.getInt("id"));
        });
        return maps;
    }

    private Integer resolveGenTypeId(String raw, Map<String, Integer> genTypeNameToId) {
        if (blank(raw)) {
            return null;
        }
        String key = normalizeKey(raw);
        if ("煤机".equals(key)) {
            key = normalizeKey(GenTypeEnum.COAL.getName());
        }
        return genTypeNameToId.get(key);
    }

    private Integer resolveTransactionTypeId(String raw, Map<String, Integer> transactionTypeNameToId) {
        if (blank(raw)) {
            return null;
        }
        String key = normalizeKey(raw);
        Integer id = transactionTypeNameToId.get(key);
        if (id != null) {
            return id;
        }
        for (TransactionTypeEnum transactionType : TransactionTypeEnum.values()) {
            if (normalizeKey(transactionType.getName()).equals(key)) {
                return transactionType.getId();
            }
        }
        return null;
    }

    private Integer resolveTransactionPeriodId(String raw, Map<String, Integer> transactionPeriodNameToId) {
        if (blank(raw)) {
            return null;
        }
        String key = normalizeKey(raw);
        Integer id = transactionPeriodNameToId.get(key);
        if (id != null) {
            return id;
        }
        for (TransactionPeriodEnum transactionPeriod : TransactionPeriodEnum.values()) {
            if (normalizeKey(transactionPeriod.getName()).equals(key)) {
                return transactionPeriod.getId();
            }
        }
        return null;
    }

    private Integer detectSpotGenTypeFromFileName(String fileName, Map<String, Integer> genTypeNameToId) {
        if (blank(fileName)) {
            return null;
        }
        String f = fileName.toLowerCase(Locale.ROOT);
        if (f.contains("煤")) {
            return genTypeNameToId.get(normalizeKey(GenTypeEnum.COAL.getName()));
        }
        if (f.contains("风")) {
            return genTypeNameToId.get(normalizeKey(GenTypeEnum.WIND.getName()));
        }
        if (f.contains("光")) {
            return genTypeNameToId.get(normalizeKey(GenTypeEnum.SOLAR.getName()));
        }
        return null;
    }

    private int detectYearFromFileName(String fileName) {
        if (blank(fileName)) {
            return LocalDate.now().getYear();
        }
        Matcher matcher = YEAR_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return LocalDate.now().getYear();
    }

    private PeriodParseResult parseContractPeriod(String raw, int defaultYear) {
        if (blank(raw)) {
            return new PeriodParseResult(null, null, defaultYear);
        }
        String text = clean(raw);
        int year = defaultYear;
        Matcher yearMatcher = YEAR_PATTERN.matcher(text);
        if (yearMatcher.find()) {
            year = Integer.parseInt(yearMatcher.group(1));
            text = text.replace(yearMatcher.group(1) + "年", "");
        }
        text = text.replace("月", "").replace("至", "-").replace("到", "-");
        int startMonth;
        int endMonth;
        Matcher periodMatcher = PERIOD_PATTERN.matcher(text);
        if (periodMatcher.find()) {
            startMonth = Integer.parseInt(periodMatcher.group(1));
            endMonth = Integer.parseInt(periodMatcher.group(2));
        } else {
            Integer single = parseInteger(text);
            if (single == null) {
                return new PeriodParseResult(null, null, year);
            }
            startMonth = single;
            endMonth = single;
        }
        if (startMonth < 1 || startMonth > 12 || endMonth < 1 || endMonth > 12) {
            return new PeriodParseResult(null, null, year);
        }
        LocalDate start = LocalDate.of(year, startMonth, 1);
        LocalDate end = LocalDate.of(year, endMonth, 1).withDayOfMonth(LocalDate.of(year, endMonth, 1).lengthOfMonth());
        return new PeriodParseResult(start, end, year);
    }

    private LocalDate parseTransactionDate(String raw, int defaultYear) {
        if (blank(raw)) {
            return null;
        }
        return parseDate(raw, defaultYear);
    }

    private LocalDate parseDate(String raw, Integer defaultYear) {
        if (blank(raw)) {
            return null;
        }
        String text = clean(raw)
                .replace("\"", "")
                .replace("'", "")
                .replace("“", "")
                .replace("”", "")
                .replace("‘", "")
                .replace("’", "")
                .replace("年", "-")
                .replace("月", "-")
                .replace("日", "")
                .replace("/", "-")
                .replace(".", "-");
        text = text.replace("T", " ").replace("t", " ").trim();
        text = text.replaceAll("\\s+", " ");
        String dateText = text.contains(" ") ? text.substring(0, text.indexOf(' ')) : text;
        try {
            if (dateText.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                String[] p = dateText.split("-");
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
            if (dateText.matches("\\d{4}-\\d{1,2}")) {
                String[] p = dateText.split("-");
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), 1);
            }
            if (dateText.matches("\\d{1,2}-\\d{1,2}") && defaultYear != null) {
                String[] p = dateText.split("-");
                return LocalDate.of(defaultYear, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            }
            if (dateText.matches("\\d{1,2}") && defaultYear != null) {
                return LocalDate.of(defaultYear, Integer.parseInt(dateText), 1);
            }
            Matcher numeric = Pattern.compile("(\\d{1,4})").matcher(dateText);
            List<Integer> parts = new ArrayList<>();
            while (numeric.find()) {
                parts.add(Integer.parseInt(numeric.group(1)));
            }
            if (parts.size() >= 3) {
                int year = parts.get(0);
                int month = parts.get(1);
                int day = parts.get(2);
                // e.g. 1/13/26 -> 2026-01-13
                if (year <= 12 && month <= 31 && day <= 99) {
                    int yy = day;
                    year = yy >= 70 ? 1900 + yy : 2000 + yy;
                    day = month;
                    month = parts.get(0);
                }
                if (year < 100 && defaultYear != null) {
                    year = defaultYear;
                }
                return LocalDate.of(year, month, day);
            }
            if (parts.size() >= 2) {
                int first = parts.get(0);
                int second = parts.get(1);
                if (String.valueOf(first).length() == 4) {
                    return LocalDate.of(first, second, 1);
                }
                if (defaultYear != null) {
                    return LocalDate.of(defaultYear, first, second);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String getFirst(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (!blank(value)) {
                return clean(value);
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(String value) {
        if (blank(value)) {
            return null;
        }
        String text = clean(value);
        if (text.equals("-") || text.equals("/") || text.equals("（公告未披露）") || text.equals("公告未披露")) {
            return null;
        }
        text = text.replace(",", "");
        if (text.endsWith("%")) {
            String n = text.substring(0, text.length() - 1);
            try {
                return new BigDecimal(n).divide(new BigDecimal("100"));
            } catch (Exception ex) {
                return null;
            }
        }
        try {
            return new BigDecimal(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal parsePercent(String value) {
        BigDecimal decimal = parseDecimal(value);
        if (decimal == null) {
            return null;
        }
        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return decimal;
    }

    private Boolean parseBooleanYN(String value) {
        if (blank(value)) {
            return Boolean.FALSE;
        }
        String text = clean(value).toLowerCase(Locale.ROOT);
        return "是".equals(text) || "y".equals(text) || "yes".equals(text) || "true".equals(text) || "1".equals(text);
    }

    private Integer parseInteger(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            String text = clean(value);
            if (text.contains(".")) {
                text = text.substring(0, text.indexOf('.'));
            }
            return Integer.parseInt(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeKey(String value) {
        return clean(value).replace(" ", "");
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", "").replace("\r", "").trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty() || "nan".equalsIgnoreCase(value.trim());
    }

    private Date toDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private String formatTs(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FMT);
    }

    private String buildReasonMessage(ParsedFile parsed, boolean noValidRows) {
        if (parsed.reasonCounts.isEmpty()) {
            return noValidRows ? "No valid rows" : "Skipped rows";
        }
        String detail = parsed.reasonCounts.entrySet()
                .stream()
                .limit(MAX_REASON_ITEMS)
                .map(e -> e.getKey() + " x" + e.getValue())
                .collect(Collectors.joining("; "));
        return (noValidRows ? "No valid rows. Reasons: " : "Skipped rows. Reasons: ") + detail;
    }

    private String buildJobFailureMessage(List<ParsedFile> parsedFiles) {
        List<String> fileReasons = parsedFiles.stream()
                .filter(p -> p.errorMessage != null && !p.errorMessage.isBlank())
                .limit(MAX_REASON_ITEMS)
                .map(p -> (p.fileName == null ? "unknown-file" : p.fileName) + ": " + p.errorMessage)
                .toList();
        if (fileReasons.isEmpty()) {
            return "No valid rows after normalization";
        }
        return cap("No valid rows after normalization. " + String.join(" | ", fileReasons), MAX_ERROR_MESSAGE_LEN);
    }

    private String cap(String value, int limit) {
        if (value == null) {
            return null;
        }
        if (value.length() <= limit) {
            return value;
        }
        if (limit <= 3) {
            return value.substring(0, limit);
        }
        return value.substring(0, limit - 3) + "...";
    }

    private enum FileType {
        LONGTERM, SPOT, UNKNOWN
    }

    private enum UploadMode {
        MIXED,
        LONGTERM_ONLY,
        SPOT_ONLY
    }

    private static class ParsedSheet {
        private final FileType type;
        private final List<Map<String, String>> rows;

        private ParsedSheet(FileType type, List<Map<String, String>> rows) {
            this.type = type;
            this.rows = rows;
        }
    }

    private static class LookupMaps {
        private Map<String, Long> companyNameToId;
        private Map<String, Integer> genTypeNameToId;
        private Map<String, Integer> transactionTypeNameToId;
        private Map<String, Integer> transactionPeriodNameToId;
    }

    private static class BufferedUploadFile {
        private final String fileName;
        private final byte[] bytes;

        private BufferedUploadFile(String fileName, byte[] bytes) {
            this.fileName = fileName;
            this.bytes = bytes;
        }
    }

    private static class SpotDedupContext {
        private final Map<String, String> baselineByKey = new HashMap<>();
        private final Set<String> currentUploadKeys = new HashSet<>();
    }

    private static class LongtermDedupContext {
        private final Map<String, String> baselineByKey = new HashMap<>();
        private final Set<String> currentUploadKeys = new HashSet<>();
    }

    private static class ParsedFile {
        private String fileName;
        private String dataType;
        private String status;
        private int totalRows;
        private int normalizedRows;
        private int duplicateRows;
        private int newRows;
        private int updatedRows;
        private int skippedRows;
        private int errorCount;
        private String errorMessage;
        private List<NormalizedLongtermRow> longtermRows;
        private List<NormalizedSpotRow> spotRows;
        private final LinkedHashMap<String, Integer> reasonCounts = new LinkedHashMap<>();

        private void addReason(String reason) {
            if (reason == null || reason.isBlank()) {
                return;
            }
            reasonCounts.put(reason, reasonCounts.getOrDefault(reason, 0) + 1);
        }

        private ImportDataFileItem toItem() {
            ImportDataFileItem item = new ImportDataFileItem();
            item.setFileName(fileName);
            item.setDataType(dataType);
            item.setStatus(status);
            item.setTotalRows(totalRows);
            item.setNormalizedRows(normalizedRows);
            item.setDuplicateRows(duplicateRows);
            item.setNewRows(newRows);
            item.setUpdatedRows(updatedRows);
            item.setSkippedRows(skippedRows);
            item.setErrorCount(errorCount);
            item.setErrorMessage(errorMessage);
            return item;
        }
    }

    private static class NormalizedLongtermRow {
        private String fileName;
        private Integer transactionId;
        private Long companyId;
        private String place;
        private LocalDate transactionDate;
        private String transactionName;
        private Integer transactionTypeId;
        private String outsendProvince;
        private Integer genTypeId;
        private Integer transactionPeriodId;
        private Integer transactionStartYear;
        private Integer transactionEndYear;
        private LocalDate contractStartDate;
        private LocalDate contractEndDate;
        private Boolean isGreen;
        private Boolean isCheap;
        private BigDecimal basePrice;
        private BigDecimal marketSize;
        private BigDecimal marketParticipationCapacity;
        private BigDecimal marketAvgPrice;
        private BigDecimal chngParticipationCapacity;
        private BigDecimal chngTransactionAmount;
        private BigDecimal chngAvgPrice;
        private BigDecimal envPremium;
        private String dataSource;
        private String note;
    }

    private static class NormalizedSpotRow {
        private String fileName;
        private Long companyId;
        private LocalDate date;
        private Integer genTypeId;
        private BigDecimal genAmount;
        private BigDecimal longtermAmount;
        private BigDecimal longtermPrice;
        private BigDecimal longtermPercent;
        private BigDecimal spotPrice;
        private BigDecimal chngSpotPrice;
        private String dataSource;
        private String note;
    }

    private String buildLongtermKey(NormalizedLongtermRow row) {
        return String.join("|",
                keyPart(row.companyId),
                keyPart(row.place),
                keyPart(row.transactionDate),
                keyPart(row.transactionName),
                keyPart(row.transactionTypeId),
                keyPart(row.outsendProvince),
                keyPart(row.genTypeId),
                keyPart(row.transactionPeriodId),
                keyPart(row.transactionStartYear),
                keyPart(row.transactionEndYear),
                keyPart(row.contractStartDate),
                keyPart(row.contractEndDate)
        );
    }

    private String buildLongtermValueFingerprint(NormalizedLongtermRow row) {
        return String.join("|",
                keyPart(row.transactionId),
                keyPart(row.isGreen),
                keyPart(row.isCheap),
                keyPart(row.basePrice),
                keyPart(row.marketSize),
                keyPart(row.marketParticipationCapacity),
                keyPart(row.marketAvgPrice),
                keyPart(row.chngParticipationCapacity),
                keyPart(row.chngTransactionAmount),
                keyPart(row.chngAvgPrice),
                keyPart(row.envPremium),
                keyPart(row.note)
        );
    }

    private String buildSpotKey(NormalizedSpotRow row) {
        // Spot records use business uniqueness: company + date + gen type.
        return String.join("|",
                keyPart(row.companyId),
                keyPart(row.date),
                keyPart(row.genTypeId)
        );
    }

    private String buildSpotValueFingerprint(NormalizedSpotRow row) {
        return String.join("|",
                keyPartScaled(row.genAmount, 6),
                keyPartScaled(row.longtermAmount, 6),
                keyPartScaled(row.longtermPrice, 4),
                // Backward-compatible precision for historical spot data.
                keyPartScaled(row.longtermPercent, 4),
                keyPartScaled(row.spotPrice, 4),
                keyPartScaled(row.chngSpotPrice, 4)
        );
    }

    private String keyPartScaled(BigDecimal value, int scale) {
        if (value == null) {
            return "";
        }
        return keyPart(value.setScale(scale, RoundingMode.HALF_UP));
    }

    private String keyPart(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        return String.valueOf(value).trim();
    }

    private static class PeriodParseResult {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final Integer year;

        private PeriodParseResult(LocalDate startDate, LocalDate endDate, Integer year) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.year = year;
        }
    }

    private record MasterStatus(String binlogFile, Long binlogPosition, String gtidSet) {}
}
