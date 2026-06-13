package com.yupi.mianshiya.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.model.dto.question.QuestionQueryRequest;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.QuestionBank;
import com.yupi.mianshiya.model.entity.QuestionBankQuestion;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.vo.QuestionVO;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目服务测试（数据一致性回归用例）
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuestionServiceTest {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private UserService userService;

    private static MockHttpServletRequest mockRequest;

    private static final Long TEST_USER_ID = 1L;

    /** 测试过程中创建的题目 id，供后续测试复用 */
    private static final List<Long> createdQuestionIds = new ArrayList<>();
    private static Long createdBankId;

    @BeforeAll
    static void setUp() {
        mockRequest = new MockHttpServletRequest();
    }

    @BeforeEach
    void loginBeforeEach() {
        StpUtil.login(TEST_USER_ID);
    }

    // ======================== 1. 题目标签编辑后查询应反映最新标签 ========================

    @Test
    @Order(1)
    void testEditQuestionTags_queryReflectsChanges() {
        // 创建一道带有 ["java", "spring"] 标签的题目
        Question question = new Question();
        question.setTitle("标签编辑测试题目");
        question.setContent("这是一道用于测试标签编辑一致性的题目");
        question.setTags("[\"java\",\"spring\"]");
        question.setUserId(TEST_USER_ID);
        boolean saved = questionService.save(question);
        Assertions.assertTrue(saved);
        createdQuestionIds.add(question.getId());

        // 用旧标签查询，应能查到
        QuestionQueryRequest queryOldTag = new QuestionQueryRequest();
        queryOldTag.setTags(Collections.singletonList("java"));
        Page<Question> pageWithOldTag = questionService.listQuestionByPage(queryOldTag);
        boolean foundByOldTag = pageWithOldTag.getRecords().stream()
                .anyMatch(q -> q.getId().equals(question.getId()));
        Assertions.assertTrue(foundByOldTag, "使用旧标签 java 应能查到题目");

        // 编辑标签为 ["python"]
        Question updateQuestion = new Question();
        updateQuestion.setId(question.getId());
        updateQuestion.setTags("[\"python\"]");
        boolean updated = questionService.updateById(updateQuestion);
        Assertions.assertTrue(updated);

        // 用旧标签查询，应查不到
        Page<Question> pageAfterEditOld = questionService.listQuestionByPage(queryOldTag);
        boolean stillFoundByOldTag = pageAfterEditOld.getRecords().stream()
                .anyMatch(q -> q.getId().equals(question.getId()));
        Assertions.assertFalse(stillFoundByOldTag, "编辑标签后，使用旧标签 java 不应查到题目");

        // 用新标签查询，应能查到
        QuestionQueryRequest queryNewTag = new QuestionQueryRequest();
        queryNewTag.setTags(Collections.singletonList("python"));
        Page<Question> pageWithNewTag = questionService.listQuestionByPage(queryNewTag);
        boolean foundByNewTag = pageWithNewTag.getRecords().stream()
                .anyMatch(q -> q.getId().equals(question.getId()));
        Assertions.assertTrue(foundByNewTag, "使用新标签 python 应能查到题目");
    }

    // ======================== 2. 从题库移除题目后，题库查询不再包含该题 ========================

    @Test
    @Order(2)
    void testRemoveQuestionFromBank_queryReflectsChanges() {
        // 创建题库
        QuestionBank bank = new QuestionBank();
        bank.setTitle("移除测试题库");
        bank.setUserId(TEST_USER_ID);
        questionBankService.save(bank);
        createdBankId = bank.getId();

        // 创建 3 道题目
        List<Long> qIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Question q = new Question();
            q.setTitle("题库移除测试题目 " + i);
            q.setContent("内容 " + i);
            q.setTags("[\"test\"]");
            q.setUserId(TEST_USER_ID);
            questionService.save(q);
            qIds.add(q.getId());
            createdQuestionIds.add(q.getId());
        }

        // 将 3 道题目添加到题库
        User loginUser = userService.getById(TEST_USER_ID);
        questionBankQuestionService.batchAddQuestionsToBank(qIds, bank.getId(), loginUser);

        // 查询题库内题目，应有 3 道
        QuestionQueryRequest queryBank = new QuestionQueryRequest();
        queryBank.setQuestionBankId(bank.getId());
        queryBank.setPageSize(100);
        Page<Question> pageBefore = questionService.listQuestionByPage(queryBank);
        Assertions.assertEquals(3, pageBefore.getTotal(), "题库内应有 3 道题目");

        // 移除第 2 道题
        questionBankQuestionService.batchRemoveQuestionsFromBank(
                Collections.singletonList(qIds.get(1)), bank.getId());

        // 再次查询，应只有 2 道
        Page<Question> pageAfter = questionService.listQuestionByPage(queryBank);
        Assertions.assertEquals(2, pageAfter.getTotal(), "移除后题库内应有 2 道题目");
        boolean removedStillPresent = pageAfter.getRecords().stream()
                .anyMatch(q -> q.getId().equals(qIds.get(1)));
        Assertions.assertFalse(removedStillPresent, "被移除的题目不应出现在题库查询结果中");
    }

    // ======================== 3. 单条删除题目应同时清理题库关联 ========================

    @Test
    @Order(3)
    void testSingleDelete_cleansUpQuestionBankQuestionAssociations() {
        // 创建题库
        QuestionBank bank = new QuestionBank();
        bank.setTitle("单删清理测试题库");
        bank.setUserId(TEST_USER_ID);
        questionBankService.save(bank);

        // 创建题目
        Question q = new Question();
        q.setTitle("单删清理测试题目");
        q.setContent("用于验证单条删除是否清理题库关联");
        q.setTags("[\"delete-test\"]");
        q.setUserId(TEST_USER_ID);
        questionService.save(q);
        createdQuestionIds.add(q.getId());

        // 添加题目到题库
        User loginUser = userService.getById(TEST_USER_ID);
        questionBankQuestionService.batchAddQuestionsToBank(
                Collections.singletonList(q.getId()), bank.getId(), loginUser);

        // 确认关联存在
        long countBefore = questionBankQuestionService.count(
                Wrappers.lambdaQuery(QuestionBankQuestion.class)
                        .eq(QuestionBankQuestion::getQuestionId, q.getId()));
        Assertions.assertEquals(1, countBefore, "删除前应存在 1 条题库关联");

        // 单条删除（复用 batchDeleteQuestions，与修复后的 Controller 逻辑一致）
        questionService.batchDeleteQuestions(Collections.singletonList(q.getId()));

        // 验证题目已逻辑删除
        Question deleted = questionService.getById(q.getId());
        Assertions.assertNull(deleted, "题目应已被逻辑删除（getById 应返回 null）");

        // 验证题库关联已清理
        long countAfter = questionBankQuestionService.count(
                Wrappers.lambdaQuery(QuestionBankQuestion.class)
                        .eq(QuestionBankQuestion::getQuestionId, q.getId()));
        Assertions.assertEquals(0, countAfter, "单条删除后题库关联应被清理");
    }

    // ======================== 4. 批量删除题目应清理所有关联 ========================

    @Test
    @Order(4)
    void testBatchDelete_cleansUpAllAssociations() {
        // 创建题库
        QuestionBank bank = new QuestionBank();
        bank.setTitle("批删测试题库");
        bank.setUserId(TEST_USER_ID);
        questionBankService.save(bank);

        // 创建 3 道题目
        List<Long> qIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Question q = new Question();
            q.setTitle("批删测试题目 " + i);
            q.setContent("内容 " + i);
            q.setTags("[\"batch-delete\"]");
            q.setUserId(TEST_USER_ID);
            questionService.save(q);
            qIds.add(q.getId());
        }

        // 添加到题库
        User loginUser = userService.getById(TEST_USER_ID);
        questionBankQuestionService.batchAddQuestionsToBank(qIds, bank.getId(), loginUser);

        // 批量删除
        questionService.batchDeleteQuestions(qIds);

        // 验证所有题目已逻辑删除
        for (Long qId : qIds) {
            Assertions.assertNull(questionService.getById(qId),
                    "题目 " + qId + " 应已被逻辑删除");
        }

        // 验证所有题库关联已清理
        for (Long qId : qIds) {
            long count = questionBankQuestionService.count(
                    Wrappers.lambdaQuery(QuestionBankQuestion.class)
                            .eq(QuestionBankQuestion::getQuestionId, qId));
            Assertions.assertEquals(0, count,
                    "题目 " + qId + " 的题库关联应已被清理");
        }

        // 查询题库，应为空
        QuestionQueryRequest queryBank = new QuestionQueryRequest();
        queryBank.setQuestionBankId(bank.getId());
        queryBank.setPageSize(100);
        Page<Question> page = questionService.listQuestionByPage(queryBank);
        Assertions.assertEquals(0, page.getTotal(), "批删后题库应为空");
    }

    // ======================== 5. ES 降级查询回退到 MySQL ========================

    @Test
    @Order(5)
    void testSearchFromEs_degradesToMySQLWhenEsUnavailable() {
        // 确保至少有一道可查题目
        Question q = new Question();
        q.setTitle("ES降级查询测试题目");
        q.setContent("用于验证 ES 不可用时降级到 MySQL 查询");
        q.setTags("[\"es-fallback\"]");
        q.setUserId(TEST_USER_ID);
        questionService.save(q);
        createdQuestionIds.add(q.getId());

        // 构造查询请求
        QuestionQueryRequest queryRequest = new QuestionQueryRequest();
        queryRequest.setSearchText("ES降级查询测试题目");
        queryRequest.setPageSize(10);

        // 调用 searchFromEs —— 本地通常不启动 ES，应降级为 MySQL 查询
        Page<Question> result = questionService.searchFromEs(queryRequest);
        Assertions.assertNotNull(result, "ES 不可用时 searchFromEs 应返回降级结果而非 null");
        // 降级到 MySQL 应能查到刚才创建的题目
        boolean found = result.getRecords().stream()
                .anyMatch(item -> item.getId().equals(q.getId()));
        Assertions.assertTrue(found, "降级到 MySQL 后应能查到刚创建的题目");
    }

    // ======================== 6. 批量添加重复检测 ========================

    @Test
    @Order(6)
    void testBatchAddQuestionsToBank_duplicateDetection() {
        // 创建题库
        QuestionBank bank = new QuestionBank();
        bank.setTitle("重复检测测试题库");
        bank.setUserId(TEST_USER_ID);
        questionBankService.save(bank);

        // 创建题目
        Question q = new Question();
        q.setTitle("重复检测测试题目");
        q.setContent("用于验证重复添加检测");
        q.setTags("[\"dup-test\"]");
        q.setUserId(TEST_USER_ID);
        questionService.save(q);
        createdQuestionIds.add(q.getId());

        User loginUser = userService.getById(TEST_USER_ID);

        // 第一次添加，应成功
        questionBankQuestionService.batchAddQuestionsToBank(
                Collections.singletonList(q.getId()), bank.getId(), loginUser);

        // 第二次添加同样的题目，应抛出异常（所有题目都已存在于题库中）
        Assertions.assertThrows(BusinessException.class, () ->
                questionBankQuestionService.batchAddQuestionsToBank(
                        Collections.singletonList(q.getId()), bank.getId(), loginUser),
                "重复添加已在题库中的题目应抛出异常");
    }

    // ======================== 7. 逻辑删除后所有查询路径均过滤 ========================

    @Test
    @Order(7)
    void testLogicalDelete_filtersCorrectly() {
        // 创建题库
        QuestionBank bank = new QuestionBank();
        bank.setTitle("逻辑删除过滤测试题库");
        bank.setUserId(TEST_USER_ID);
        questionBankService.save(bank);

        // 创建题目
        Question q = new Question();
        q.setTitle("逻辑删除过滤测试题目_唯一标题_XYZ");
        q.setContent("用于验证逻辑删除后各查询路径均不再返回");
        q.setTags("[\"logical-delete-test\"]");
        q.setUserId(TEST_USER_ID);
        questionService.save(q);
        createdQuestionIds.add(q.getId());

        // 添加到题库
        User loginUser = userService.getById(TEST_USER_ID);
        questionBankQuestionService.batchAddQuestionsToBank(
                Collections.singletonList(q.getId()), bank.getId(), loginUser);

        // 删除前：全文搜索应能找到
        QuestionQueryRequest searchQuery = new QuestionQueryRequest();
        searchQuery.setSearchText("逻辑删除过滤测试题目_唯一标题_XYZ");
        searchQuery.setPageSize(10);
        Page<Question> searchBefore = questionService.listQuestionByPage(searchQuery);
        boolean foundBeforeSearch = searchBefore.getRecords().stream()
                .anyMatch(item -> item.getId().equals(q.getId()));
        Assertions.assertTrue(foundBeforeSearch, "删除前全文搜索应能找到题目");

        // 删除前：标签查询应能找到
        QuestionQueryRequest tagQuery = new QuestionQueryRequest();
        tagQuery.setTags(Collections.singletonList("logical-delete-test"));
        tagQuery.setPageSize(10);
        Page<Question> tagBefore = questionService.listQuestionByPage(tagQuery);
        boolean foundBeforeTag = tagBefore.getRecords().stream()
                .anyMatch(item -> item.getId().equals(q.getId()));
        Assertions.assertTrue(foundBeforeTag, "删除前标签查询应能找到题目");

        // 删除前：题库查询应能找到
        QuestionQueryRequest bankQuery = new QuestionQueryRequest();
        bankQuery.setQuestionBankId(bank.getId());
        bankQuery.setPageSize(10);
        Page<Question> bankBefore = questionService.listQuestionByPage(bankQuery);
        boolean foundBeforeBank = bankBefore.getRecords().stream()
                .anyMatch(item -> item.getId().equals(q.getId()));
        Assertions.assertTrue(foundBeforeBank, "删除前题库查询应能找到题目");

        // 执行删除（同时清理题库关联）
        questionService.batchDeleteQuestions(Collections.singletonList(q.getId()));

        // 删除后：全文搜索不应找到
        Page<Question> searchAfter = questionService.listQuestionByPage(searchQuery);
        boolean foundAfterSearch = searchAfter.getRecords().stream()
                .anyMatch(item -> item.getId().equals(q.getId()));
        Assertions.assertFalse(foundAfterSearch, "删除后全文搜索不应找到题目");

        // 删除后：标签查询不应找到
        Page<Question> tagAfter = questionService.listQuestionByPage(tagQuery);
        boolean foundAfterTag = tagAfter.getRecords().stream()
                .anyMatch(item -> item.getId().equals(q.getId()));
        Assertions.assertFalse(foundAfterTag, "删除后标签查询不应找到题目");

        // 删除后：题库查询不应找到（题库关联也已清理）
        Page<Question> bankAfter = questionService.listQuestionByPage(bankQuery);
        boolean foundAfterBank = bankAfter.getRecords().stream()
                .anyMatch(item -> item.getId().equals(q.getId()));
        Assertions.assertFalse(foundAfterBank, "删除后题库查询不应找到题目");
    }

    // ======================== 清理测试数据 ========================

    @AfterAll
    static void cleanup() {
        // 测试数据由 @Transactional 或测试内的删除操作处理
        // 对于未被删除的测试数据，可在后续手动清理或依赖测试数据库重置
    }
}
