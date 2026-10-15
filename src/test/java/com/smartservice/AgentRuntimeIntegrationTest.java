package com.smartservice;

import com.smartservice.aftersale.AfterSaleService;
import com.smartservice.agent.AgentRequest;
import com.smartservice.agent.AgentResponse;
import com.smartservice.agent.AgentRuntime;
import com.smartservice.agent.ai.SpringAiToolbox;
import com.smartservice.agent.safety.ConfirmationService;
import com.smartservice.agent.tool.ToolContext;
import com.smartservice.agent.tool.ToolRegistry;
import com.smartservice.auth.AuthRepository;
import com.smartservice.audit.AuditService;
import com.smartservice.common.BusinessException;
import com.smartservice.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "smartservice.agent.stream-delay-ms=0")
@AutoConfigureMockMvc
class AgentRuntimeIntegrationTest {
    @Autowired AgentRuntime runtime;
    @Autowired ToolRegistry tools;
    @Autowired AfterSaleService afterSale;
    @Autowired AuthRepository authRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockMvc mockMvc;
    @Autowired ConfirmationService confirmations;
    @Autowired AuditService audit;

    @Test
    void demoAccountPasswordIsUsable() {
        var account = authRepository.findByUsername("demo").orElseThrow();
        assertThat(passwordEncoder.matches("demo123", account.passwordHash())).isTrue();
    }

    @Test
    void completesLogisticsQueryThroughTwoTools() {
        AgentResponse response = runtime.execute(1, new AgentRequest("帮我查一下耳机到哪了", "logistics-1", null));
        assertThat(response.status()).isEqualTo(AgentResponse.Status.ANSWERED);
        assertThat(response.message()).contains("顺丰速运", "已签收");
        assertThat(response.toolCalls()).extracting(ToolRegistry.ToolResult::tool)
                .containsExactly("query_order", "query_logistics");
        assertThat(response.execution().engine()).isEqualTo(AgentResponse.Engine.RULE_ENGINE);
        assertThat(response.execution().fallback()).isFalse();
    }

    @Test
    void springAiRefundToolOnlyPreparesConfirmationWithoutWriting() {
        int before = afterSale.countByUser(1);
        SpringAiToolbox toolbox = new SpringAiToolbox(tools, confirmations, audit,
                1, "ai-tool-trace", "ai-tool-request", "这个耳机坏了，我想退货");

        SpringAiToolbox.RefundPreparation prepared = toolbox.prepareRefund("耳机", "耳机单侧无声");

        assertThat(prepared.order().orderNo()).isEqualTo("SS202710110001");
        assertThat(toolbox.confirmation()).isNotNull();
        assertThat(toolbox.calls()).extracting(ToolRegistry.ToolResult::tool)
                .containsExactly("query_order", "search_policy", "prepare_refund");
        assertThat(afterSale.countByUser(1)).isEqualTo(before);
    }

    @Test
    void springAiToolboxCannotReadAnotherUsersOrderBySupplyingAnId() {
        SpringAiToolbox toolbox = new SpringAiToolbox(tools, confirmations, audit,
                2, "ai-isolation-trace", "ai-isolation-request", "查询订单 101 的物流");

        assertThatThrownBy(() -> toolbox.queryLogistics(101))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有找到该订单");
    }

    @Test
    void refundRequiresConfirmationAndReplayDoesNotDuplicateWrite() {
        int before = afterSale.countByUser(1);
        AgentResponse first = runtime.execute(1, new AgentRequest("这个耳机坏了，我想退货", "refund-1", null));
        assertThat(first.status()).isEqualTo(AgentResponse.Status.CONFIRMATION_REQUIRED);
        assertThat(first.confirmation()).isNotNull();
        String token = first.confirmation().token();

        AgentResponse confirmed = runtime.execute(1, new AgentRequest("确认退货", "refund-2", token));
        AgentResponse replayed = runtime.execute(1, new AgentRequest("确认退货", "refund-3", token));
        assertThat(confirmed.status()).isEqualTo(AgentResponse.Status.ACTION_COMPLETED);
        assertThat(replayed.message()).contains("已处理过");
        assertThat(afterSale.countByUser(1) - before).isEqualTo(1);
    }

