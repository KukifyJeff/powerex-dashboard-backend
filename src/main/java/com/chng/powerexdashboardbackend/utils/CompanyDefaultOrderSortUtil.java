package com.chng.powerexdashboardbackend.utils;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Global sorting utility for SpotTracking & financial modules.
 *
 * RULE:
 * All company-level responses MUST be sorted by companyId ASC.
 */
public class CompanyDefaultOrderSortUtil {

    /**
     * Generic sort by companyId (safe null handling)
     */
    public static <T> List<T> sortByCompanyId(
            List<T> list,
            Function<T, Integer> companyIdGetter
    ) {
        if (list == null || list.isEmpty()) {
            return list;
        }

        list.sort(Comparator.comparing(
                item -> {
                    Integer id = companyIdGetter.apply(item);
                    return id == null ? Integer.MAX_VALUE : id;
                }
        ));

        return list;
    }

    /**
     * Convenience method for already-known DTOs using lambda
     */
    public static <T> List<T> safeSort(List<T> list, Function<T, Integer> companyIdGetter) {
        return sortByCompanyId(list, companyIdGetter);
    }
}
