package com.tekmaturix.commercialloanunderwritingrealtime.converter;

import java.math.BigDecimal;

public record ComplianceNodeBeanOutputConverter(BigDecimal fraudScore, Boolean pepMatch, Boolean sanctionsMatch) {

}