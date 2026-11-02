package com.tekmaturix.commercialloanunderwritingrealtime.converter;

import java.math.BigDecimal;

public record DocumentExtractionNodeBeanOutputConverter(BigDecimal revenue, BigDecimal annualDebt, Integer creditScore) {

}
