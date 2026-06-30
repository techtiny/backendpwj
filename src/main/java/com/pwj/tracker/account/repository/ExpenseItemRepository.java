package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, Long> {

    List<ExpenseItem> findByProjectIdAndCategoryOrderById(Long projectId, String category);

    List<ExpenseItem> findByProjectIdOrderByCategoryAscIdAsc(Long projectId);

    @Query("SELECT e.refNo FROM ExpenseItem e WHERE e.projectId = :projectId AND e.refNo IS NOT NULL AND e.refNo != ''")
    List<String> findTrackedRefsByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT e FROM ExpenseItem e WHERE e.category IS NULL OR e.category = ''")
    List<ExpenseItem> findUncategorized();

    @Query("SELECT COALESCE(SUM(e.pwjTotalPayable), 0) FROM ExpenseItem e WHERE e.projectId = :projectId AND e.category = :category")
    BigDecimal sumPwjTotalPayable(Long projectId, String category);

    @Query("SELECT COALESCE(SUM(e.vendorTotalPayable), 0) FROM ExpenseItem e WHERE e.projectId = :projectId AND e.category = :category")
    BigDecimal sumVendorTotalPayable(Long projectId, String category);

    @Query("SELECT COALESCE(SUM(e.vendorGstAmount), 0) FROM ExpenseItem e WHERE e.projectId = :projectId AND e.category = :category")
    BigDecimal sumVendorGst(Long projectId, String category);

    @Query("SELECT COALESCE(SUM(e.paidAmount), 0) FROM ExpenseItem e WHERE e.projectId = :projectId AND e.category = :category")
    BigDecimal sumPaid(Long projectId, String category);

    @Query("""
        SELECT e.category,
               COALESCE(SUM(e.pwjTotalPayable), 0),
               COALESCE(SUM(e.paidAmount), 0),
               COALESCE(SUM(e.vendorTotalPayable), 0)
        FROM ExpenseItem e
        WHERE e.projectId = :projectId
        GROUP BY e.category
        """)
    List<Object[]> getSummaryByProject(Long projectId);

    @Query("""
        SELECT e.projectId,
               COALESCE(SUM(CASE WHEN e.category = 'MATERIAL'      THEN e.pwjTotalPayable ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN e.category = 'LABOUR'        THEN e.pwjTotalPayable ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN e.category = 'SUBCONTRACT'   THEN e.pwjTotalPayable ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN e.category = 'CONSULTANTS'   THEN e.pwjTotalPayable ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN e.category = 'MISCELLANEOUS' THEN e.pwjTotalPayable ELSE 0 END), 0)
        FROM ExpenseItem e
        GROUP BY e.projectId
        """)
    List<Object[]> getExpenseBreakdownByProject();

    @Query("SELECT e.projectId, COALESCE(SUM(e.vendorGstAmount), 0) FROM ExpenseItem e GROUP BY e.projectId")
    List<Object[]> getTotalVendorGstByProject();

    // Returns a single row [sumPwjGst, sumVendorGst] for the whole project
    @Query("""
        SELECT COALESCE(SUM(e.pwjGstAmount), 0),
               COALESCE(SUM(e.vendorGstAmount), 0)
        FROM ExpenseItem e
        WHERE e.projectId = :projectId
        """)
    List<Object[]> getGstTotalsByProject(Long projectId);
}
