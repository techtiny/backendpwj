package com.pwj.tracker.account.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class DashboardStatsDto {
    private BigDecimal totalQuote;
    private BigDecimal totalBudget;
    private BigDecimal totalSpent;
    private BigDecimal totalCollection;
    private BigDecimal avgProfitMargin;
    private long totalProjects;
    private long activeProjects;
    private long completedProjects;
    private List<Map<String, Object>> categoryBreakdown;
    private List<Map<String, Object>> projectExpenses;

    public DashboardStatsDto() {}

    public BigDecimal getTotalQuote() { return totalQuote; }
    public void setTotalQuote(BigDecimal totalQuote) { this.totalQuote = totalQuote; }
    public BigDecimal getTotalBudget() { return totalBudget; }
    public void setTotalBudget(BigDecimal totalBudget) { this.totalBudget = totalBudget; }
    public BigDecimal getTotalSpent() { return totalSpent; }
    public void setTotalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; }
    public BigDecimal getTotalCollection() { return totalCollection; }
    public void setTotalCollection(BigDecimal totalCollection) { this.totalCollection = totalCollection; }
    public BigDecimal getAvgProfitMargin() { return avgProfitMargin; }
    public void setAvgProfitMargin(BigDecimal avgProfitMargin) { this.avgProfitMargin = avgProfitMargin; }
    public long getTotalProjects() { return totalProjects; }
    public void setTotalProjects(long totalProjects) { this.totalProjects = totalProjects; }
    public long getActiveProjects() { return activeProjects; }
    public void setActiveProjects(long activeProjects) { this.activeProjects = activeProjects; }
    public long getCompletedProjects() { return completedProjects; }
    public void setCompletedProjects(long completedProjects) { this.completedProjects = completedProjects; }
    public List<Map<String, Object>> getCategoryBreakdown() { return categoryBreakdown; }
    public void setCategoryBreakdown(List<Map<String, Object>> categoryBreakdown) { this.categoryBreakdown = categoryBreakdown; }
    public List<Map<String, Object>> getProjectExpenses() { return projectExpenses; }
    public void setProjectExpenses(List<Map<String, Object>> projectExpenses) { this.projectExpenses = projectExpenses; }
}
