package com.schoolhub.schoolservice.dto;

import java.math.BigDecimal;

public class FineRuleReq {
    private String ruleType;
    private BigDecimal value;
    
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
}
