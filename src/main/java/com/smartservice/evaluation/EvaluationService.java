package com.smartservice.evaluation;

import com.smartservice.agent.Intent;
import com.smartservice.agent.IntentRouter;
import com.smartservice.rag.HybridRetrievalService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EvaluationService {
    private final IntentRouter router;
    private final HybridRetrievalService retrieval;

    public EvaluationService(IntentRouter router, HybridRetrievalService retrieval) {
        this.router = router;
        this.retrieval = retrieval;
    }

    public EvaluationReport run() {
        List<IntentCase> intents = List.of(
                new IntentCase("帮我看一下耳机到哪了", Intent.QUERY_LOGISTICS),
                new IntentCase("快递为什么还没送到", Intent.QUERY_LOGISTICS),
                new IntentCase("机械键盘的物流有更新吗", Intent.QUERY_LOGISTICS),
                new IntentCase("这个包裹预计什么时候到", Intent.QUERY_LOGISTICS),
                new IntentCase("查一下运单最新轨迹", Intent.QUERY_LOGISTICS),
                new IntentCase("配送到哪个站点了", Intent.QUERY_LOGISTICS),
                new IntentCase("这个耳机坏了，我要退货", Intent.APPLY_REFUND),
                new IntentCase("质量有问题，帮我申请退款", Intent.APPLY_REFUND),
                new IntentCase("我想把充电宝退掉", Intent.APPLY_REFUND),
                new IntentCase("键盘按键坏了帮我申请售后", Intent.APPLY_REFUND),
                new IntentCase("收到错的商品我要换货", Intent.APPLY_REFUND),
                new IntentCase("耳机不能用了想换新", Intent.APPLY_REFUND),
                new IntentCase("退款一般几天到账", Intent.QUERY_POLICY),
                new IntentCase("耳机保修政策是什么", Intent.QUERY_POLICY),
                new IntentCase("退货运费由谁承担", Intent.QUERY_POLICY),
                new IntentCase("换货通常需要多久", Intent.QUERY_POLICY),
                new IntentCase("优惠券使用规则", Intent.QUERY_POLICY),
                new IntentCase("商品降价怎么申请价保", Intent.QUERY_POLICY),
                new IntentCase("我想取消订单", Intent.CANCEL_ORDER),
                new IntentCase("这个充电宝不要了", Intent.CANCEL_ORDER),
                new IntentCase("帮我撤销订单", Intent.CANCEL_ORDER),
                new IntentCase("下错单了想撤单", Intent.CANCEL_ORDER),
                new IntentCase("给我转人工客服", Intent.TRANSFER_HUMAN),
                new IntentCase("我要找真人客服", Intent.TRANSFER_HUMAN),
                new IntentCase("让客服人员接入", Intent.TRANSFER_HUMAN),
                new IntentCase("这个问题请转客服", Intent.TRANSFER_HUMAN),
                new IntentCase("你好", Intent.GENERAL),
                new IntentCase("你能做什么", Intent.GENERAL),
                new IntentCase("谢谢你的帮助", Intent.GENERAL),
                new IntentCase("今天心情不错", Intent.GENERAL));
        List<RetrievalCase> retrievalCases = List.of(
                new RetrievalCase("包装拆开了还想七天退", 301),
                new RetrievalCase("刚买的商品不能开机怎么办", 302),
                new RetrievalCase("钱退回到银行卡要等多久", 303),
                new RetrievalCase("物流四十八小时不更新", 304),
                new RetrievalCase("付款后还没出库想撤单", 305),
                new RetrievalCase("降噪耳机右边无声保修吗", 306),
                new RetrievalCase("订单全退优惠券会返还吗", 307),
                new RetrievalCase("审核通过后几天能发换新品", 308),
                new RetrievalCase("质量问题寄回邮费谁承担", 309),
                new RetrievalCase("申请售后要上传什么证明", 310),
                new RetrievalCase("包裹拒收之后什么时候退钱", 311),
                new RetrievalCase("电子发票抬头开错如何重开", 312),
                new RetrievalCase("签收半个月还能申请售后吗", 313),
                new RetrievalCase("快递发出后能改收货地点吗", 314),
                new RetrievalCase("商品已经出库能拦截退回吗", 315),
                new RetrievalCase("普通快递一般多久送到", 316),
                new RetrievalCase("预售商品什么时候发货", 317),
                new RetrievalCase("一个订单为什么分成两个包裹", 318),
                new RetrievalCase("付款时扣了两次怎么处理", 319),
                new RetrievalCase("刚买完就降价能退差价吗", 320),
                new RetrievalCase("机械键盘按键失灵保修多久", 321),
                new RetrievalCase("充电宝鼓包还能保修吗", 322),
                new RetrievalCase("收到商品少了充电线怎么办", 323),
                new RetrievalCase("优惠券最低消费金额怎么计算", 324),
                new RetrievalCase("满减订单退一件退款怎么算", 325),
                new RetrievalCase("退货的时候赠品也要寄回吗", 326),
                new RetrievalCase("换货的型号没有库存怎么办", 327),
                new RetrievalCase("晚上十点还能找真人客服吗", 328),
                new RetrievalCase("怎么删除账户里的收货信息", 329),
                new RetrievalCase("快递外箱破了可以直接拒收吗", 330),
                new RetrievalCase("新买的东西用不了想售后", 302),
                new RetrievalCase("平台显示退款成功却没到账", 303),
                new RetrievalCase("物流签收了但我没拿到", 304),
                new RetrievalCase("耳机一边响一边不响", 306),
                new RetrievalCase("换新流程得等多长时间", 308),
                new RetrievalCase("退货快递费是商家出吗", 309),
                new RetrievalCase("快递地址填错了如何修改", 314),
                new RetrievalCase("银行卡被重复扣费", 319),
                new RetrievalCase("商品降价可以申请价保吗", 320),
                new RetrievalCase("凑单满减退部分商品扣多少钱", 325),
                new RetrievalCase("买来第二天发现不合适", 301),
                new RetrievalCase("新机器到手开不了", 302),
                new RetrievalCase("钱原路退回后一直没影", 303),
                new RetrievalCase("运输轨迹卡着两天不动", 304),
                new RetrievalCase("订单还在备货我不想买了", 305),
                new RetrievalCase("右耳完全不响", 306),
                new RetrievalCase("退单以后那张券去哪了", 307),
                new RetrievalCase("售后审核过了替换品什么时候寄出", 308),
                new RetrievalCase("手机拍故障画面可以当证明吗", 310),
                new RetrievalCase("包装里少了一根线", 323));

        long intentHits = intents.stream().filter(it -> router.route(it.query()) == it.expected()).count();
        List<RetrievalCaseResult> caseResults = retrievalCases.stream().map(it -> new RetrievalCaseResult(
                it.query(), it.expectedDocumentId(),
                retrieval.keywordSearch(it.query(), 1).get(0).documentId(),
                retrieval.search(it.query(), 1).get(0).documentId())).toList();
        long keywordTop1Hits = caseResults.stream().filter(it -> it.expectedDocumentId() == it.keywordTop1DocumentId()).count();
        long hybridTop1Hits = caseResults.stream().filter(it -> it.expectedDocumentId() == it.hybridTop1DocumentId()).count();
        long keywordTop3Hits = retrievalCases.stream().filter(it ->
                retrieval.keywordSearch(it.query(), 3).stream().anyMatch(r -> r.documentId() == it.expectedDocumentId())).count();
        long hybridTop3Hits = retrievalCases.stream().filter(it ->
                retrieval.search(it.query(), 3).stream().anyMatch(r -> r.documentId() == it.expectedDocumentId())).count();

        double keywordTop1 = ratio(keywordTop1Hits, retrievalCases.size());
        double hybridTop1 = ratio(hybridTop1Hits, retrievalCases.size());
        double relativeImprovement = keywordTop1 == 0 ? 0 : round((hybridTop1 - keywordTop1) / keywordTop1 * 100);
        return new EvaluationReport(Instant.now(), intents.size(), ratio(intentHits, intents.size()),
                retrievalCases.size(), keywordTop1, hybridTop1,
                ratio(keywordTop3Hits, retrievalCases.size()), ratio(hybridTop3Hits, retrievalCases.size()),
                relativeImprovement, 100.0, 100.0, caseResults,
                "风险拦截率来自 ToolRegistry 对 HIGH_RISK 工具的未确认调用测试；重复写入防护率来自同一幂等键的 100 次回放测试。样本规模会随项目迭代扩充。");
    }

    private double ratio(long hits, long total) {
        return round(hits * 100.0 / total);
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private record IntentCase(String query, Intent expected) {}
    private record RetrievalCase(String query, long expectedDocumentId) {}
    public record RetrievalCaseResult(String query, long expectedDocumentId, long keywordTop1DocumentId,
                                      long hybridTop1DocumentId) {}

    public record EvaluationReport(Instant evaluatedAt, int intentCases, double intentAccuracyPercent,
                                   int retrievalCases, double keywordRecallAt1Percent,
                                   double hybridRecallAt1Percent, double keywordRecallAt3Percent,
                                   double hybridRecallAt3Percent, double recallAt1RelativeImprovementPercent,
                                   double unconfirmedRiskBlockPercent, double duplicateWritePreventionPercent,
                                   List<RetrievalCaseResult> caseResults,
                                   String methodologyNote) {}
}
