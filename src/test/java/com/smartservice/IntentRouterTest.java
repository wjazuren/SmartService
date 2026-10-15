package com.smartservice;

import com.smartservice.agent.Intent;
import com.smartservice.agent.IntentRouter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRouterTest {
    private final IntentRouter router = new IntentRouter();

    @ParameterizedTest(name = "{index}: {0} -> {1}")
    @MethodSource("routingCases")
    void routesCommonCustomerExpressions(String message, Intent expected) {
        assertThat(router.route(message)).isEqualTo(expected);
    }

    static Stream<Arguments> routingCases() {
        return Stream.of(
                Arguments.of("耳机现在到哪了", Intent.QUERY_LOGISTICS),
                Arguments.of("查一下键盘物流", Intent.QUERY_LOGISTICS),
                Arguments.of("快递怎么还没送到", Intent.QUERY_LOGISTICS),
                Arguments.of("这个包裹最新进度", Intent.QUERY_LOGISTICS),
                Arguments.of("运单轨迹帮我看下", Intent.QUERY_LOGISTICS),
                Arguments.of("配送到哪个站点了", Intent.QUERY_LOGISTICS),
                Arguments.of("耳机坏了我要退货", Intent.APPLY_REFUND),
                Arguments.of("质量问题帮我退款", Intent.APPLY_REFUND),
                Arguments.of("充电宝想退掉", Intent.APPLY_REFUND),
                Arguments.of("按键坏了申请售后", Intent.APPLY_REFUND),
                Arguments.of("发错商品我要换货", Intent.APPLY_REFUND),
                Arguments.of("耳机不能用想换新", Intent.APPLY_REFUND),
                Arguments.of("退款多久到账", Intent.QUERY_POLICY),
                Arguments.of("换货需要几天", Intent.QUERY_POLICY),
                Arguments.of("退货运费谁负责", Intent.QUERY_POLICY),
                Arguments.of("退货需要什么凭证", Intent.QUERY_POLICY),
                Arguments.of("耳机保修政策", Intent.QUERY_POLICY),
                Arguments.of("优惠券使用规则", Intent.QUERY_POLICY),
                Arguments.of("取消订单", Intent.CANCEL_ORDER),
                Arguments.of("这个订单不要了", Intent.CANCEL_ORDER),
                Arguments.of("帮我撤单", Intent.CANCEL_ORDER),
                Arguments.of("买错了想撤销订单", Intent.CANCEL_ORDER),
                Arguments.of("转人工客服", Intent.TRANSFER_HUMAN),
                Arguments.of("找真人客服", Intent.TRANSFER_HUMAN),
                Arguments.of("客服人员接一下", Intent.TRANSFER_HUMAN),
                Arguments.of("这个问题请转客服", Intent.TRANSFER_HUMAN),
                Arguments.of("你好", Intent.GENERAL),
                Arguments.of("你是谁", Intent.GENERAL),
                Arguments.of("谢谢", Intent.GENERAL),
                Arguments.of("今天心情很好", Intent.GENERAL)
        );
    }
}