    @Test
    void highRiskToolCannotBypassCentralGuard() {
        ToolContext unconfirmed = new ToolContext(1, "risk-test", "risk-test", false);
        for (int attempt = 0; attempt < 100; attempt++) {
            assertThatThrownBy(() -> tools.execute("create_refund", unconfirmed,
                    Map.of("orderId", 101L, "reason", "test", "idempotencyKey", "risk-test")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("二次确认");
        }
    }

    @Test
    void oneHundredReplaysProduceOneDatabaseWrite() {
        int before = afterSale.countByUser(1);
        for (int attempt = 0; attempt < 100; attempt++) {
            afterSale.createRefund(1, 101, "回放测试", "replay-benchmark", "replay-" + attempt);
        }
        assertThat(afterSale.countByUser(1) - before).isEqualTo(1);
    }

    @Test
    void sseCompletesAfterAuthenticatedAsyncDispatch() throws Exception {
        UserPrincipal principal = new UserPrincipal(1, "demo", "林同学", "USER");
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var initial = mockMvc.perform(post("/api/agent/chat/stream")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"帮我查一下耳机到哪了\",\"requestId\":\"sse-test\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:engine")))
                .andExpect(content().string(containsString("event:result")));
    }

    @Test
    void frontendDataEndpointsReturnKnowledgeAndAfterSales() throws Exception {
        UserPrincipal principal = new UserPrincipal(1, "demo", "林同学", "USER");
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(get("/api/knowledge/documents").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(30)))
                .andExpect(content().string(containsString("\"category\":\"AFTER_SALE\"")));

        mockMvc.perform(get("/api/after-sales").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"success\":true")));
    }

    @Test
    void hybridSearchEndpointReturnsRankedScores() throws Exception {
        var authentication = userAuthentication(1, "demo", "USER");
        mockMvc.perform(get("/api/knowledge/search")
                        .with(authentication(authentication))
                        .param("query", "充电宝鼓包还能保修吗")
                        .param("topK", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].documentId", is(322)))
                .andExpect(jsonPath("$.data[0].bm25Score").exists())
                .andExpect(jsonPath("$.data[0].vectorScore").exists())
                .andExpect(jsonPath("$.data[0].rrfScore").exists());
    }

    @Test
    void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("请先登录")));
    }

    @Test
    void runtimeStatusReportsRuleModeWhenNoModelKeyIsConfigured() throws Exception {
        mockMvc.perform(get("/api/agent/status")
                        .with(authentication(userAuthentication(1, "demo", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiEnabled", is(false)))
                .andExpect(jsonPath("$.data.engine", is("RULE_ENGINE")));
    }

    @Test
    void regularUserCannotRunAdminEvaluation() throws Exception {
        mockMvc.perform(get("/api/admin/evaluation")
                        .with(authentication(userAuthentication(1, "demo", "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmationTokenIsBoundToIssuingUser() {
        AgentResponse first = runtime.execute(1, new AgentRequest("耳机坏了，我要退货", "bound-1", null));
        String token = first.confirmation().token();

        assertThatThrownBy(() -> runtime.execute(2,
                new AgentRequest("确认退货", "bound-2", token)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户");
    }

    @Test
    void afterSalesAreIsolatedByUser() throws Exception {
        mockMvc.perform(get("/api/after-sales")
                        .with(authentication(userAuthentication(2, "admin", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void blankKnowledgeQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/knowledge/search")
                        .with(authentication(userAuthentication(1, "demo", "USER")))
                        .param("query", " "))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken userAuthentication(long id, String username, String role) {
        UserPrincipal principal = new UserPrincipal(id, username, username, role);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
