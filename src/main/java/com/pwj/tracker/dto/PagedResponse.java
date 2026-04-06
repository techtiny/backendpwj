package com.pwj.tracker.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private boolean first;

    // Summary stats
    private Long totalClosed;
    private Long totalOpen;
    private Long totalProceed;
    private Long totalHold;
    private Long totalNotApproved;
}
