package com.pwj.tracker.dto;

import lombok.Data;
import java.util.List;

@Data
public class SplitVendorRequest {
    private String vendor;
    private String pwjType;
    private List<ItemRow> items;

    @Data
    public static class ItemRow {
        private String item;
        private String unit;
        private String qty;
        private String rate;
        private String spec;
    }
}
