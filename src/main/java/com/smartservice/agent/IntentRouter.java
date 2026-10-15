package com.smartservice.agent;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class IntentRouter {
    public Intent route(String message) {
        if (containsAny(message, "人工", "客服人员", "转客服", "真人客服")) return Intent.TRANSFER_HUMAN;
        if (containsAny(message, "取消订单", "撤销订单", "撤单", "不想要", "不要了")) return Intent.CANCEL_ORDER;
        if (containsAny(message, "退货", "退款", "换货", "换新")
                && containsAny(message, "规则", "政策", "条件", "运费", "凭证", "几天", "多久", "到账", "时效", "能不能", "可以吗")) {
            return Intent.QUERY_POLICY;
        }
        if (containsAny(message, "到哪", "物流", "快递", "配送", "送到", "运单", "包裹")) return Intent.QUERY_LOGISTICS;
        if (containsAny(message, "退货", "退款", "退掉", "坏了", "质量问题", "换货", "换新", "申请售后")) return Intent.APPLY_REFUND;
        if (containsAny(message, "规则", "政策", "保修", "多久", "几天", "优惠券", "价保", "发票", "售后期限")) return Intent.QUERY_POLICY;
        return Intent.GENERAL;
    }

    public String productKeyword(String message) {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("耳机", "耳机");
        aliases.put("键盘", "键盘");
        aliases.put("充电宝", "充电宝");
        return aliases.entrySet().stream().filter(it -> message.contains(it.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse("");
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
