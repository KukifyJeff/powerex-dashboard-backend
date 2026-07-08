package com.chng.powerexdashboardbackend.mapper.spottracking;

import com.chng.powerexdashboardbackend.dto.spottracking.SpotDataDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SpotMapper {

    @Select("""
        SELECT
            company_id,
            gen_type_id,
            date,
            gen_amount,
            longterm_amount,
            longterm_price,
            spot_price,
            chng_spot_price
        FROM spot_transactions
        WHERE date BETWEEN #{startDate} AND #{endDate}
          AND gen_type_id = #{genTypeId}
    """)
    List<SpotDataDTO> selectByCondition(
            @Param("genTypeId") Integer genTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // =========================================================
    // ② 公司维度汇总（对应 Python spot_result）
    // =========================================================
    @Select("""
        SELECT
            c.id AS companyId,
            c.name AS companyName,

            COALESCE(SUM(t.gen_amount), 0) AS genAmount,
            COALESCE(SUM(t.longterm_amount), 0) AS longtermAmount,
            COALESCE(AVG(t.spot_price), 0) AS spotAvgPrice,

            COALESCE(SUM(t.longterm_amount * t.longterm_price), 0) AS longtermRevenueSum,
            COALESCE(SUM(t.gen_amount * t.chng_spot_price), 0) AS chngRevenueSum,

            c.capacity AS capacity

        FROM companies c
        LEFT JOIN spot_transactions t
            ON c.id = t.company_id
            AND t.gen_type_id = #{genTypeId}
            AND t.date BETWEEN #{startDate} AND #{endDate}

        GROUP BY c.id, c.name, c.capacity
        ORDER BY c.id
    """)
    List<SpotDataDTO> selectCompanySpotSummary(
            @Param("genTypeId") Integer genTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Select("""
        SELECT DISTINCT YEAR(date)
        FROM spot_transactions
        WHERE date IS NOT NULL
        ORDER BY YEAR(date)
    """)
    List<Integer> selectContractYears();

    // =========================================================
    // ③ 日维度数据（用于月筛选 / drill down）
    // =========================================================
    @Select("""
        SELECT
            company_id AS companyId,
            c.name AS companyName,
            date,

            gen_amount,
            longterm_amount,
            longterm_price,
            spot_price,
            chng_spot_price,

            c.capacity AS capacity

        FROM spot_transactions
        LEFT JOIN companies c ON company_id = c.id
        WHERE gen_type_id = #{genTypeId}
          AND date BETWEEN #{startDate} AND #{endDate}
        ORDER BY date
    """)
    List<SpotDataDTO> selectDailyDetail(
            @Param("genTypeId") Integer genTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // =========================================================
    // ④ 公司+月度汇总（替代 Python groupby month）
    // =========================================================
    @Select("""
        SELECT
            company_id AS companyId,
            c.name AS companyName,

            DATE_FORMAT(date, '%Y-%m') AS month,

            SUM(gen_amount) AS genAmount,
            SUM(longterm_amount) AS longtermAmount,

            SUM(longterm_amount * longterm_price) AS longtermRevenueSum,
            SUM(gen_amount * spot_price) AS spotRevenueSum,
            SUM(gen_amount * chng_spot_price) AS chngRevenueSum,

            c.capacity AS capacity

        FROM spot_transactions
        LEFT JOIN companies c ON company_id = c.id
        WHERE gen_type_id = #{genTypeId}
          AND date BETWEEN #{startDate} AND #{endDate}
        GROUP BY company_id, c.name, DATE_FORMAT(date, '%Y-%m')
    """)
    List<SpotDataDTO> selectMonthlySummary(
            @Param("genTypeId") Integer genTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}