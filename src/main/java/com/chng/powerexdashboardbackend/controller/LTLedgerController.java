package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerSummaryDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerTrendDTO;
import com.chng.powerexdashboardbackend.request.LTLedgerOptionsQuery;
import com.chng.powerexdashboardbackend.request.LTLedgerQuery;
import com.chng.powerexdashboardbackend.services.ltledger.LTLedgerServices;
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
}