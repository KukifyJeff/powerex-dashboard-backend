package com.chng.powerexdashboardbackend.mapper.spottracking;

import com.chng.powerexdashboardbackend.dto.spottracking.LongtermMonthlyDTO;
import com.chng.powerexdashboardbackend.dto.spottracking.LongtermYearlyDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LongtermMapper {

    // =========================================================
    // ① 基础查询（按发电类型）
    // =========================================================
    @Select("""
        SELECT 
            company_id AS companyId,
            gen_type_id AS genTypeId,
            transaction_type_id AS transactionTypeId,
            transaction_period_id AS transactionPeriodId,
            chng_transaction_amount AS transactionAmount,
            chng_avg_price AS transactionPrice
        FROM longterm_transactions
        WHERE gen_type_id = #{genTypeId}
    """)
    List<LongtermYearlyDTO> selectByGenType(@Param("genTypeId") Integer genTypeId);


    // =========================================================
    // ② 年度交易汇总（对应 Python yearly_result）
    // transaction_period_id: 1=年度, 2=年度分解
    // =========================================================
    @Select("""
    SELECT 
        c.id AS companyId,
        c.name AS companyName,

        COALESCE(SUM(t.chng_transaction_amount), 0) AS transactionAmount,

        COALESCE(
            SUM(t.chng_transaction_amount * t.chng_avg_price)
            / NULLIF(SUM(t.chng_transaction_amount), 0),
            0
        ) AS transactionPrice

    FROM companies c
    LEFT JOIN longterm_transactions t 
        ON c.id = t.company_id
        AND t.gen_type_id = #{genTypeId}
        AND t.contract_start_date <= #{endDate}
        AND t.contract_end_date >= #{startDate}
        AND t.transaction_period_id IN (2)

    GROUP BY c.id, c.name
    ORDER BY c.id
""")
    List<LongtermYearlyDTO> selectYearlySummary(
        @Param("genTypeId") Integer genTypeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );


    // =========================================================
    // ③ 月度交易汇总（对应 Python monthly_result）
    // transaction_period_id: 3=多月,4=多月分解,5=月度,6=月度分解
    // =========================================================
    @Select("""
    SELECT 
        c.id AS companyId,
        c.name AS companyName,

        COALESCE(SUM(t.chng_transaction_amount), 0) AS transactionAmount,

        COALESCE(
            SUM(t.chng_transaction_amount * t.chng_avg_price)
            / NULLIF(SUM(t.chng_transaction_amount), 0),
            0
        ) AS transactionPrice

    FROM companies c
    LEFT JOIN longterm_transactions t 
        ON c.id = t.company_id
        AND t.gen_type_id = #{genTypeId}
        AND t.contract_start_date <= #{endDate}
        AND t.contract_end_date >= #{startDate}
        AND t.transaction_period_id IN (4, 5)

    GROUP BY c.id, c.name
    ORDER BY c.id
""")
    List<LongtermMonthlyDTO> selectMonthlySummary(
        @Param("genTypeId") Integer genTypeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );



}