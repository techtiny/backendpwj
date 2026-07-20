package com.pwj.tracker.account.dto;

import java.math.BigDecimal;
import java.util.List;

public class SendForPaymentRequest {

    private List<Item> items;

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        private Long id;
        private String paymentType; // "PART" or "FULL"
        private BigDecimal amount;  // required when paymentType = PART; ignored for FULL

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPaymentType() { return paymentType; }
        public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}
