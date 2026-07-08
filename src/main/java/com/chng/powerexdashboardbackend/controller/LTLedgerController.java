package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerSummaryDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerTrendDTO;
import com.chng.powerexdashboardbackend.request.LTLedgerQuery;
import com.chng.powerexdashboardbackend.services.ltledger.LTLedgerServices;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/detail")
    public List<LTLedgerDTO> getDetail(@RequestBody LTLedgerQuery query) {
        return ltLedgerServices.getDetail(query);
    }

    @PostMapping("/data")
    public List<LTLedgerDTO> getLedger(@RequestBody LTLedgerQuery query) {
        return ltLedgerServices.getLedger(query);
    }

    @GetMapping("/options")
    public LTLedgerFilterOptionsDTO getFilterOptions() {
        return ltLedgerServices.getFilterOptions();
    }

    @PostMapping("/summary")
    public LTLedgerSummaryDTO getSummary(@RequestBody LTLedgerQuery query) {
        return ltLedgerServices.getSummary(query);
    }

    @PostMapping("/trend")
    public List<LTLedgerTrendDTO> getTrend(@RequestBody LTLedgerQuery query) {
        return ltLedgerServices.getTrend(query);
    }
}