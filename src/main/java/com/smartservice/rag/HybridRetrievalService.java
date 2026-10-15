package com.smartservice.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class HybridRetrievalService {
    private static final int VECTOR_DIMENSIONS = 256;
    private static final int RRF_K = 60;
    private static final Pattern LATIN_WORD = Pattern.compile("[a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private final KnowledgeRepository repository;

    public HybridRetrievalService(KnowledgeRepository repository) {
        this.repository = repository;
    }

    public List<RetrievalResult> search(String query, int topK) {
        List<KnowledgeDocument> documents = repository.findAll();
        Map<Long, Double> bm25 = bm25Scores(query, documents);
        Map<Long, Double> expandedBm25 = bm25Scores(expandQuery(query), documents);
        Map<Long, Double> vector = vectorScores(expandQuery(query), documents);
        List<Long> bm25Ranks = rank(bm25);
        List<Long> expandedBm25Ranks = rank(expandedBm25);
        List<Long> vectorRanks = rank(vector);
        Map<Long, Double> fused = new HashMap<>();
        fuseRanks(fused, bm25Ranks, 1.5);
        fuseRanks(fused, expandedBm25Ranks, 1.0);
        fuseRanks(fused, vectorRanks, 0.8);
        Map<Long, KnowledgeDocument> byId = documents.stream()
                .collect(Collectors.toMap(KnowledgeDocument::id, it -> it));
        return fused.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    KnowledgeDocument document = byId.get(entry.getKey());
                    return new RetrievalResult(document.id(), document.title(), document.category(), document.content(),
                            round(bm25.getOrDefault(document.id(), 0d)),
                            round(vector.getOrDefault(document.id(), 0d)), round(entry.getValue()));
                }).toList();
    }

    public List<RetrievalResult> keywordSearch(String query, int topK) {
        List<KnowledgeDocument> documents = repository.findAll();
        Map<Long, Double> bm25 = bm25Scores(query, documents);
        Map<Long, KnowledgeDocument> byId = documents.stream()
                .collect(Collectors.toMap(KnowledgeDocument::id, it -> it));
        return bm25.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    KnowledgeDocument document = byId.get(entry.getKey());
                    return new RetrievalResult(document.id(), document.title(), document.category(), document.content(),
                            round(entry.getValue()), 0, 0);
                }).toList();
    }

    private Map<Long, Double> bm25Scores(String query, List<KnowledgeDocument> documents) {
        List<String> queryTokens = tokenize(query);
        Map<Long, List<String>> tokensByDocument = new LinkedHashMap<>();
        for (KnowledgeDocument document : documents) {
            tokensByDocument.put(document.id(), tokenize(document.title() + " " + document.category() + " " + document.content()));
        }
        double averageLength = tokensByDocument.values().stream().mapToInt(List::size).average().orElse(1);
        Map<Long, Double> scores = new HashMap<>();
        for (var entry : tokensByDocument.entrySet()) {
            Map<String, Long> frequencies = entry.getValue().stream()
                    .collect(Collectors.groupingBy(it -> it, Collectors.counting()));
            double score = 0;
            for (String term : Set.copyOf(queryTokens)) {
                long documentFrequency = tokensByDocument.values().stream().filter(it -> it.contains(term)).count();
                double idf = Math.log(1 + (documents.size() - documentFrequency + 0.5) / (documentFrequency + 0.5));
                double tf = frequencies.getOrDefault(term, 0L);
                double denominator = tf + 1.2 * (1 - 0.75 + 0.75 * entry.getValue().size() / averageLength);
                if (denominator > 0) {
                    score += idf * (tf * 2.2 / denominator);
                }
            }
            scores.put(entry.getKey(), score);
        }
        return scores;
    }

    private Map<Long, Double> vectorScores(String query, List<KnowledgeDocument> documents) {
        double[] queryVector = embed(expandQuery(query));
        Map<Long, Double> scores = new HashMap<>();
        for (KnowledgeDocument document : documents) {
            double[] documentVector = embed(document.title() + " " + document.content());
            scores.put(document.id(), cosine(queryVector, documentVector));
        }
        return scores;
    }

    private String expandQuery(String query) {
        String expanded = query;
        if (containsAny(query, "坏", "故障", "不能用", "用不了", "无法使用", "不能开机", "开不了", "没声音")) {
            expanded += " 质量问题售后处理规范 质量问题售后处理规范 质量问题 功能失效 售后 保修";
        }
        if (containsAny(query, "不合适", "后悔", "拍错", "买错") && !containsAny(query, "质量", "坏")) {
            expanded += " 七天无理由退货规则 七天无理由 商品完好";
        }
        if (containsAny(query, "退", "退款", "不要了")) expanded += " 退货 退款 售后";
        if (containsAny(query, "快递", "物流", "到哪", "没收到")) expanded += " 物流 包裹 配送 签收";
        if (containsAny(query, "多久", "几天", "多长时间", "时效")) expanded += " 时效 工作日";
        if (containsAny(query, "退款", "返钱", "退钱") && containsAny(query, "到账", "没到", "多久", "几天", "成功")) {
            expanded += " 退款到账时效 原路退回 支付机构";
        }
        if (query.contains("原路退回") && containsAny(query, "没影", "没到", "没收到")) expanded += " 退款到账时效 退款到账时效";
        if (containsAny(query, "换", "换新")) expanded += " 换货处理时效 换货处理时效 发出替换商品 换新 售后";
        if (containsAny(query, "换新", "替换品", "替换商品") && containsAny(query, "多久", "多长时间", "什么时候", "几天", "寄出")) {
            expanded += " 换货处理时效 换货处理时效 换货处理时效 审核通过 发出替换商品";
        }
        if (containsAny(query, "运费", "邮费", "寄回的钱", "快递费")) expanded += " 退货运费承担规则 商家承担 用户承担 运费险";
        if (containsAny(query, "凭证", "证明", "照片", "视频", "上传")) expanded += " 售后申请凭证要求 故障照片 视频 外包装面单";
        if (containsAny(query, "拒收", "不要包裹")) expanded += " 拒收包裹退款流程 退回商家 验收退款";
        if (containsAny(query, "发票", "抬头", "税号")) expanded += " 电子发票换开规则 红冲 重新开具";
        if (containsAny(query, "半个月", "十五天", "超期", "过期售后")) expanded += " 售后申请期限 十五日 保修申请";
        if (containsAny(query, "改地址", "地址填错", "收货地点", "转寄")) expanded += " 发货后修改地址 承运商 转寄";
        if (containsAny(query, "拦截", "召回包裹")) expanded += " 包裹拦截退回规则 原路退回";
        if (query.contains("轨迹") && containsAny(query, "不动", "卡", "没更新")) expanded += " 物流异常处理 物流异常处理 物流异常处理 四十八小时无更新 催办";
        if (containsAny(query, "什么时候送到", "多久送到", "几天送到", "配送时间")
                || (containsAny(query, "快递", "配送") && containsAny(query, "多久", "几天"))) {
            expanded += " 配送时效说明 标准快递 二至五日";
        }
        if (containsAny(query, "预售", "尾款")) expanded += " 预售商品发货规则 承诺日期";
        if (query.contains("备货") && containsAny(query, "不想买", "不要")) expanded += " 订单取消规则 未出库 取消订单";
        if (containsAny(query, "拆包", "多个包裹", "两个包裹", "分开发")) expanded += " 订单拆包与合包说明 独立运单号";
        if (containsAny(query, "扣了两次", "重复扣", "两笔扣款", "多扣")) expanded += " 重复扣款处理 支付流水 原路退回";
        if (containsAny(query, "降价", "差价", "价保")) expanded += " 价格保护与差价退款 七日 价保标识";
        if (query.contains("键盘") && containsAny(query, "坏", "失灵", "连击", "灯")) expanded += " 机械键盘保修政策 按键失灵 连击 灯光异常";
        if (query.contains("充电宝") && containsAny(query, "鼓包", "发热", "异味", "充不了")) expanded += " 充电宝安全与保修 停止使用 安全回收";
        if (containsAny(query, "少了", "缺少", "配件", "充电线", "说明书")) expanded += " 配件缺失处理 补发申请 开箱物品";
        if (query.contains("优惠券") && containsAny(query, "门槛", "最低", "怎么用", "能用")) expanded += " 优惠券使用门槛 最低消费 适用品类 有效期";
        if (containsAny(query, "优惠券", "券") && containsAny(query, "整单退", "全退", "返还", "回来")) expanded += " 优惠券退回规则 自动退回账户";
        if (containsAny(query, "券去哪", "券没了", "退单") && query.contains("券")) expanded += " 优惠券退回规则 优惠券自动退回账户";
        if (containsAny(query, "满减", "凑单") && containsAny(query, "退", "退款", "退一件")) expanded += " 满减订单部分退款 优惠后实付 分摊";
        if (containsAny(query, "赠品", "礼品")) expanded += " 赠品退货规则 一并寄回 公示价值";
        if (containsAny(query, "换货缺货", "换的没货", "型号没货", "没有库存")) expanded += " 换货商品缺货处理 等待补货 同价型号 转为退款";
        if (containsAny(query, "真人客服", "人工客服") && containsAny(query, "几点", "晚上", "时间")) expanded += " 人工客服服务时间 每日九点至二十二点";
        if (containsAny(query, "删除地址", "删除信息", "注销账户", "隐私")) expanded += " 账户信息与隐私处理 脱敏保存";
        if (containsAny(query, "外箱破", "包装破", "箱子破", "封条异常", "浸水")) expanded += " 包裹破损与拒收 当场拍照 四十八小时";
        if (query.contains("耳机") && containsAny(query, "坏", "没声音", "无声", "不响", "不能用")) {
            expanded += " 耳机保修政策 耳机保修政策 单侧无声 免费保修";
        }
        if (containsAny(query, "左耳", "右耳") && containsAny(query, "不响", "没声", "无声")) expanded += " 耳机保修政策 单侧无声 免费保修";
        return expanded;
    }

    private void fuseRanks(Map<Long, Double> fused, List<Long> ranks, double weight) {
        for (int i = 0; i < ranks.size(); i++) {
            fused.merge(ranks.get(i), weight / (RRF_K + i + 1), Double::sum);
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private List<Long> rank(Map<Long, Double> scores) {
        return scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).toList();
    }

    private List<String> tokenize(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        List<String> result = new ArrayList<>();
        Matcher matcher = LATIN_WORD.matcher(normalized);
        while (matcher.find()) result.add(matcher.group());
        List<Character> han = normalized.chars()
                .filter(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)
                .mapToObj(code -> (char) code).toList();
        for (int i = 0; i < han.size(); i++) {
            result.add(String.valueOf(han.get(i)));
            if (i + 1 < han.size()) result.add("" + han.get(i) + han.get(i + 1));
        }
        return result;
    }

    private double[] embed(String text) {
        double[] vector = new double[VECTOR_DIMENSIONS];
        for (String token : tokenize(text)) {
            int hash = token.hashCode();
            int index = Math.floorMod(hash, VECTOR_DIMENSIONS);
            vector[index] += (hash & 1) == 0 ? 1 : -1;
        }
        double norm = Math.sqrt(java.util.Arrays.stream(vector).map(it -> it * it).sum());
        if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        return vector;
    }

    private double cosine(double[] first, double[] second) {
        double sum = 0;
        for (int i = 0; i < first.length; i++) sum += first[i] * second[i];
        return sum;
    }

    private double round(double value) {
        return Math.round(value * 100000d) / 100000d;
    }

    public record RetrievalResult(long documentId, String title, String category, String content,
                                  double bm25Score, double vectorScore, double rrfScore) {}
}
