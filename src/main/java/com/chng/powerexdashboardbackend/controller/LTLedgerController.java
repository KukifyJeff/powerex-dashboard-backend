package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerSummaryDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerTrendDTO;
import com.chng.powerexdashboardbackend.request.ltledger.LTLedgerCompareExportRequest;
import com.chng.powerexdashboardbackend.request.ltledger.LTLedgerOptionsQuery;
import com.chng.powerexdashboardbackend.request.ltledger.LTLedgerQuery;
import com.chng.powerexdashboardbackend.services.ltledger.LTLedgerServices;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/longterm-ledger")
public class LTLedgerController {

    private final LTLedgerServices ltLedgerServices;

    public LTLedgerController(LTLedgerServices ltLedgerServices) {
        this.ltLedgerServices = ltLedgerServices;
    }

    @PostMapping("/pivot")
    public com.chng.powerexdashboardbackend.responses.ltledger.LTLedgerResponse getPivot(@RequestBody LTLedgerQuery query) {
        return ltLedgerServices.getPivot(query);
    }

    @PostMapping("/company_detail")
    public List<LTLedgerDTO> getCompanyDetail(@RequestBody LTLedgerQuery query) {
        return ltLedgerServices.getDetail(query);
    }

    @PostMapping("/options")
    public LTLedgerFilterOptionsDTO getFilterOptionsDynamic(@RequestBody(required = false) LTLedgerOptionsQuery query) {
        return ltLedgerServices.getFilterOptions(query);
    }

    @PostMapping("/summary")
    public LTLedgerSummaryDTO getSummary(@RequestBody LTLedgerQuery query) {
        return ltLedgerServices.getSummary(query);
    }

    @PostMapping("/trend")
    public List<LTLedgerTrendDTO> getTrend(@RequestBody LTLedgerQuery query) {
        return ltLedgerServices.getTrend(query);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportPivot(@RequestBody(required = false) LTLedgerQuery query) {
        byte[] csv = ltLedgerServices.exportPivotCsv(query);
        String fileName = "lt-ledger-pivot.csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(csv);
    }

    @PostMapping("/export/compare")
    public ResponseEntity<byte[]> exportCompare(@RequestBody LTLedgerCompareExportRequest request) {
        try {
            byte[] csv = ltLedgerServices.exportComparePivotCsv(request);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lt-ledger-pivot-compare.csv\"")
                    .body(csv);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}