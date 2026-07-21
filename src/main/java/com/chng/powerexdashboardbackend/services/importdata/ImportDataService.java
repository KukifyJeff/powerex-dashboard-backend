package com.chng.powerexdashboardbackend.services.importdata;

import com.chng.powerexdashboardbackend.config.ImportDataProperties;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
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

    private final JdbcTemplate jdbcTemplate;
    private final ImportDataProperties importDataProperties;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final DataFormatter dataFormatter = new DataFormatter(Locale.SIMPLIFIED_CHINESE);

    public ImportDataUploadResponse uploadAndNormalize(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeInternal(files, createdBy, UploadMode.MIXED);
    }

    public ImportDataUploadResponse uploadLongtermAndNormalize(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeInternal(files, createdBy, UploadMode.LONGTERM_ONLY);
    }

    public ImportDataUploadResponse uploadSpotAndNormalize(MultipartFile[] files, String createdBy) {
        return uploadAndNormalizeInternal(files, createdBy, UploadMode.SPOT_ONLY);
    }

    private ImportDataUploadResponse uploadAndNormalizeInternal(MultipartFile[] files, String createdBy, UploadMode mode) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("files is required");
        }

        long jobId = createJob(files.length, createdBy);
        LookupMaps lookupMaps = loadLookupMaps();
        List<ImportDataFileItem> fileItems = new ArrayList<>();
        List<ParsedFile> parsedFiles = new ArrayList<>();
        List<NormalizedLongtermRow> allLongtermRows = new ArrayList<>();
        List<NormalizedSpotRow> allSpotRows = new ArrayList<>();
        int failedFileCount = 0;

        for (MultipartFile file : files) {
            ParsedFile parsed = parseFile(file, lookupMaps, mode);
            parsedFiles.add(parsed);
            fileItems.add(parsed.toItem());
            if ("FAILED".equals(parsed.status)) {
                failedFileCount++;
                insertFileRecord(jobId, parsed);
                continue;
            }
            allLongtermRows.addAll(parsed.longtermRows);
            allSpotRows.addAll(parsed.spotRows);
            insertFileRecord(jobId, parsed);
        }

        insertLongtermStagingRows(jobId, allLongtermRows);
        insertSpotStagingRows(jobId, allSpotRows);

        String finalStatus = allLongtermRows.isEmpty() && allSpotRows.isEmpty() ? JOB_STATUS_FAILED : JOB_STATUS_NORMALIZED;
        String errorMessage = JOB_STATUS_FAILED.equals(finalStatus) ? buildJobFailureMessage(parsedFiles) : null;
        updateJobAfterNormalize(jobId, finalStatus, allLongtermRows.size(), allSpotRows.size(), failedFileCount, errorMessage);

        ImportDataUploadResponse response = new ImportDataUploadResponse();
        response.setSuccess(JOB_STATUS_NORMALIZED.equals(finalStatus));
        response.setMessage(JOB_STATUS_NORMALIZED.equals(finalStatus) ? "Normalization completed" : "Normalization failed");
        response.setData(getJob(jobId));
        response.getData().setFiles(fileItems);
        return response;
    }

    public ImportDataJobResponse getJob(Long jobId) {
        ImportDataJobResponse job = jdbcTemplate.query("""
                SELECT
                    id, status, uploaded_file_count, longterm_row_count, spot_row_count, failed_file_count,
                    error_message, created_at, normalized_at, confirmed_at
                FROM import_jobs
                WHERE id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            ImportDataJobResponse item = new ImportDataJobResponse();
            item.setJobId(rs.getLong("id"));
            item.setStatus(rs.getString("status"));
            item.setUploadedFileCount(rs.getInt("uploaded_file_count"));
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
                    file_name, data_type, status, total_rows, normalized_rows, skipped_rows, error_count, error_message
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
        assertAdminPassword(adminPassword);
        ImportDataJobResponse job = getJob(jobId);
        if (!JOB_STATUS_NORMALIZED.equals(job.getStatus())) {
            throw new IllegalStateException("Job status must be NORMALIZED");
        }

        Long previousActiveVersionId = getActiveVersionId();
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
        return response;
    }

    @Transactional
    public ImportDataActionResponse rollbackToVersion(Long versionId, String adminPassword, String reason) {
        assertAdminPassword(adminPassword);
        return rollbackToVersionInternal(versionId, reason, "admin");
    }

    @Transactional
    public ImportDataActionResponse rollbackRecentVersions(Integer steps, String adminPassword, String reason) {
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
        return response;
    }

    private void assertAdminPassword(String adminPassword) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new SecurityException("Admin password is required");
        }
        String hash = importDataProperties.getAdminPasswordHash();
        if (hash == null || hash.isBlank()) {
            throw new IllegalStateException("IMPORT_DATA_ADMIN_PASSWORD_HASH is not configured");
        }
        if (!passwordEncoder.matches(adminPassword, hash)) {
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
            throw new IllegalStateException("MySQL binary logging is not enabled. Enable binlog before using confirm/rollback workflow.");
        }
        return status;
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

    private void insertFileRecord(long jobId, ParsedFile file) {
        jdbcTemplate.update("""
                INSERT INTO import_job_files (
                    job_id, file_name, data_type, status, total_rows, normalized_rows, skipped_rows, error_count, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, jobId, file.fileName, file.dataType, file.status, file.totalRows, file.normalizedRows, file.skippedRows, file.errorCount, cap(file.errorMessage, MAX_ERROR_MESSAGE_LEN));
    }

    private void insertLongtermStagingRows(long jobId, List<NormalizedLongtermRow> rows) {
        for (NormalizedLongtermRow row : rows) {
            jdbcTemplate.update("""
                    INSERT INTO import_job_longterm_rows (
                        job_id, file_name, transaction_id, company_id, place, transaction_date, transaction_name, transaction_type_id,
                        outsend_province, gen_type_id, transaction_period_id, transaction_start_year, transaction_end_year,
                        contract_start_date, contract_end_date, is_green, is_cheap, base_price, market_size,
                        market_participation_capacity, market_avg_price, chng_participation_capacity,
                        chng_transaction_amount, chng_avg_price, env_premium, data_source, note
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    jobId,
                    row.fileName,
                    row.transactionId,
                    row.companyId,
                    row.place,
                    toDate(row.transactionDate),
                    row.transactionName,
                    row.transactionTypeId,
                    row.outsendProvince,
                    row.genTypeId,
                    row.transactionPeriodId,
                    row.transactionStartYear,
                    row.transactionEndYear,
                    toDate(row.contractStartDate),
                    toDate(row.contractEndDate),
                    row.isGreen,
                    row.isCheap,
                    row.basePrice,
                    row.marketSize,
                    row.marketParticipationCapacity,
                    row.marketAvgPrice,
                    row.chngParticipationCapacity,
                    row.chngTransactionAmount,
                    row.chngAvgPrice,
                    row.envPremium,
                    row.dataSource,
                    row.note
            );
        }
    }

    private void insertSpotStagingRows(long jobId, List<NormalizedSpotRow> rows) {
        for (NormalizedSpotRow row : rows) {
            jdbcTemplate.update("""
                    INSERT INTO import_job_spot_rows (
                        job_id, file_name, company_id, date, gen_type_id, gen_amount, longterm_amount,
                        longterm_price, longterm_percent, spot_price, chng_spot_price, data_source, note
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    jobId,
                    row.fileName,
                    row.companyId,
                    toDate(row.date),
                    row.genTypeId,
                    row.genAmount,
                    row.longtermAmount,
                    row.longtermPrice,
                    row.longtermPercent,
                    row.spotPrice,
                    row.chngSpotPrice,
                    row.dataSource,
                    row.note
            );
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
            return parsed;
        }

        String lower = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
            parsed.errorMessage = "Only .xlsx/.xls is supported";
            parsed.errorCount = 1;
            return parsed;
        }

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            ParsedSheet parsedSheet = readSheet(sheet);

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
                    parsed.errorMessage = buildReasonMessage(parsed);
                }
            } else if (parsed.errorCount > 0 || parsed.skippedRows > 0) {
                parsed.status = "PARTIAL";
                if (parsed.errorMessage == null) {
                    parsed.errorMessage = buildReasonMessage(parsed);
                }
            } else {
                parsed.status = "SUCCESS";
            }
            return parsed;
        } catch (Exception ex) {
            parsed.errorCount = 1;
            parsed.errorMessage = "Parse failed: " + ex.getMessage();
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
            if (blank(companyName)) {
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

    private ParsedSheet readSheet(Sheet sheet) {
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
                String value = clean(cellValue(row.getCell(c)));
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
        List<String> headers = buildHeaders(headerRow);
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
                String value = clean(cellValue(row.getCell(c)));
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

    private List<String> buildHeaders(Row row) {
        List<String> headers = new ArrayList<>();
        if (row == null) {
            return headers;
        }
        Map<String, Integer> seen = new HashMap<>();
        int max = row.getLastCellNum();
        for (int i = 0; i < max; i++) {
            String raw = clean(cellValue(row.getCell(i)));
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

    private String cellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell);
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
        text = text.replaceAll("\\s+", "");
        try {
            if (text.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                String[] p = text.split("-");
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
            if (text.matches("\\d{4}-\\d{1,2}")) {
                String[] p = text.split("-");
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), 1);
            }
            if (text.matches("\\d{1,2}-\\d{1,2}") && defaultYear != null) {
                String[] p = text.split("-");
                return LocalDate.of(defaultYear, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            }
            if (text.matches("\\d{1,2}") && defaultYear != null) {
                return LocalDate.of(defaultYear, Integer.parseInt(text), 1);
            }
            Matcher numeric = Pattern.compile("(\\d{1,4})").matcher(text);
            List<Integer> parts = new ArrayList<>();
            while (numeric.find()) {
                parts.add(Integer.parseInt(numeric.group(1)));
            }
            if (parts.size() >= 3) {
                int year = parts.get(0);
                int month = parts.get(1);
                int day = parts.get(2);
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
        BigDecimal max = new BigDecimal("0.9999");
        if (decimal.compareTo(max) > 0) {
            return max;
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

    private String buildReasonMessage(ParsedFile parsed) {
        if (parsed.reasonCounts.isEmpty()) {
            return "No valid rows";
        }
        String detail = parsed.reasonCounts.entrySet()
                .stream()
                .limit(MAX_REASON_ITEMS)
                .map(e -> e.getKey() + " x" + e.getValue())
                .collect(Collectors.joining("; "));
        return "No valid rows. Reasons: " + detail;
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

    private static class ParsedFile {
        private String fileName;
        private String dataType;
        private String status;
        private int totalRows;
        private int normalizedRows;
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
